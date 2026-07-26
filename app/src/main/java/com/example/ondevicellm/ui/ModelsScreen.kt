package com.example.ondevicellm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

private fun iconFor(kind: ModelKind): ImageVector = when (kind) {
    ModelKind.TEXT -> Icons.Filled.TextFields
    ModelKind.ASR -> Icons.Filled.Mic
    ModelKind.TTS -> Icons.Filled.RecordVoiceOver
    ModelKind.MULTIMODAL -> Icons.Filled.ViewInAr
}

@Composable
private fun colorFor(kind: ModelKind): Color = when (kind) {
    ModelKind.TEXT -> MaterialTheme.colorScheme.primary
    ModelKind.ASR -> MaterialTheme.colorScheme.tertiary
    ModelKind.TTS -> Color(0xFFB4690E)
    ModelKind.MULTIMODAL -> MaterialTheme.colorScheme.secondary
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

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text("Models", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(3.dp))
            Text(
                "Chat, speech-to-text and text-to-speech models. All offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = state.importState == null,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Add")
                }
                OutlinedButton(onClick = viewModel::scanForModels) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Scan")
                }
                OutlinedButton(onClick = { pathDialog = true }) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Path")
                }
            }

            state.importState?.let { import ->
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${import.fileName} ${(import.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
                }
                ProgressBar(import.fraction)
            }
        }

        if (models.isEmpty()) {
            EmptyModels()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isSelected = model.id == selectedText ||
                            model.id == selectedAsr ||
                            model.id == selectedTts,
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
private fun EmptyModels() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ViewInAr,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.size(18.dp))
        Text("No models yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(7.dp))
        Text(
            "Tap Add to pick a file, or push one to the device:",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "adb push model.task /data/local/tmp/llm/",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelSpec,
    isSelected: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = colorFor(model.kind)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accent.copy(alpha = 0.14f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        iconFor(model.kind),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Spacer(Modifier.size(11.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${model.kind.label} · ${model.sizeBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.size(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSelected) {
                        StatusPill(
                            "Active",
                            icon = Icons.Filled.Check,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (model.kind.isConversational) {
                        StatusPill(model.backend.label, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (model.supportsThinking) {
                        StatusPill(
                            "Thinking",
                            icon = Icons.Filled.Psychology,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (model.kind == ModelKind.TTS) {
                        StatusPill("${model.ttsSampleRateHz / 1000} kHz", color = accent)
                    }
                }

                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(5.dp))
                    Text("Tune")
                }
                if (!isSelected) {
                    Button(onClick = onUse) {
                        Text(if (model.kind.isConversational) "Load" else "Use")
                    }
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
                        "\"${model.displayName}\" will be removed from the list. " +
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FieldLabel("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = { draft = draft.copy(kind = kind) },
                            label = { Text(kind.label) },
                        )
                    }
                }

                // Generation settings only make sense for conversational models.
                if (draft.kind.isConversational) {
                    FieldLabel("Backend")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BackendPref.entries.forEach { backend ->
                            FilterChip(
                                selected = draft.backend == backend,
                                onClick = { draft = draft.copy(backend = backend) },
                                label = { Text(backend.label) },
                            )
                        }
                    }

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
                                "Model wraps its thinking in <think> tags",
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
                    FieldLabel("Voice output")
                    NumberField("Sample rate (Hz)", draft.ttsSampleRateHz.toString()) {
                        draft = draft.copy(
                            ttsSampleRateHz = it.toIntOrNull() ?: draft.ttsSampleRateHz
                        )
                    }
                    NumberField("Speaker id", draft.ttsSpeakerId.toString()) {
                        draft = draft.copy(ttsSpeakerId = it.toIntOrNull() ?: draft.ttsSpeakerId)
                    }
                    Text(
                        "The sample rate must match what the model was trained to " +
                            "output, or speech plays too fast or too slow. Place a " +
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
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PathDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var path by remember { mutableStateOf("/data/local/tmp/llm/model.task") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
        title = { Text("Register by path") },
        text = {
            Column {
                Text(
                    "For models pushed with adb. The file is referenced in place — " +
                        "nothing is copied, so no extra storage is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Absolute path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(path) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
