package com.example.ondevicellm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.ui.theme.Gradients
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.panel

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
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        if (device.isGalaxyS25Ultra || device.isSnapdragon8Elite) {
            HeroBanner(
                title = if (device.isGalaxyS25Ultra) {
                    "Galaxy S25 Ultra"
                } else {
                    "Snapdragon 8 Elite"
                },
                subtitle = "High-end Qualcomm silicon detected. GPU backend recommended.",
            )
        }

        SectionCard(
            icon = Icons.Filled.DeveloperBoard,
            title = "Hardware",
            subtitle = device.socModel.ifBlank { "Unknown SoC" },
            trailing = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = viewModel::refreshDevice)
                        .padding(Space.sm)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            InfoRow("Model", device.deviceModel.ifBlank { "unknown" })
            InfoRow("Manufacturer", device.manufacturer.ifBlank { "unknown" })
            InfoRow("SoC vendor", device.socManufacturer.ifBlank { "unknown" })
            InfoRow("CPU cores", device.cpuCores.toString())
            InfoRow("ABIs", device.supportedAbis.joinToString(", ").ifBlank { "unknown" })
        }

        SectionCard(
            icon = Icons.Filled.Memory,
            title = "Memory",
            subtitle = "Physical and extended",
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            val used = (memory.totalRamBytes - memory.availableRamBytes).coerceAtLeast(0)
            val fraction = if (memory.totalRamBytes > 0) {
                used.toFloat() / memory.totalRamBytes
            } else 0f

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    memory.availableRamBytes.formatBytes(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "free of ${memory.totalRamBytes.formatBytes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            Spacer(Modifier.height(Space.md))
            ProgressBar(fraction, color = MaterialTheme.colorScheme.tertiary, height = 8)
            Spacer(Modifier.height(6.dp))
            Text(
                "${(fraction * 100).toInt()}% in use",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SoftDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "RAM Plus",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
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

            Spacer(Modifier.height(Space.sm))

            if (memory.hasExtendedMemory) {
                InfoRow("Total", memory.swapTotalBytes.formatBytes())
                InfoRow("Free", memory.swapFreeBytes.formatBytes())
                Spacer(Modifier.height(Space.sm))
                Caption(
                    "Model weights are memory-mapped, so pages can spill into " +
                        "extended memory instead of failing to allocate."
                )
            } else {
                Caption(
                    "Not enabled. Turn it on in Settings › Device care › Memory › " +
                        "RAM Plus to give large models more headroom."
                )
            }

            Spacer(Modifier.height(Space.lg))

            Column(
                Modifier
                    .fillMaxWidth()
                    .panel(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f),
                    )
                    .padding(Space.md)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "Effective budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        memory.effectiveAvailableBytes.formatBytes(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Caption("Available RAM + free extended memory. Decides what can load.")
            }
        }

        SectionCard(
            icon = Icons.Filled.Bolt,
            title = "Accelerators",
            subtitle = device.accelerators.hexagonVersion?.let { "Hexagon $it" }
                ?: "GPU and NPU support",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            val acc = device.accelerators
            CapabilityRow("Vulkan", acc.vulkanAvailable)
            CapabilityRow("OpenCL", acc.openClAvailable)
            CapabilityRow("NNAPI", acc.nnapiAvailable, "deprecated on Android 15+")
            CapabilityRow("Vendor NPU runtime", acc.hasNpuRuntime)

            if (acc.hasNpuRuntime) {
                Spacer(Modifier.height(Space.md))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .panel(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        .padding(Space.md)
                ) {
                    Text(
                        acc.npuRuntimeLibraries.take(10).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SoftDivider()

            Text(
                "How NPU selection behaves",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Space.xs))
            Caption(
                "The bundled LLM runtime exposes CPU and GPU. Choosing \"NPU\" for a " +
                    "model runs it on the GPU and says so on the chat screen rather " +
                    "than pretending. Wiring in Qualcomm's QNN/Genie runtime is done " +
                    "in BackendResolver.kt."
            )
        }

        SectionCard(
            icon = Icons.Filled.Storage,
            title = "Storage",
            subtitle = "Where models live",
        ) {
            InfoRow("Free space", viewModel.registry.managedDir.usableSpace.formatBytes())
            Spacer(Modifier.height(Space.sm))
            Caption(viewModel.registry.managedDir.absolutePath)
        }

        Spacer(Modifier.height(Space.xl))
    }
}

@Composable
private fun HeroBanner(title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .panel(
                brush = Gradients.accentSoft,
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(Space.md))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Capability line with a coloured dot — reads faster than yes/no text. */
@Composable
private fun CapabilityRow(label: String, available: Boolean, note: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (available) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.outlineVariant
                )
        )
        Spacer(Modifier.width(Space.md))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            if (available) "available" else (note ?: "not found"),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (available) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
