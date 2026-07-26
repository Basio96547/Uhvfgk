cmake_minimum_required(VERSION 3.22.1)
project(llamabridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Pinned so a rebuild never silently picks up a breaking API change. Bump
# deliberately, then rebuild in CI to confirm the bridge still compiles.
set(LLAMA_CPP_TAG "master" CACHE STRING "llama.cpp git tag or branch")

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        ${LLAMA_CPP_TAG}
    GIT_SHALLOW    TRUE
)

# Build only the library: no CLI tools, tests, servers or curl on a phone.
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)

# The NDK ships no OpenMP runtime by default, and llamafile's sgemm kernels
# don't build cleanly for android arm64 — ggml's own NEON path is used instead.
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL            OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN            OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

add_library(llamabridge SHARED llama_bridge.cpp)

target_link_libraries(llamabridge
    PRIVATE
        llama
        ggml
        android
        log
)
