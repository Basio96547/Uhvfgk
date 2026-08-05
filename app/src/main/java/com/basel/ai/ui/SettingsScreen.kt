package com.basel.ai.ui

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basel.ai.ChatViewModel
import com.basel.ai.audio.CloudTts
import com.basel.ai.audio.CloudTtsProvider
import com.basel.ai.audio.VoicePicker
import com.basel.ai.core.AppLanguage
import com.basel.ai.core.AppSettings
import com.basel.ai.core.AppStrings
import com.basel.ai.core.TtsEngine
import com.basel.ai.llm.RoutingMode
import com.basel.ai.web.SearchDepth
import com.basel.ai.ui.theme.Space
import com.basel.ai.ui.theme.panel

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

        // Right under the language card: what the app decided for the last
        // message, in its own words. An automatic system nobody can interrogate
        // is just an opaque one, and every other part of this app is built on
        // being able to find out why.
        SectionCard(
            icon = Icons.Filled.AutoAwesome,
            title = s.autoTitle,
            subtitle = s.autoSubtitle,
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            val notes by viewModel.autoNotes.collectAsStateWithLifecycle()
            if (notes.isEmpty()) {
                Caption(s.autoNothingToShow)
            } else {
                notes.forEach { note ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Space.xs))
                }
            }
            Spacer(Modifier.height(Space.sm))
            Caption(s.autoExplainNote)
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
                    // Automatic first, because it is the default and the one
                    // most people should leave alone.
                    FilterChip(
                        selected = settings.searchDepth == null,
                        onClick = { viewModel.updateSettings { it.copy(searchDepth = null) } },
                        label = { Text(s.autoLabel) },
                    )
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
                        null -> s.autoExplainNote
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
            icon = Icons.Filled.Description,
            title = s.ocrTitle,
            subtitle = s.ocrSubtitle,
            tint = MaterialTheme.colorScheme.secondary,
        ) {
            ToggleRow(
                label = s.ocrEnable,
                description = s.ocrEnableDesc,
                checked = settings.ocrEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(ocrEnabled = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            // The gap is stated where the switch is, not in a document nobody
            // opens: there is no offline option, and why.
            Caption(s.ocrOfflineGap, isWarning = true)

            if (settings.ocrEnabled) {
                SoftDivider()
                Caption(s.ocrPrivacy, isWarning = true)
                Spacer(Modifier.height(Space.sm))

                var visible by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = settings.ocrApiKey,
                    onValueChange = { v ->
                        viewModel.updateSettings { it.copy(ocrApiKey = v.trim()) }
                    },
                    label = { Text(s.ocrApiKeyLabel) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(if (visible) s.cloudHideKey else s.cloudShowKey)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
                Caption(s.cloudKeyStorageNote)

                if (settings.ocrApiKey.isBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Caption(s.ocrNeedsKey, isWarning = true)
                }
            }
        }

        SectionCard(
            icon = Icons.Filled.Terminal,
            title = s.toolsTitle,
            subtitle = s.toolsSettingsSubtitle,
            tint = MaterialTheme.colorScheme.tertiary,
        ) {
            ToggleRow(
                label = s.toolsEnable,
                description = s.toolsEnableDesc,
                checked = settings.toolsEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(toolsEnabled = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(s.toolsCostNote)

            if (settings.toolsEnabled) {
                SoftDivider()
                ToggleRow(
                    label = s.toolsAllowShell,
                    description = s.toolsAllowShellDesc,
                    checked = settings.toolShellEnabled,
                    onChange = { v ->
                        viewModel.updateSettings { it.copy(toolShellEnabled = v) }
                    },
                )
                Spacer(Modifier.height(Space.sm))
                ToggleRow(
                    label = s.toolsAllowWrites,
                    description = s.toolsAllowWritesDesc,
                    checked = settings.toolAllowWrites,
                    onChange = { v ->
                        viewModel.updateSettings { it.copy(toolAllowWrites = v) }
                    },
                )
                Spacer(Modifier.height(Space.sm))
                ToggleRow(
                    label = s.toolsAllowDangerous,
                    description = s.toolsAllowDangerousDesc,
                    checked = settings.toolAllowDangerous,
                    onChange = { v ->
                        viewModel.updateSettings { it.copy(toolAllowDangerous = v) }
                    },
                )

                SoftDivider()
                GroupLabel(s.toolsSteps)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = settings.toolMaxSteps == null,
                        onClick = { viewModel.updateSettings { it.copy(toolMaxSteps = null) } },
                        label = { Text(s.autoLabel) },
                    )
                    // Each step is a whole generation, so this is the setting
                    // that decides whether an answer takes seconds or minutes.
                    listOf(1, 2, 3, 4, 5).forEach { steps ->
                        FilterChip(
                            selected = settings.toolMaxSteps == steps,
                            onClick = {
                                viewModel.updateSettings { it.copy(toolMaxSteps = steps) }
                            },
                            label = { Text("$steps") },
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Caption(s.toolsStepsNote)

                SoftDivider()
                Caption(s.terminalLimitsNote, isWarning = true)
            }
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

            if (settings.ttsEngine == TtsEngine.SYSTEM) {
                SoftDivider()
                GroupLabel(s.speechEngineLabel)

                // Listing voices reads a live TextToSpeech instance. Nothing
                // has necessarily started one yet — and after the user picks a
                // different engine the old one is still what would be read —
                // so start it here and re-read when it reports in.
                val generation by viewModel.ttsGeneration.collectAsStateWithLifecycle()
                LaunchedEffect(settings.systemVoiceEngine) { viewModel.prepareSystemTts() }

                val engines = remember(generation) { viewModel.systemVoiceEngines() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = settings.systemVoiceEngine == null,
                        onClick = {
                            viewModel.updateSettings {
                                // The voice belongs to the old engine.
                                it.copy(systemVoiceEngine = null, systemVoiceName = null)
                            }
                        },
                        label = { Text(s.speechEngineDefault) },
                    )
                    engines.forEach { engine ->
                        FilterChip(
                            selected = settings.systemVoiceEngine == engine.packageName,
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        systemVoiceEngine = engine.packageName,
                                        systemVoiceName = null,
                                    )
                                }
                            },
                            label = { Text(engine.label) },
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Caption(s.speechEngineNote)

                SoftDivider()
                GroupLabel(s.voiceLabel)

                // Read once per composition of this card: enumerating voices
                // touches the engine, and it cannot change while the screen is
                // open anyway.
                val voices = remember(settings.voiceLanguageTag, generation) {
                    VoicePicker.candidatesFor(viewModel.systemVoices(), settings.voiceLanguageTag)
                }

                if (voices.isEmpty()) {
                    Caption(s.voiceNoneInstalled, isWarning = true)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = settings.systemVoiceName == null,
                            onClick = {
                                viewModel.updateSettings { it.copy(systemVoiceName = null) }
                            },
                            label = { Text(s.voiceAutoBest) },
                        )
                        voices.forEach { voice ->
                            FilterChip(
                                selected = settings.systemVoiceName == voice.name,
                                onClick = {
                                    viewModel.updateSettings {
                                        it.copy(systemVoiceName = voice.name)
                                    }
                                },
                                label = {
                                    Text(
                                        buildString {
                                            append(voice.languageTag)
                                            append(" · ")
                                            append(s.voiceQuality(voice.quality))
                                            if (voice.needsNetwork) {
                                                append(" · ")
                                                append(s.voiceNeedsNetwork)
                                            }
                                        }
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    Caption(s.voiceNote)
                }
                SoftDivider()
            }

            if (settings.ttsEngine == TtsEngine.CLOUD) {
                CloudVoiceSettings(viewModel, settings, s)
                SoftDivider()
            }

            when (settings.ttsEngine) {
                TtsEngine.SYSTEM -> Caption(s.systemEngineNote)

                TtsEngine.MODEL -> when {
                    !viewModel.ttsRuntimeAvailable -> Caption(s.litertMissingNote, isWarning = true)

                    ttsModel != null -> Caption(
                        s.usingVoiceModel(ttsModel.displayName, ttsModel.ttsSampleRateHz)
                    )

                    else -> Caption(s.noVoiceModelNote, isWarning = true)
                }

                // Everything the cloud voice has to say is above, next to the
                // fields it is about.
                TtsEngine.CLOUD -> Unit
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
            icon = Icons.Filled.Rule,
            title = s.guidanceTitle,
            subtitle = s.guidanceDescription,
            tint = MaterialTheme.colorScheme.primary,
        ) {
            ToggleRow(
                label = s.guidanceTitle,
                description = s.guidanceDescription,
                checked = settings.guidanceEnabled,
                onChange = { v -> viewModel.updateSettings { it.copy(guidanceEnabled = v) } },
            )
            Spacer(Modifier.height(Space.sm))
            Caption(s.guidanceNote)

            // Shown in full rather than described: what goes into the model on
            // your behalf shouldn't be something you have to take on trust.
            if (settings.guidanceEnabled) {
                SoftDivider()
                Text(
                    s.assistantGuidance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .panel(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                .copy(alpha = 0.5f),
                        )
                        .padding(Space.md),
                )
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

/**
 * The hosted-voice fields.
 *
 * Split out because this is the one section that sends anything off the
 * device, and it should read as its own decision rather than as another row of
 * chips: what it costs in privacy is stated first, above the fields, not in a
 * footnote under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudVoiceSettings(
    viewModel: ChatViewModel,
    settings: AppSettings,
    s: AppStrings,
) {
    SoftDivider()
    GroupLabel(s.cloudTitle)
    Caption(s.cloudPrivacy, isWarning = true)
    Spacer(Modifier.height(Space.sm))

    GroupLabel(s.cloudProvider)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CloudTtsProvider.entries.forEach { provider ->
            FilterChip(
                selected = settings.cloudProvider == provider,
                onClick = {
                    viewModel.updateSettings {
                        // The voice field means different things per provider —
                        // a documented name for Azure, an account id for
                        // ElevenLabs — so carrying one across is nonsense.
                        it.copy(
                            cloudProvider = provider,
                            cloudVoice = if (provider == CloudTtsProvider.AZURE) {
                                CloudTts.DEFAULT_AZURE_VOICE
                            } else {
                                ""
                            },
                        )
                    }
                },
                label = { Text(provider.label(s)) },
            )
        }
    }

    Spacer(Modifier.height(Space.sm))

    var keyVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = settings.cloudApiKey,
        onValueChange = { v -> viewModel.updateSettings { it.copy(cloudApiKey = v.trim()) } },
        label = { Text(s.cloudApiKey) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        // Hidden by default because it is a secret on a screen anyone nearby
        // can read, revealable because a key you cannot see is a key you
        // cannot check for a bad paste.
        visualTransformation = if (keyVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { keyVisible = !keyVisible }) {
                Text(if (keyVisible) s.cloudHideKey else s.cloudShowKey)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Space.xs))
    Caption(s.cloudKeyStorageNote)

    Spacer(Modifier.height(Space.sm))

    when (settings.cloudProvider) {
        CloudTtsProvider.AZURE -> {
            OutlinedTextField(
                value = settings.cloudRegion,
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(cloudRegion = v.trim()) }
                },
                label = { Text(s.cloudRegion) },
                placeholder = { Text("westeurope, eastus, …") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.sm))
            GroupLabel(s.cloudVoice)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CloudTts.AZURE_ARABIC_VOICES.forEach { voice ->
                    FilterChip(
                        selected = settings.cloudVoice == voice,
                        onClick = { viewModel.updateSettings { it.copy(cloudVoice = voice) } },
                        // The full name is noise; the dialect and the given
                        // name are what anyone actually chooses between.
                        label = { Text(azureVoiceLabel(voice)) },
                    )
                }
            }
        }

        CloudTtsProvider.ELEVENLABS -> {
            OutlinedTextField(
                value = settings.cloudVoice,
                onValueChange = { v ->
                    viewModel.updateSettings { it.copy(cloudVoice = v.trim()) }
                },
                label = { Text(s.cloudVoice) },
                placeholder = { Text(s.cloudVoiceIdHint) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val missing = CloudTts.missingSetting(
        provider = settings.cloudProvider,
        apiKey = settings.cloudApiKey,
        region = settings.cloudRegion,
        voice = settings.cloudVoice,
        s = s,
    )
    if (missing != null) {
        Spacer(Modifier.height(Space.xs))
        Caption(missing, isWarning = true)
    }
}

/** `ar-SA-HamedNeural` → `ar-SA · Hamed`. */
private fun azureVoiceLabel(voice: String): String {
    val parts = voice.split('-')
    if (parts.size < 3) return voice
    val given = parts[2].removeSuffix("Neural")
    return "${parts[0]}-${parts[1]} · $given"
}
