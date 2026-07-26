package com.example.ondevicellm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.core.TtsEngine
import com.example.ondevicellm.web.SearchDepth
import com.example.ondevicellm.ui.theme.Space

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
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        SectionCard(
            icon = Icons.Filled.Psychology,
            title = "Reasoning",
            subtitle = "How the model thinks",
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            ToggleRow(
                label = "Enable thinking",
                description = "Let reasoning-capable models think before answering.",
                checked = settings.thinkingEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(thinkingEnabled = v) } },
            )
            ToggleRow(
                label = "Show reasoning",
                description = "Display the collapsible thinking trace in chat.",
                checked = settings.showThinking,
                onChange = { v -> viewModel.updateSettings { it.copy(showThinking = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(
                "Detected from <think>…</think> in the model's output. Mark a model " +
                    "as reasoning-capable on the Models screen."
            )
        }

        SectionCard(
            icon = Icons.Filled.Language,
            title = "Web search",
            subtitle = "Ground answers in live sources",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            ToggleRow(
                label = "Search the web",
                description = "Look up the question before answering, and cite sources.",
                checked = settings.webSearchEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(webSearchEnabled = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(
                if (settings.webSearchEnabled) {
                    "Your questions are sent to DuckDuckGo and Wikipedia. This is the " +
                        "only feature that leaves your device — everything else stays " +
                        "offline. Results are used as context and cited under each reply."
                } else {
                    "Off. The model answers from its own weights and nothing leaves " +
                        "your device. Turning this on sends your questions to " +
                        "DuckDuckGo and Wikipedia."
                },
                isWarning = settings.webSearchEnabled,
            )

            if (settings.webSearchEnabled) {
                SoftDivider()
                GroupLabel("Depth")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchDepth.entries.forEach { depth ->
                        FilterChip(
                            selected = settings.searchDepth == depth,
                            onClick = {
                                viewModel.updateSettings { it.copy(searchDepth = depth) }
                            },
                            label = { Text(depth.label) },
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Caption(
                    when (settings.searchDepth) {
                        SearchDepth.QUICK ->
                            "Uses result snippets. One round of requests, fastest."
                        SearchDepth.DEEP ->
                            "Opens the top three results and reads them, so answers " +
                                "come from page content rather than a two-line " +
                                "snippet. Slower and uses more data."
                    }
                )
            }
        }

        SectionCard(
            icon = Icons.Filled.Thermostat,
            title = "Performance",
            subtitle = "Heat and battery",
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            Caption(
                if (viewModel.thermalSupported) {
                    "The app watches the phone's thermal state and lowers the number " +
                        "of compute threads as it warms up, pausing if it gets too " +
                        "hot. Sustained performance mode is requested so clocks stay " +
                        "steady instead of spiking then throttling."
                } else {
                    "This Android version doesn't report thermal state, so a " +
                        "conservative thread count is used at all times. Sustained " +
                        "performance mode is still requested."
                }
            )
        }

        SectionCard(
            icon = Icons.Filled.RecordVoiceOver,
            title = "Speech output",
            subtitle = "Read replies aloud",
            tint = MaterialTheme.colorScheme.primary,
        ) {
            GroupLabel("Engine")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TtsEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.ttsEngine == engine,
                        onClick = { viewModel.updateSettings { it.copy(ttsEngine = engine) } },
                        label = { Text(engine.label) },
                    )
                }
            }

            Spacer(Modifier.height(Space.md))

            when (settings.ttsEngine) {
                TtsEngine.SYSTEM -> Caption(
                    "Android's built-in engine. Works out of the box and stays offline " +
                        "once a voice pack is installed."
                )

                TtsEngine.MODEL -> when {
                    !viewModel.ttsRuntimeAvailable -> Caption(
                        "This build doesn't bundle the LiteRT runtime, so custom TTS " +
                            "models can't run. Use the system engine, or switch " +
                            "compileOnly(…tensorflow-lite…) to implementation(…) in " +
                            "app/build.gradle.kts and rebuild.",
                        isWarning = true,
                    )

                    ttsModel != null -> Caption(
                        "Using \"${ttsModel.displayName}\" at ${ttsModel.ttsSampleRateHz} Hz."
                    )

                    else -> Caption(
                        "No voice model selected. Add one on the Models screen and set " +
                            "its type to \"Text → Speech\".",
                        isWarning = true,
                    )
                }
            }

            SoftDivider()

            ToggleRow(
                label = "Speak replies automatically",
                description = "Read each reply aloud as soon as it finishes.",
                checked = settings.autoSpeakReplies,
                onChange = { v -> viewModel.updateSettings { it.copy(autoSpeakReplies = v) } },
            )

            Spacer(Modifier.height(Space.sm))

            SliderRow(
                label = "Speed",
                value = settings.speakingRate,
                range = 0.5f..2.0f,
                onChange = { v -> viewModel.updateSettings { it.copy(speakingRate = v) } },
            )

            // Pitch is a system-engine feature; model synthesis ignores it.
            if (settings.ttsEngine == TtsEngine.SYSTEM) {
                SliderRow(
                    label = "Pitch",
                    value = settings.pitch,
                    range = 0.5f..1.5f,
                    onChange = { v -> viewModel.updateSettings { it.copy(pitch = v) } },
                )
            }
        }

        SectionCard(
            icon = Icons.Filled.Mic,
            title = "Voice input",
            subtitle = "Dictate instead of typing",
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            Caption(
                when {
                    !viewModel.speechAvailable ->
                        "No speech recognizer is available on this device."

                    viewModel.speechOnDevice ->
                        "On-device recognition available — your speech stays on the phone."

                    else ->
                        "Recognition available, but no on-device pack was found. Install " +
                            "an offline language pack in system settings to keep " +
                            "transcription local."
                },
                isWarning = viewModel.speechAvailable && !viewModel.speechOnDevice,
            )
        }

        SectionCard(
            icon = Icons.Filled.Translate,
            title = "Language",
            subtitle = "For voice in and out",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            OutlinedTextField(
                value = settings.voiceLanguageTag,
                onValueChange = { v -> viewModel.updateSettings { it.copy(voiceLanguageTag = v) } },
                label = { Text("Language tag") },
                placeholder = { Text("ar-SA, en-US, …") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ar-SA", "ar-EG", "en-US", "en-GB", "fr-FR", "tr-TR").forEach { tag ->
                    FilterChip(
                        selected = settings.voiceLanguageTag == tag,
                        onClick = {
                            viewModel.updateSettings { it.copy(voiceLanguageTag = tag) }
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }

        SectionCard(
            icon = Icons.Filled.Tune,
            title = "System prompt",
            subtitle = "Set the assistant's behaviour",
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { v -> viewModel.updateSettings { it.copy(systemPrompt = v) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. You are a concise assistant.") },
                shape = MaterialTheme.shapes.small,
                minLines = 3,
            )
            Spacer(Modifier.height(Space.sm))
            Caption("Prepended to each message. Takes effect on the next message.")
        }

        Spacer(Modifier.height(Space.xl))
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Space.md))
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
    Column(Modifier.padding(vertical = Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            StatusPill(String.format("%.2f×", value))
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
