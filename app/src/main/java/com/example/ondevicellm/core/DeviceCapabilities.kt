package com.example.ondevicellm.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Memory picture of the device.
 *
 * Android reports "RAM Plus" (Samsung's extended memory) as **swap**, not as
 * physical RAM: `MemTotal` stays at the physical size and the extra capacity
 * shows up under `SwapTotal`. So a device with 12 GB physical + 8 GB RAM Plus
 * reports MemTotal ≈ 12 GB and SwapTotal ≈ 8 GB. That is why model-size
 * decisions here use [effectiveAvailableBytes] rather than available RAM alone.
 */
data class MemorySnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
    val isLowMemory: Boolean,
) {
    /** True when the kernel exposes swap — RAM Plus and/or zram is active. */
    val hasExtendedMemory: Boolean get() = swapTotalBytes > 0

    /** Headroom the OS can actually give us, counting extended memory. */
    val effectiveAvailableBytes: Long get() = availableRamBytes + swapFreeBytes
}

/** What kind of accelerator the device can actually offer. */
data class AcceleratorSupport(
    /** Vendor NPU runtime libraries found on the device (Qualcomm QNN / Hexagon). */
    val npuRuntimeLibraries: List<String>,
    /** Hexagon DSP architecture stubs, e.g. `libQnnHtpV79Stub.so`. */
    val hexagonStubs: List<String>,
    val nnapiAvailable: Boolean,
    val vulkanAvailable: Boolean,
    val openClAvailable: Boolean,
) {
    /** A vendor NPU runtime is physically present on this device. */
    val hasNpuRuntime: Boolean get() = npuRuntimeLibraries.isNotEmpty()

    /** Best-effort Hexagon architecture tag, e.g. "V79", or null. */
    val hexagonVersion: String?
        get() = hexagonStubs.firstNotNullOfOrNull { name ->
            HEXAGON_VERSION_REGEX.find(name)?.groupValues?.getOrNull(1)
        }

    private companion object {
        val HEXAGON_VERSION_REGEX = Regex("libQnnHtp(V\\d+)Stub\\.so")
    }
}

data class DeviceSnapshot(
    val deviceModel: String,
    val manufacturer: String,
    val socModel: String,
    val socManufacturer: String,
    val supportedAbis: List<String>,
    val cpuCores: Int,
    val memory: MemorySnapshot,
    val accelerators: AcceleratorSupport,
) {
    /** Snapdragon 8 Elite — the SoC in the Galaxy S25 / S25 Ultra. */
    val isSnapdragon8Elite: Boolean
        get() = socModel.contains("SM8750", ignoreCase = true) ||
            socModel.contains("8 Elite", ignoreCase = true)

    /** Galaxy S25 Ultra ships as SM-S938x across regions. */
    val isGalaxyS25Ultra: Boolean
        get() = deviceModel.startsWith("SM-S938", ignoreCase = true)
}

object DeviceCapabilities {

    /**
     * Vendor paths that hold the Qualcomm AI Engine Direct (QNN) runtime when
     * the OEM ships it. Their presence means the NPU is reachable *in
     * principle* — an inference runtime still has to be built against QNN to
     * use it. See [com.example.ondevicellm.llm.BackendResolver].
     */
    private val VENDOR_LIB_DIRS = listOf(
        "/vendor/lib64",
        "/vendor/lib64/rfsa/adsp",
        "/system/vendor/lib64",
        "/system/lib64",
    )

    private val NPU_LIB_PATTERNS = listOf(
        "libQnnHtp",       // Qualcomm Hexagon Tensor Processor
        "libQnnSystem",
        "libQnnGpu",
        "libQnnDsp",
        "libcdsprpc",      // Hexagon RPC transport
        "libhta",
    )

    fun snapshot(context: Context): DeviceSnapshot {
        return DeviceSnapshot(
            deviceModel = Build.MODEL.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            socModel = socModel(),
            socManufacturer = socManufacturer(),
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            memory = readMemory(context),
            accelerators = probeAccelerators(context),
        )
    }

    private fun socModel(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Build.SOC_MODEL
        else -> Build.BOARD.orEmpty().ifBlank { Build.HARDWARE.orEmpty() }
    }

    private fun socManufacturer(): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Build.SOC_MANUFACTURER
        else -> Build.HARDWARE.orEmpty()
    }

    fun readMemory(context: Context): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val meminfo = readProcMeminfo()
        return MemorySnapshot(
            totalRamBytes = info.totalMem,
            availableRamBytes = info.availMem,
            swapTotalBytes = meminfo["SwapTotal"] ?: 0L,
            swapFreeBytes = meminfo["SwapFree"] ?: 0L,
            isLowMemory = info.lowMemory,
        )
    }

    /** Parses `/proc/meminfo` into bytes keyed by field name. */
    private fun readProcMeminfo(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        try {
            File("/proc/meminfo").useLines { lines ->
                for (line in lines) {
                    // Format: "SwapTotal:       8388608 kB"
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val key = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                        .removeSuffix("kB").trim()
                        .toLongOrNull() ?: continue
                    result[key] = value * 1024L
                }
            }
        } catch (_: Exception) {
            // /proc/meminfo can be restricted on some builds; callers treat
            // missing swap data as "no extended memory".
        }
        return result
    }

    private fun probeAccelerators(context: Context): AcceleratorSupport {
        val npuLibs = mutableListOf<String>()
        val hexagonStubs = mutableListOf<String>()

        for (dir in VENDOR_LIB_DIRS) {
            val files = try {
                File(dir).listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

            for (file in files) {
                val name = file.name
                if (NPU_LIB_PATTERNS.any { name.startsWith(it) }) {
                    npuLibs += name
                    if (name.startsWith("libQnnHtpV") && name.endsWith("Stub.so")) {
                        hexagonStubs += name
                    }
                }
            }
        }

        val pm = context.packageManager
        return AcceleratorSupport(
            npuRuntimeLibraries = npuLibs.distinct().sorted(),
            hexagonStubs = hexagonStubs.distinct().sorted(),
            // NNAPI exists since API 27 but is deprecated from Android 15 (API 35)
            // in favour of vendor delegates such as QNN.
            nnapiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM,
            vulkanAvailable = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION),
            openClAvailable = File("/vendor/lib64/libOpenCL.so").exists() ||
                File("/system/vendor/lib64/libOpenCL.so").exists(),
        )
    }
}

/** Formats a byte count as a short human-readable string, e.g. "11.4 GB". */
fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}"
    else String.format("%.1f %s", value, units[unit])
}
