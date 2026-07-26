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
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Severity
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.ui.theme.Gradients
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.panel

@Composable
fun DeviceScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val device by viewModel.device.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val problems by ErrorLog.entries.collectAsStateWithLifecycle()
    var showDiagnostics by remember { mutableStateOf(false) }

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
                subtitle = s.snapdragonNote,
            )
        }

        SectionCard(
            icon = Icons.Filled.DeveloperBoard,
            title = s.hardware,
            subtitle = device.socModel.ifBlank { s.unknownSoc },
            trailing = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = viewModel::refreshDevice)
                        .padding(Space.sm)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = s.refresh,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            InfoRow(s.model, device.deviceModel.ifBlank { s.unknown })
            InfoRow(s.manufacturer, device.manufacturer.ifBlank { s.unknown })
            InfoRow(s.socVendor, device.socManufacturer.ifBlank { s.unknown })
            InfoRow(s.cpuCores, device.cpuCores.toString())
            InfoRow(s.abis, device.supportedAbis.joinToString(", ").ifBlank { s.unknown })
        }

        SectionCard(
            icon = Icons.Filled.Memory,
            title = s.memory,
            subtitle = s.physicalAndExtended,
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
                    s.freeOf(memory.totalRamBytes.formatBytes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            Spacer(Modifier.height(Space.md))
            ProgressBar(fraction, color = MaterialTheme.colorScheme.tertiary, height = 8)
            Spacer(Modifier.height(6.dp))
            Text(
                s.percentInUse((fraction * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SoftDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.ramPlus,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    if (memory.hasExtendedMemory) s.ramPlusOn else s.off,
                    color = if (memory.hasExtendedMemory) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.height(Space.sm))

            if (memory.hasExtendedMemory) {
                InfoRow(s.total, memory.swapTotalBytes.formatBytes())
                InfoRow(s.free, memory.swapFreeBytes.formatBytes())
                Spacer(Modifier.height(Space.sm))
                Caption(
                    s.mmapNote
                )
            } else {
                Caption(
                    s.ramPlusOff
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
                        s.effectiveBudget,
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
                Caption(s.effectiveBudgetNote)
            }
        }

        SectionCard(
            icon = Icons.Filled.Bolt,
            title = s.accelerators,
            subtitle = device.accelerators.hexagonVersion?.let { "Hexagon $it" }
                ?: s.gpuAndNpu,
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            val acc = device.accelerators
            CapabilityRow(s.vulkan, acc.vulkanAvailable)
            CapabilityRow(s.openCl, acc.openClAvailable)
            CapabilityRow(s.nnapi, acc.nnapiAvailable, s.deprecatedOn15)
            CapabilityRow(s.vendorNpuRuntime, acc.hasNpuRuntime)

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
                s.howNpuBehaves,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Space.xs))
            Caption(
                s.npuExplanation
            )
        }

        SectionCard(
            icon = Icons.Filled.Storage,
            title = s.storage,
            subtitle = s.whereModelsLive,
        ) {
            InfoRow(s.freeSpace, viewModel.registry.managedDir.usableSpace.formatBytes())
            Spacer(Modifier.height(Space.sm))
            Caption(viewModel.registry.managedDir.absolutePath)
        }

        SectionCard(
            icon = Icons.Filled.ReportProblem,
            title = s.diagnostics,
            subtitle = s.whatWentWrong,
            tint = if (problems.any { it.severity >= Severity.ERROR }) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.secondary
            },
        ) {
            val errors = problems.count { it.severity >= Severity.ERROR }
            val warnings = problems.count { it.severity == Severity.WARNING }

            InfoRow(
                s.recordedEvents,
                if (problems.isEmpty()) "none" else problems.size.toString(),
            )
            if (errors > 0) {
                InfoRow(
                    s.errorsAndCrashes,
                    errors.toString(),
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }
            if (warnings > 0) InfoRow(s.warnings, warnings.toString())

            Spacer(Modifier.height(Space.md))
            Caption(
                s.diagnosticsIntro
            )
            Spacer(Modifier.height(Space.md))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                    .clickable {
                        ErrorLog.markSeen()
                        showDiagnostics = true
                    }
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            ) {
                Text(
                    if (problems.isEmpty()) s.openLog else s.reviewCount(problems.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(Space.xl))
    }

    if (showDiagnostics) {
        DiagnosticsDialog(onDismiss = { showDiagnostics = false })
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
    val s = LocalStrings.current
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
            if (available) s.availableWord else (note ?: s.notFound),
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
