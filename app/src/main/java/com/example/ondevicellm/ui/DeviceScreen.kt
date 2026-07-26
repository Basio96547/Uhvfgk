package com.example.ondevicellm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.formatBytes

@Composable
fun DeviceScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Device",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            OutlinedButton(onClick = viewModel::refreshDevice) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Refresh")
            }
        }

        if (device.isGalaxyS25Ultra || device.isSnapdragon8Elite) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(11.dp))
                    Column {
                        Text(
                            if (device.isGalaxyS25Ultra) "Galaxy S25 Ultra" else "Snapdragon 8 Elite",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "High-end Qualcomm hardware — GPU backend recommended.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        SectionCard(Icons.Filled.Info, "Hardware") {
            InfoRow("Model", device.deviceModel.ifBlank { "unknown" })
            InfoRow("Manufacturer", device.manufacturer.ifBlank { "unknown" })
            InfoRow("SoC", device.socModel.ifBlank { "unknown" })
            InfoRow("SoC vendor", device.socManufacturer.ifBlank { "unknown" })
            InfoRow("CPU cores", device.cpuCores.toString())
            InfoRow("ABIs", device.supportedAbis.joinToString(", ").ifBlank { "unknown" })
        }

        SectionCard(Icons.Filled.Memory, "Memory", tint = MaterialTheme.colorScheme.tertiary) {
            val usedRam = (memory.totalRamBytes - memory.availableRamBytes).coerceAtLeast(0)
            val ramFraction = if (memory.totalRamBytes > 0) {
                usedRam.toFloat() / memory.totalRamBytes
            } else 0f

            InfoRow("Physical RAM", memory.totalRamBytes.formatBytes())
            InfoRow("Available", memory.availableRamBytes.formatBytes())
            Spacer(Modifier.size(8.dp))
            ProgressBar(ramFraction, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.size(4.dp))
            Text(
                "${(ramFraction * 100).toInt()}% in use",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Extended memory (RAM Plus)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    if (memory.hasExtendedMemory) "Active" else "Off",
                    color = if (memory.hasExtendedMemory) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.size(8.dp))

            if (memory.hasExtendedMemory) {
                InfoRow("Total", memory.swapTotalBytes.formatBytes())
                InfoRow("Free", memory.swapFreeBytes.formatBytes())
                Spacer(Modifier.size(6.dp))
                Text(
                    "Model weights are memory-mapped, so pages can spill into " +
                        "extended memory instead of failing to allocate.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Not enabled. Turn it on in Settings › Device care › Memory › " +
                        "RAM Plus to give large models more headroom.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(14.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    InfoRow(
                        "Effective budget",
                        memory.effectiveAvailableBytes.formatBytes(),
                        valueColor = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "Available RAM + free extended memory. Used to decide " +
                            "whether a model can be loaded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard(
            Icons.Filled.Bolt,
            "Accelerators",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            val acc = device.accelerators
            InfoRow("Vulkan", if (acc.vulkanAvailable) "yes" else "no")
            InfoRow("OpenCL", if (acc.openClAvailable) "yes" else "no")
            InfoRow("NNAPI", if (acc.nnapiAvailable) "yes" else "no (deprecated on Android 15+)")
            InfoRow("Vendor NPU runtime", if (acc.hasNpuRuntime) "detected" else "not found")
            acc.hexagonVersion?.let { InfoRow("Hexagon arch", it) }

            if (acc.hasNpuRuntime) {
                Spacer(Modifier.size(10.dp))
                Text("NPU libraries found", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(4.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        acc.npuRuntimeLibraries.take(12).joinToString("\n"),
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(12.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "How NPU selection behaves",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        "The MediaPipe LLM runtime bundled here exposes CPU and GPU " +
                            "only. Selecting \"NPU\" for a model runs it on the GPU and " +
                            "says so on the chat screen — it never silently pretends. " +
                            "Driving the Hexagon NPU needs Qualcomm's QNN/Genie runtime " +
                            "plus a model compiled to a QNN context binary; the " +
                            "integration point is NpuRuntime in BackendResolver.kt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionCard(Icons.Filled.Storage, "Storage") {
            InfoRow("Free space", viewModel.registry.managedDir.usableSpace.formatBytes())
            InfoRow("Managed models", viewModel.registry.managedDir.absolutePath)
        }

        Spacer(Modifier.size(16.dp))
    }
}
