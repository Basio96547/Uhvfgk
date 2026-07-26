package com.example.ondevicellm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Device", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = viewModel::refreshDevice) { Text("Refresh") }
        }

        InfoCard("Hardware") {
            InfoRow("Model", device.deviceModel)
            InfoRow("Manufacturer", device.manufacturer)
            InfoRow("SoC", device.socModel.ifBlank { "unknown" })
            InfoRow("SoC vendor", device.socManufacturer.ifBlank { "unknown" })
            InfoRow("CPU cores", device.cpuCores.toString())
            InfoRow("ABIs", device.supportedAbis.joinToString(", "))

            if (device.isGalaxyS25Ultra) {
                Spacer(Modifier.size(6.dp))
                Highlight("Galaxy S25 Ultra detected.")
            }
            if (device.isSnapdragon8Elite) {
                Highlight("Snapdragon 8 Elite detected — GPU backend recommended.")
            }
        }

        InfoCard("Memory") {
            val used = (memory.totalRamBytes - memory.availableRamBytes)
                .coerceAtLeast(0)
            val fraction = if (memory.totalRamBytes > 0) {
                used.toFloat() / memory.totalRamBytes
            } else 0f

            InfoRow("Physical RAM", memory.totalRamBytes.formatBytes())
            InfoRow("Available", memory.availableRamBytes.formatBytes())
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            Spacer(Modifier.size(4.dp))
            Text("Extended memory (RAM Plus / zram)", style = MaterialTheme.typography.labelLarge)

            if (memory.hasExtendedMemory) {
                InfoRow("Total", memory.swapTotalBytes.formatBytes())
                InfoRow("Free", memory.swapFreeBytes.formatBytes())
                Highlight(
                    "Active. Model weights are memory-mapped, so pages can spill " +
                        "into extended memory instead of failing to allocate."
                )
            } else {
                Text(
                    "Not enabled. Turn it on in Settings › Device care › Memory › " +
                        "RAM Plus to give large models more headroom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(6.dp))
            InfoRow(
                "Effective budget",
                memory.effectiveAvailableBytes.formatBytes(),
            )
            Text(
                "Available RAM + free extended memory. Used to decide whether a " +
                    "model can be loaded.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoCard("Accelerators") {
            val acc = device.accelerators
            InfoRow("Vulkan", if (acc.vulkanAvailable) "yes" else "no")
            InfoRow("OpenCL", if (acc.openClAvailable) "yes" else "no")
            InfoRow("NNAPI", if (acc.nnapiAvailable) "yes" else "no (deprecated on Android 15+)")
            InfoRow("Vendor NPU runtime", if (acc.hasNpuRuntime) "detected" else "not found")
            acc.hexagonVersion?.let { InfoRow("Hexagon arch", it) }

            if (acc.hasNpuRuntime) {
                Spacer(Modifier.size(6.dp))
                Text(
                    "NPU libraries found:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    acc.npuRuntimeLibraries.take(12).joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(8.dp))
            Text(
                "How NPU selection behaves",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "The MediaPipe LLM runtime bundled here exposes CPU and GPU only. " +
                    "Selecting \"NPU\" for a model runs it on the GPU and tells you so " +
                    "on the chat screen — it never silently pretends. Driving the " +
                    "Hexagon NPU needs Qualcomm's QNN/Genie runtime plus a model " +
                    "compiled to a QNN context binary; the integration point is " +
                    "NpuRuntime in BackendResolver.kt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Highlight(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
