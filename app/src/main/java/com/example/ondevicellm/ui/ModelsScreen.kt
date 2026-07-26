package com.example.ondevicellm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.model.BackendPref
import com.example.ondevicellm.model.ModelKind
import com.example.ondevicellm.model.ModelSpec
import com.example.ondevicellm.ui.theme.Gradients
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.panel

private fun iconFor(kind: ModelKind): ImageVector = when (kind) {
    ModelKind.TEXT -> Icons.Filled.TextFields
    ModelKind.ASR -> Icons.Filled.Mic
    ModelKind.TTS -> Icons.Filled.RecordVoiceOver
    ModelKind.MULTIMODAL -> Icons.Filled.ViewInAr
}

/** Each model type gets a stable hue so the list is scannable at a glance. */
@Composable
private fun colorFor(kind: ModelKind): Color = when (kind) {
    ModelKind.TEXT -> MaterialTheme.colorScheme.primary
    ModelKind.ASR -> MaterialTheme.colorScheme.tertiary
    ModelKind.TTS -> Color(0xFFE08A00)
    ModelKind.MULTIMODAL -> Color(0xFFC2410C)
}

@Composable
fun ModelsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedText by viewModel.registry.selectedTextModelId.collectAsStateWithLifecycle()
    val selectedAsr by viewModel.registry.selectedAsrModelId.collectAsStateWithLifecycle()
    val selectedTts by viewModel.registry.selectedTtsModelId.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ModelSpec?>(null) }
    var pathDialog by remember { mutableStateOf(false) }

    // Model bundles have no registered MIME type, so accept any file.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importModel) }

    Column(modifier = modifier.fillMaxSize()) {

        Column(Modifier.padding(horizontal = Space.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                PrimaryAction(
                    icon = Icons.Filled.Add,
                    label = "Add model",
                    enabled = state.importState == null,
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                )
                SecondaryAction(Icons.Filled.Search, "Scan", viewModel::scanForModels)
                SecondaryAction(Icons.Filled.FolderOpen, "Path") { pathDialog = true }
            }

            AnimatedVisibility(
                visible = state.importState != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                state.importState?.let { import ->
                    Column(Modifier.padding(top = Space.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                import.fileName,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(import.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
                        }
                        GradientProgressBar(import.fraction, Gradients.accent)
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))
        }

        if (models.isEmpty()) {
            EmptyModels()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Space.lg,
                    end = Space.lg,
                    bottom = Space.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                val active = setOfNotNull(selectedText, selectedAsr, selectedTts)
                items(models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isActive = model.id in active,
                        onUse = {
                            if (model.kind.isConversational) viewModel.loadModel(model)
                            else viewModel.registry.select(model)
                        },
                        onEdit = { editing = model },
                        onDelete = { viewModel.removeModel(model) },
                    )
                }
            }
        }
    }

    editing?.let { model ->
        ModelSettingsDialog(
            model = model,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateModel(it)
                editing = null
            },
        )
    }

    if (pathDialog) {
        PathDialog(
            onDismiss = { pathDialog = false },
            onConfirm = { path ->
                pathDialog = false
                viewModel.registerModelPath(path)
            },
        )
    }
}

