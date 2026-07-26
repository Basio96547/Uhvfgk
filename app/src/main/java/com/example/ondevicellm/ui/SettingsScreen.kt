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
import com.example.ondevicellm.core.AppLanguage
import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.core.TtsEngine
import com.example.ondevicellm.llm.RoutingMode
import com.example.ondevicellm.web.SearchDepth
import com.example.ondevicellm.ui.theme.Space

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ttsModel = viewModel.registry.selectedTtsModel

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // First card on the screen: the language you read the rest of it in
        // shouldn't be buried below eight sections you can't read yet.
        SectionCard(
            icon = Icons.Filled.Translate,
            title = s.language,
            subtitle = s.languageSubtitle,
            tint = MaterialTheme.colorScheme.primary,
        ) {
            GroupLabel(s.interfaceLanguage)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = settings.language == language,
                        onClick = {
                            viewModel.updateSettings { current ->
                                // Voice follows the interface unless the user has
                                // already picked a tag of their own.
                                val voice = language.resolve().defaultVoiceTag
                                current.copy(language = language, voiceLanguageTag = voice)
                            }
                        },
                        // Each option is written in its own language, so it is
                        // legible even when the app currently isn't.
                        label = { Text(language.label) },
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            Caption(s.languageNote)
        }

        SectionCard(
            icon = Icons.Filled.Psychology,
            title = s.reasoningTitle,
            subtitle = s.reasoningSubtitle,
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            GroupLabel(s.whenToThink)
            ModeChips(
                selected = settings.thinkingMode,
                strings = s,
                onSelect = { mode -> viewModel.updateSettings { it.copy(thinkingMode = mode) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(
                when (settings.thinkingMode) {
                    RoutingMode.AUTO -> s.thinkAuto
                    RoutingMode.ALWAYS -> s.thinkAlways
                    RoutingMode.NEVER -> s.thinkNever
                }
            )

            SoftDivider()

            ToggleRow(
                label = s.showReasoning,
                description = s.showReasoningDesc,
                checked = settings.showThinking,
                onChange = { v -> viewModel.updateSettings { it.copy(showThinking = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(s.reasoningDetectedNote)
        }

        SectionCard(
            icon = Icons.Filled.Language,
            title = s.webSearch,
            subtitle = s.webSearchSubtitle,
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            ToggleRow(
                label = s.searchTheWeb,
                description = s.searchTheWebDesc,
                checked = settings.webSearchEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(webSearchEnabled = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(
                if (settings.webSearchEnabled) s.searchOnNote else s.searchOffNote,
                isWarning = settings.webSearchEnabled,
            )

            if (settings.webSearchEnabled) {
                SoftDivider()
                GroupLabel(s.whenToSearch)
                ModeChips(
                    selected = settings.searchMode,
                    strings = s,
                    onSelect = { mode -> viewModel.updateSettings { it.copy(searchMode = mode) } },
                )
                Spacer(Modifier.height(Space.sm))
                Caption(
                    when (settings.searchMode) {
                        RoutingMode.AUTO -> s.searchAuto
                        RoutingMode.ALWAYS -> s.searchAlways
                        RoutingMode.NEVER -> s.searchNever
                    }
                )

                SoftDivider()
                GroupLabel(s.depth)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchDepth.entries.forEach { depth ->
                        FilterChip(
                            selected = settings.searchDepth == depth,
                            onClick = {
                                viewModel.updateSettings { it.copy(searchDepth = depth) }
                            },
                            label = {
                                Text(
                                    when (depth) {
                                        SearchDepth.QUICK -> s.depthQuickLabel
                                        SearchDepth.DEEP -> s.depthDeepLabel
                                    }
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Caption(
                    when (settings.searchDepth) {
                        SearchDepth.QUICK -> s.depthQuick
                        SearchDepth.DEEP -> s.depthDeep
                    }
                )
            }
        }

        SectionCard(
            icon = Icons.Filled.Thermostat,
            title = s.performance,
            subtitle = s.performanceSubtitle,
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            Caption(
                if (viewModel.thermalSupported) {
                    s.thermalSupportedNote
                } else {
                    s.thermalUnsupportedNote
                }
            )
        }

        SectionCard(
            icon = Icons.Filled.RecordVoiceOver,
            title = s.speechOutput,
            subtitle = s.speechOutputSubtitle,
            tint = MaterialTheme.colorScheme.primary,
        ) {
            GroupLabel(s.engine)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TtsEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.ttsEngine == engine,
                        onClick = { viewModel.updateSettings { it.copy(ttsEngine = engine) } },
                        label = { Text(engine.label(s)) },
                    )
                }
            }

            Spacer(Modifier.height(Space.md))

            when (settings.ttsEngine) {
                TtsEngine.SYSTEM -> Caption(s.systemEngineNote)

                TtsEngine.MODEL -> when {
                    !viewModel.ttsRuntimeAvailable -> Caption(s.litertMissingNote, isWarning = true)

                    ttsModel != null -> Caption(
                        s.usingVoiceModel(ttsModel.displayName, ttsModel.ttsSampleRateHz)
                    )

                    else -> Caption(s.noVoiceModelNote, isWarning = true)
                }
            }

            SoftDivider()

            ToggleRow(
                label = s.speakAutomatically,
                description = s.speakAutomaticallyDesc,
                checked = settings.autoSpeakReplies,
                onChange = { v -> viewModel.updateSettings { it.copy(autoSpeakReplies = v) } },
            )

            Spacer(Modifier.height(Space.sm))

            SliderRow(
                label = s.speed,
                value = settings.speakingRate,
                range = 0.5f..2.0f,
                onChange = { v -> viewModel.updateSettings { it.copy(speakingRate = v) } },
            )

            // Pitch is a system-engine feature; model synthesis ignores it.
            if (settings.ttsEngine == TtsEngine.SYSTEM) {
                SliderRow(
                    label = s.pitch,
                    value = settings.pitch,
                    range = 0.5f..1.5f,
                    onChange = { v -> viewModel.updateSettings { it.copy(pitch = v) } },
                )
            }
        }

        SectionCard(
            icon = Icons.Filled.Mic,
            title = s.voiceInputTitle,
            subtitle = s.voiceInputSubtitle,
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            Caption(
                when {
                    !viewModel.speechAvailable -> s.noRecognizer
                    viewModel.speechOnDevice -> s.onDeviceRecognition
                    else -> s.cloudRecognition
                },
                isWarning = viewModel.speechAvailable && !viewModel.speechOnDevice,
            )
        }

        SectionCard(
            icon = Icons.Filled.Translate,
            title = s.voiceLanguage,
            subtitle = s.voiceLanguageSubtitle,
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            OutlinedTextField(
                value = settings.voiceLanguageTag,
                onValueChange = { v -> viewModel.updateSettings { it.copy(voiceLanguageTag = v) } },
                label = { Text(s.languageTag) },
                placeholder = { Text("ar-SA, en-US, …") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Arabic variants lead: they are what this app is used in most,
                // and speech recognition is markedly better with the right one.
                listOf(
                    "ar-SA", "ar-EG", "ar-AE", "ar-MA", "ar-IQ",
                    "en-US", "en-GB", "fr-FR", "tr-TR",
                ).forEach { tag ->
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
            title = s.systemPrompt,
            subtitle = s.systemPromptSubtitle,
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { v -> viewModel.updateSettings { it.copy(systemPrompt = v) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(s.systemPromptPlaceholder) },
                shape = MaterialTheme.shapes.small,
                minLines = 3,
            )
            Spacer(Modifier.height(Space.sm))
            Caption(s.systemPromptNote)
        }

        Spacer(Modifier.height(Space.xl))
    }
}

/** Auto / Always / Never, used for both reasoning and search. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(
    selected: RoutingMode,
    strings: AppStrings,
    onSelect: (RoutingMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RoutingMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = {
                    Text(
                        when (mode) {
                            RoutingMode.AUTO -> strings.modeAuto
                            RoutingMode.ALWAYS -> strings.modeAlways
                            RoutingMode.NEVER -> strings.modeNever
                        }
                    )
                },
            )
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
