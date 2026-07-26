package com.example.ondevicellm.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.TtsEngine

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ttsModel = viewModel.registry.selectedTtsModel

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(4.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 4.dp),
        )

        SectionCard(Icons.Filled.Psychology, "Reasoning") {
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
            Spacer(Modifier.size(4.dp))
            Hint(
                "Reasoning is detected from <think>…</think> in the model's output. " +
                    "Mark a model as reasoning-capable on the Models screen."
            )
        }

        SectionCard(Icons.Filled.RecordVoiceOver, "Speech output") {
            Text("Engine", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TtsEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.ttsEngine == engine,
                        onClick = { viewModel.updateSettings { it.copy(ttsEngine = engine) } },
                        label = { Text(engine.label) },
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            when (settings.ttsEngine) {
                TtsEngine.SYSTEM -> Hint(
                    "Uses Android's built-in engine. Works out of the box and stays " +
                        "offline once a voice pack is installed."
                )

                TtsEngine.MODEL -> when {
                    !viewModel.ttsRuntimeAvailable -> Hint(
                        "This build doesn't bundle the LiteRT runtime, so custom TTS " +
                            "models can't run — the app ships that way on purpose so " +
                            "nothing can conflict with the LLM runtime. Use the system " +
                            "engine, or in app/build.gradle.kts change compileOnly(…" +
                            "tensorflow-lite…) to implementation(…) and rebuild.",
                        isWarning = true,
                    )

                    ttsModel != null -> Hint(
                        "Using \"${ttsModel.displayName}\" at ${ttsModel.ttsSampleRateHz} Hz."
                    )

                    else -> Hint(
                        "No text-to-speech model selected. Add one on the Models " +
                            "screen and set its type to \"Text → Speech\".",
                        isWarning = true,
                    )
                }
            }

            Spacer(Modifier.size(10.dp))

            ToggleRow(
                label = "Speak replies automatically",
                description = "Read each reply aloud as soon as it finishes.",
                checked = settings.autoSpeakReplies,
                onChange = { value ->
                    viewModel.updateSettings { it.copy(autoSpeakReplies = value) }
                },
            )

            Spacer(Modifier.size(6.dp))

            SliderRow(
                label = "Speed",
                value = settings.speakingRate,
                range = 0.5f..2.0f,
                onChange = { value ->
                    viewModel.updateSettings { it.copy(speakingRate = value) }
                },
            )

            // Pitch is a system-engine feature; model synthesis ignores it.
            if (settings.ttsEngine == TtsEngine.SYSTEM) {
                SliderRow(
                    label = "Pitch",
                    value = settings.pitch,
                    range = 0.5f..1.5f,
                    onChange = { value -> viewModel.updateSettings { it.copy(pitch = value) } },
                )
            }
        }

        SectionCard(Icons.Filled.Mic, "Voice input") {
            Hint(
                if (!viewModel.speechAvailable) {
                    "No speech recognizer is available on this device."
                } else if (viewModel.speechOnDevice) {
                    "On-device recognition available — your speech stays on the phone."
                } else {
                    "Recognition is available, but no on-device pack was found. " +
                        "Install an offline language pack in system settings to keep " +
                        "transcription local."
                },
                isWarning = viewModel.speechAvailable && !viewModel.speechOnDevice,
            )
            Spacer(Modifier.size(10.dp))
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
            Spacer(Modifier.size(4.dp))
            Hint("Applies to both voice input and speech output.")
        }

        SectionCard(Icons.Filled.Terminal, "System prompt") {
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(systemPrompt = value) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. You are a concise assistant.") },
                minLines = 3,
            )
            Spacer(Modifier.size(6.dp))
            Hint("Prepended to each message. Takes effect on the next message.")
        }

        Spacer(Modifier.size(16.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
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
        Spacer(Modifier.size(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                String.format("%.2f×", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Hint(text: String, isWarning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isWarning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
