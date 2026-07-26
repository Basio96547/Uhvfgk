package com.example.ondevicellm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.formatBytes
import com.example.ondevicellm.model.BackendPref
import com.example.ondevicellm.model.ModelKind
import com.example.ondevicellm.model.ModelSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTextId by viewModel.registry.selectedTextModelId.collectAsStateWithLifecycle()
    val selectedAudioId by viewModel.registry.selectedAudioModelId.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ModelSpec?>(null) }
    var pathDialog by remember { mutableStateOf(false) }

    // Model bundles have no registered MIME type, so accept any file.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importModel) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        Text("Models", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Add .task / .litertlm bundles. Everything runs offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = state.importState == null,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add model")
            }
            OutlinedButton(onClick = viewModel::scanForModels) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Scan")
            }
            OutlinedButton(onClick = { pathDialog = true }) { Text("Path…") }
        }

        state.importState?.let {
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${it.fileName} ${(it.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::cancelImport) { Text("Cancel") }
            }
        }

        Spacer(Modifier.size(12.dp))

        if (models.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No models yet.\n\nAdd one with the button above, or push it with:\n" +
                        "adb push model.task /data/local/tmp/llm/",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isSelected = model.id == selectedTextId || model.id == selectedAudioId,
                        onLoad = { viewModel.loadModel(model) },
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
private fun ModelCard(
    model: ModelSpec,
    isSelected: Boolean,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${model.kind.label} · ${model.sizeBytes.formatBytes()} · " +
                            "${model.backend.label}${if (model.managed) "" else " · in place"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }

            Text(
                model.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.size(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSelected) {
                    AssistChip(onClick = {}, label = { Text("Selected") })
                }
                if (model.supportsThinking) {
                    AssistChip(onClick = {}, label = { Text("Thinking") })
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Settings") }
                if (model.kind != ModelKind.AUDIO) {
                    Button(onClick = onLoad) { Text("Load") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove model?") },
            text = {
                Text(
                    if (model.managed) {
                        "\"${model.displayName}\" will be deleted from app storage " +
                            "(${model.sizeBytes.formatBytes()} freed)."
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
        title = { Text(model.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelKind.entries.forEach { kind ->
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = { draft = draft.copy(kind = kind) },
                            label = { Text(kind.label) },
                        )
                    }
                }

                Text("Backend", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BackendPref.entries.forEach { backend ->
                        FilterChip(
                            selected = draft.backend == backend,
                            onClick = { draft = draft.copy(backend = backend) },
                            label = { Text(backend.label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.maxTokens.toString(),
                    onValueChange = {
                        draft = draft.copy(maxTokens = it.toIntOrNull() ?: draft.maxTokens)
                    },
                    label = { Text("Max tokens") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = draft.temperature.toString(),
                    onValueChange = {
                        draft = draft.copy(temperature = it.toFloatOrNull() ?: draft.temperature)
                    },
                    label = { Text("Temperature") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = draft.topK.toString(),
                    onValueChange = { draft = draft.copy(topK = it.toIntOrNull() ?: draft.topK) },
                    label = { Text("Top-K") },
                    singleLine = true,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Emits reasoning (<think>)", Modifier.weight(1f))
                    Switch(
                        checked = draft.supportsThinking,
                        onCheckedChange = { draft = draft.copy(supportsThinking = it) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PathDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var path by remember { mutableStateOf("/data/local/tmp/llm/model.task") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register model by path") },
        text = {
            Column {
                Text(
                    "Use this for models pushed with adb. The file is referenced " +
                        "in place — nothing is copied.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Absolute path") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(path) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