@Composable
private fun PrimaryAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (enabled) Modifier.background(Gradients.accent)
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(46.dp)
            .panel(shape = RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyModels() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Space.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Gradients.halo(MaterialTheme.colorScheme.primary)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ViewInAr,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            "Nothing installed yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Pick a model file with Add, or push one from your computer:",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.md))
        Box(
            Modifier
                .panel(shape = MaterialTheme.shapes.small)
                .padding(horizontal = Space.md, vertical = Space.sm)
        ) {
            Text(
                "adb push model.task /data/local/tmp/llm/",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: ModelSpec,
    isActive: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = colorFor(model.kind)

    Column(
        Modifier
            .fillMaxWidth()
            .panel(
                color = if (isActive) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            )
            .padding(Space.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(iconFor(model.kind), accent, size = 42)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${model.kind.label} · ${model.sizeBytes.formatBytes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                StatusPill("Active", icon = Icons.Filled.Check, filled = true, color = accent)
            }
        }

        Spacer(Modifier.height(Space.md))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (model.kind.isConversational) {
                StatusPill(model.backend.label, color = MaterialTheme.colorScheme.secondary)
                StatusPill(
                    "${model.maxTokens} tok",
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (model.supportsThinking) {
                StatusPill(
                    "Reasoning",
                    icon = Icons.Filled.Psychology,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (model.kind == ModelKind.TTS) {
                StatusPill(
                    "${model.ttsSampleRateHz / 1000} kHz",
                    icon = Icons.Filled.GraphicEq,
                    color = accent,
                )
            }
            if (!model.managed) {
                StatusPill("Linked", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SoftDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            CardAction(Icons.Filled.Tune, "Tune", onEdit)
            Spacer(Modifier.width(Space.sm))
            CardAction(
                Icons.Filled.Delete,
                "Remove",
                { confirmDelete = true },
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.weight(1f))
            if (!isActive) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(onClick = onUse)
                        .padding(horizontal = Space.lg, vertical = Space.sm),
                ) {
                    Text(
                        if (model.kind.isConversational) "Load" else "Use",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Remove model?") },
            text = {
                Text(
                    if (model.managed) {
                        "\"${model.displayName}\" will be deleted from app storage, " +
                            "freeing ${model.sizeBytes.formatBytes()}."
                    } else {
                        "\"${model.displayName}\" will be removed from this list. " +
                            "The file on disk is left untouched."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelSettingsDialog(
    model: ModelSpec,
    onDismiss: () -> Unit,
    onSave: (ModelSpec) -> Unit,
) {
    var draft by remember { mutableStateOf(model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(iconFor(draft.kind), contentDescription = null) },
        title = { Text(model.displayName, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                GroupLabel("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = { draft = draft.copy(kind = kind) },
                            label = { Text(kind.label) },
                        )
                    }
                }

                // Only show the fields that matter for the chosen type.
                if (draft.kind.isConversational) {
                    GroupLabel("Backend")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BackendPref.entries.forEach { backend ->
                            FilterChip(
                                selected = draft.backend == backend,
                                onClick = { draft = draft.copy(backend = backend) },
                                label = { Text(backend.label) },
                            )
                        }
                    }

                    GroupLabel("Generation")
                    NumberField("Max tokens", draft.maxTokens.toString()) {
                        draft = draft.copy(maxTokens = it.toIntOrNull() ?: draft.maxTokens)
                    }
                    NumberField("Temperature", draft.temperature.toString()) {
                        draft = draft.copy(temperature = it.toFloatOrNull() ?: draft.temperature)
                    }
                    NumberField("Top-K", draft.topK.toString()) {
                        draft = draft.copy(topK = it.toIntOrNull() ?: draft.topK)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Emits reasoning", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Wraps its thinking in <think> tags",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.supportsThinking,
                            onCheckedChange = { draft = draft.copy(supportsThinking = it) },
                        )
                    }
                }

                if (draft.kind == ModelKind.TTS) {
                    GroupLabel("Voice output")
                    NumberField("Sample rate (Hz)", draft.ttsSampleRateHz.toString()) {
                        draft = draft.copy(
                            ttsSampleRateHz = it.toIntOrNull() ?: draft.ttsSampleRateHz
                        )
                    }
                    NumberField("Speaker id", draft.ttsSpeakerId.toString()) {
                        draft = draft.copy(ttsSpeakerId = it.toIntOrNull() ?: draft.ttsSpeakerId)
                    }
                    Text(
                        "The sample rate must match the model's training output, or " +
                            "speech plays too fast or too slow. Put a " +
                            "\"<name>.tokens.json\" vocabulary next to the model file " +
                            "for correct pronunciation.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PathDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var path by remember { mutableStateOf("/data/local/tmp/llm/model.task") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
        title = { Text("Link by path") },
        text = {
            Column {
                Text(
                    "For models pushed with adb. The file is referenced in place — " +
                        "nothing is copied, so no extra storage is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.md))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Absolute path") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(path) }) { Text("Link") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
