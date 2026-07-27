cmake_minimum_required(VERSION 3.22.1)
project(llamabridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Pinned to a commit the bridge has been compiled *and linked* against — see
# tools/verify-native.sh. Tracking master is how this broke before:
# llama_model_params dropped use_mmap/use_mlock in favour of load_mode and the
# build failed. Bump deliberately, re-run the verifier, then rebuild in CI.
set(LLAMA_CPP_TAG "7cdd557f76800b5a84ddee2bff6f20178a3e31fe"
    CACHE STRING "llama.cpp commit, tag or branch")

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        ${LLAMA_CPP_TAG}
    # Not shallow: a shallow fetch of an arbitrary commit (rather than a branch
    # or tag) is unreliable, and a failed fetch costs more than the clone.
    GIT_SHALLOW    FALSE
)

# Build only the library: no CLI tools, tests, servers or curl on a phone.
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)

# --- CPU instruction set ------------------------------------------------------
#
# This matters more than any backend setting on the Settings screen.
#
# ggml picks its matmul kernels at *compile* time from -march. Cross-compiling
# for Android with neither GGML_CPU_ARM_ARCH nor GGML_CPU_ALL_VARIANTS set,
# ggml adds no arch flags at all, so the NDK default applies: plain armv8-a.
# That builds the scalar/NEON fallback and leaves SDOT unused — on a Snapdragon
# 8 Elite (S25 Ultra) that is several times slower than the kernels the chip can
# actually run.
#
# armv8.2-a+dotprod+fp16 is the highest baseline that is safe for every device
# this app installs on (minSdk 24): dotprod arrived with Cortex-A75/A55 in 2018
# and every 64-bit Android phone since has it. i8mm (armv8.6) would be faster
# still on 2022+ chips, but emitting those instructions unconditionally would
# make the app SIGILL on anything older, so it stays opt-in.
#
# GGML_CPU_ALL_VARIANTS, which would give per-device dispatch, is not usable
# here: it builds the backends as versioned MODULE libraries (libggml-cpu-*.so.N)
# and Android only packages plain .so files out of an APK.
set(LLAMA_ARM_ARCH "armv8.2-a+dotprod+fp16"
    CACHE STRING "-march for the ggml CPU kernels")
# GGML_CPU_ARM_ARCH is only consulted on the non-native path, and -march=native
# is meaningless when cross-compiling anyway.
set(GGML_NATIVE       OFF                  CACHE BOOL   "" FORCE)
set(GGML_CPU_ARM_ARCH "${LLAMA_ARM_ARCH}"  CACHE STRING "" FORCE)
message(STATUS "ggml CPU kernels: -march=${LLAMA_ARM_ARCH}")

# The NDK ships no OpenMP runtime by default, and llamafile's sgemm kernels
# don't build cleanly for android arm64 — ggml's own NEON path is used instead.
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN            OFF CACHE BOOL "" FORCE)

# --- GPU: OpenCL on Adreno ----------------------------------------------------
#
# The single largest speed lever available to this app. Every GGUF token was
# being decoded on the CPU while the Adreno sat idle, and the Settings screen
# offered a "GPU" option that did nothing for the format people actually run.
#
# Two dependencies have to be built from source because the NDK ships neither:
# there is no `libOpenCL.so` to link against and no `CL/cl.h` to include. The
# Khronos ICD loader is what resolves the vendor driver at runtime, so it is
# built statically into libllamabridge.so — nothing extra to package, and
# nothing to load if the device has no driver.
#
# Compiling the backend in does NOT move any work to the GPU on its own:
# `n_gpu_layers` stays 0 unless the user asks for GPU, and a failed GPU load
# falls back to CPU rather than refusing the model. So the worst case of this
# whole block is an APK that behaves exactly as before.
option(LLAMA_ENABLE_OPENCL "Build the Adreno OpenCL backend" ON)

if (LLAMA_ENABLE_OPENCL)
    set(OPENCL_HEADERS_TAG   "v2024.10.24" CACHE STRING "OpenCL-Headers tag")
    set(OPENCL_LOADER_TAG    "v2024.10.24" CACHE STRING "OpenCL-ICD-Loader tag")

    FetchContent_Declare(
        opencl_headers
        GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-Headers.git
        GIT_TAG        ${OPENCL_HEADERS_TAG}
    )
    set(BUILD_TESTING                     OFF CACHE BOOL "" FORCE)
    set(OPENCL_HEADERS_BUILD_TESTING      OFF CACHE BOOL "" FORCE)
    set(OPENCL_HEADERS_BUILD_CXX_TESTS    OFF CACHE BOOL "" FORCE)
    FetchContent_MakeAvailable(opencl_headers)

    # The loader must compile against the same headers ggml will include, or
    # the two disagree about the ABI of a struct nobody looks at until it
    # crashes.
    set(OPENCL_ICD_LOADER_HEADERS_DIR "${opencl_headers_SOURCE_DIR}"
        CACHE PATH "" FORCE)
    set(OPENCL_ICD_LOADER_BUILD_TESTING OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(
        opencl_icd_loader
        GIT_REPOSITORY https://github.com/KhronosGroup/OpenCL-ICD-Loader.git
        GIT_TAG        ${OPENCL_LOADER_TAG}
    )
    FetchContent_MakeAvailable(opencl_icd_loader)

    # ggml-opencl calls find_package(OpenCL REQUIRED) and then uses
    # OpenCL_LIBRARIES / OpenCL_INCLUDE_DIRS directly. Pre-seeding the cache
    # makes FindOpenCL's find_path/find_library no-ops and satisfies it without
    # a sysroot copy of a library that does not exist on Android. "OpenCL" here
    # is the ICD loader's own target name, which target_link_libraries resolves
    # as a target rather than a file.
    set(OpenCL_INCLUDE_DIR "${opencl_headers_SOURCE_DIR}" CACHE PATH "" FORCE)
    set(OpenCL_LIBRARY     OpenCL                          CACHE STRING "" FORCE)

    set(GGML_OPENCL                    ON  CACHE BOOL "" FORCE)
    # Kernels must be compiled into the .so. Left as loose .cl files they are
    # looked up by path at runtime, and an APK has no paths to look up.
    set(GGML_OPENCL_EMBED_KERNELS      ON  CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_KERNELS ON  CACHE BOOL "" FORCE)
    message(STATUS "OpenCL backend: ON (Adreno kernels, embedded)")
else()
    set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
    message(STATUS "OpenCL backend: OFF")
endif()

FetchContent_MakeAvailable(llama_cpp)

add_library(llamabridge SHARED llama_bridge.cpp)

target_link_libraries(llamabridge
    PRIVATE
        llama
        ggml
        android
        log
)
