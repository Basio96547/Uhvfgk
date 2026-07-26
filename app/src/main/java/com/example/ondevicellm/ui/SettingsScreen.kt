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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        SettingsCard("Reasoning") {
            ToggleRow(
                label = "Enable thinking",
                description = "Let reasoning-capable models think before answering.",
                checked = settings.thinkingEnabled,
                onChange = { value ->
                    viewModel.updateSettings { it.copy(thinkingEnabled = value) }
                },
            )
            ToggleRow(
                label = "Show reasoning",
                description = "Display the collapsible thinking trace in chat.",
                checked = settings.showThinking,
                onChange = { value ->
                    viewModel.updateSettings { it.copy(showThinking = value) }
                },
            )
            Text(
                "Reasoning is detected from <think>…</think> in the model's output. " +
                    "Mark a model as reasoning-capable on the Models screen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard("System prompt") {
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(systemPrompt = value) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. You are a concise assistant.") },
                minLines = 3,
            )
            Text(
                "Prepended to each message. Takes effect on the next message.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard("Voice input") {
            Text(
                if (viewModel.speechAvailable) {
                    if (viewModel.speechOnDevice) {
                        "On-device recognition available — speech stays on the phone."
                    } else {
                        "Recognition available, but no on-device pack was found. " +
                            "Install an offline language pack in system settings to " +
                            "keep transcription local."
                    }
                } else {
                    "No speech recognizer on this device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = settings.voiceLanguageTag,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(voiceLanguageTag = value) }
                },
                label = { Text("Language tag") },
                placeholder = { Text("ar-SA, en-US, …") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
