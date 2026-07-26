package com.example.ondevicellm.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.Author
import com.example.ondevicellm.ChatMessage
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.ModelStatus
import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.core.ThermalLevel
import com.example.ondevicellm.llm.QueryKind
import com.example.ondevicellm.llm.RoutingDecision
import com.example.ondevicellm.web.SearchSource
import com.example.ondevicellm.ui.theme.Gradients
import com.example.ondevicellm.ui.theme.Layout
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.hairlineColor
import com.example.ondevicellm.ui.theme.panel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {

        state.backend?.let { backend ->
            ModelChip(
                modelName = state.activeModel?.displayName.orEmpty(),
                backendLabel = backend.actualLabel,
                note = backend.note,
                thermalLevel = state.thermalLevel,
            )
        }

        AnimatedVisibility(
            visible = state.importState != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.importState?.let { import ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            import.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            s.copyingPercent((import.fraction * 100).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                    GradientProgressBar(import.fraction, Gradients.accent)
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (state.status) {
                ModelStatus.LOADING -> EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = s.warmingUp,
                    subtitle = state.activeModel?.displayName?.let { s.loadingNamed(it) }
                        ?: s.loadingModel,
                    showSpinner = true,
                )

                ModelStatus.ERROR -> EmptyState(
                    icon = Icons.Filled.ErrorOutline,
                    title = s.couldNotLoadModel,
                    subtitle = state.errorMessage,
                    isError = true,
                    action = s.tryAgain to viewModel::retryLoad,
                )

                ModelStatus.NONE -> EmptyState(
                    icon = Icons.Filled.Download,
                    title = s.addFirstModel,
                    subtitle = s.privacyNote,
                    action = s.browseModels to onOpenModels,
                )

                ModelStatus.READY -> if (state.messages.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = s.readyWhenYouAre,
                        subtitle = s.runningOffline,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Space.lg,
                            end = Space.lg,
                            top = Space.md,
                            bottom = Space.lg,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageRow(
                                message = message,
                                showThinking = settings.showThinking,
                                isSpeaking = state.speakingMessageId == message.id,
                                isSynthesizing = state.speakingMessageId == message.id &&
                                    state.isSynthesizing,
                                onToggleThinking = { viewModel.toggleThinkingExpanded(message.id) },
                                onToggleSpeak = { viewModel.toggleSpeak(message.id) },
                                onSaveAudio = { viewModel.saveMessageAudio(message.id) },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.searchStatus != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.searchStatus?.let { status ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xl, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Space.sm))
                    TypingIndicator(dotColor = MaterialTheme.colorScheme.primary)
                }
            }
        }

        AnimatedVisibility(
            visible = state.notice != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.notice?.let { NoticeBar(it, viewModel::dismissNotice) }
        }

        MessageInput(
            viewModel = viewModel,
            enabled = state.status == ModelStatus.READY && !state.isBusy,
            isListening = state.isListening,
            voiceDraft = state.voiceDraft,
            searchEnabled = settings.webSearchEnabled,
            onToggleSearch = {
                viewModel.updateSettings { it.copy(webSearchEnabled = !it.webSearchEnabled) }
            },
        )
    }
}

/**
 * Floating pill showing which model is live and where it runs. A pill rather
 * than a full-width bar so it reads as ambient status, not a warning.
 */
@Composable
private fun ModelChip(
    modelName: String,
    backendLabel: String,
    note: String,
    thermalLevel: ThermalLevel,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val hasNote = note.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm)
    ) {
        Row(
            modifier = Modifier
                .panel(shape = RoundedCornerShape(50))
                .then(
                    if (hasNote) Modifier.clickable { expanded = !expanded } else Modifier
                )
                .padding(horizontal = Space.md, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveDot(
                color = if (hasNote) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    Color(0xFF22C55E)
                },
                animated = false,
                size = 7,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(Space.sm))
            StatusPill(backendLabel, icon = Icons.Filled.Bolt)

            // Only shown once the device is warm — silence means all is well.
            if (thermalLevel != ThermalLevel.NORMAL) {
                Spacer(Modifier.width(6.dp))
                StatusPill(
                    thermalLevel.label(s),
                    icon = Icons.Filled.Thermostat,
                    color = if (thermalLevel == ThermalLevel.CRITICAL) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFFE08A00)
                    },
                )
            }

            if (hasNote) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "noteChevron",
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = s.details,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded && hasNote) {
            Text(
                text = note,
                modifier = Modifier.padding(top = Space.sm, start = Space.md, end = Space.md),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    showThinking: Boolean,
    isSpeaking: Boolean,
    isSynthesizing: Boolean,
    onToggleThinking: () -> Unit,
    onToggleSpeak: () -> Unit,
    onSaveAudio: () -> Unit,
) {
    val s = LocalStrings.current
    val isUser = message.author == Author.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // A proportion, not 340dp: that constant was 83% of an S25 Ultra and
        // 96% of a small phone, so one screen looked roomy and the other
        // edge-to-edge.
        val bubbleMax = Layout.bubbleMaxWidth(LocalConfiguration.current.screenWidthDp).dp
        Column(Modifier.widthIn(max = bubbleMax)) {

            // Says why this reply was routed the way it was, so a missing search
            // or a skipped chain of thought is explained rather than mysterious.
            message.routing?.takeIf { !isUser }?.let { decision ->
                Text(
                    routingLine(decision, s),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs, bottom = Space.xs),
                )
            }

            if (!isUser && showThinking && message.thinking.isNotEmpty()) {
                ThinkingBlock(
                    text = message.thinking,
                    expanded = message.thinkingExpanded,
                    stillThinking = message.isGenerating && message.text.isEmpty(),
                    onToggle = onToggleThinking,
                )
                Spacer(Modifier.height(Space.sm))
            }

            Bubble(message = message, isUser = isUser)

            if (message.sources.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                SourceList(message.sources)
            }

            if (!isUser && !message.isGenerating && message.text.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = Space.xs, start = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionChip(
                        icon = when {
                            isSpeaking -> Icons.Filled.Stop
                            else -> Icons.Filled.VolumeUp
                        },
                        label = if (isSpeaking) s.stop else s.listen,
                        loading = isSynthesizing,
                        active = isSpeaking,
                        onClick = onToggleSpeak,
                    )
                    Spacer(Modifier.width(Space.sm))
                    ActionChip(
                        icon = Icons.Filled.Download,
                        label = s.save,
                        onClick = onSaveAudio,
                    )
                }
            }
        }
    }
}

/**
 * Turns a routing decision into a sentence, in the reader's language.
 *
 * The router deliberately returns the decision rather than prose, so this is
 * the only place the wording lives and Arabic gets a real sentence instead of
 * a translated fragment.
 */
private fun routingLine(decision: RoutingDecision, s: AppStrings): String {
    val base = when (decision.kind) {
        QueryKind.SOCIAL -> s.routeGreeting
        QueryKind.SIMPLE -> s.routeSimple
        QueryKind.LOOKUP -> s.routeLookup
        QueryKind.REASONING -> s.routeReasoning
    }
    val extras = buildList {
        if (decision.search) add(s.routeSearching)
        if (decision.think) add(s.routeThinking)
        if (isEmpty() && decision.overridden) add(s.routeOverridden)
    }
    return s.routeLine(base, extras)
}

/** Numbered, tappable citations matching the [1], [2] markers in the answer. */
@Composable
private fun SourceList(sources: List<SearchSource>) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        sources.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable { runCatching { uriHandler.openUri(source.url) } }
                    .padding(horizontal = Space.sm, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "[${source.index}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    source.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, isUser: Boolean) {
    // Asymmetric corner points each bubble toward its author.
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 7.dp,
        bottomEnd = if (isUser) 7.dp else 20.dp,
    )
    val generatingEmpty = !isUser && message.isGenerating && message.text.isEmpty()

    Box(
        modifier = if (isUser) {
            Modifier
                .clip(shape)
                .background(Gradients.accent)
        } else {
            Modifier.panel(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow)
        }
    ) {
        if (generatingEmpty) {
            Box(Modifier.padding(horizontal = Space.lg, vertical = Space.lg)) {
                TypingIndicator()
            }
        } else {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                style = MaterialTheme.typography.bodyLarge.copy(
                    // Direction from the text itself, not from the interface:
                    // an Arabic reply reads right-to-left even when the app is
                    // in English, and a code block or a Latin quotation inside
                    // an Arabic chat still reads left-to-right.
                    textDirection = TextDirection.Content,
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/** Small labelled button under a reply. Text + icon reads clearer than a bare icon. */
@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    active: Boolean = false,
) {
    val tint = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(50)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ThinkingBlock(
    text: String,
    expanded: Boolean,
    stillThinking: Boolean,
    onToggle: () -> Unit,
) {
    val s = LocalStrings.current
    val accent = MaterialTheme.colorScheme.tertiary
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "thinkChevron",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .panel(
                shape = MaterialTheme.shapes.small,
                color = accent.copy(alpha = 0.08f),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = Space.md, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = accent,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (stillThinking) s.thinking else s.reasoning,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            if (stillThinking) {
                Spacer(Modifier.width(7.dp))
                TypingIndicator(dotColor = accent)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) s.collapse else s.expand,
                modifier = Modifier.size(17.dp).rotate(rotation),
                tint = accent,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = text,
                modifier = Modifier.padding(top = Space.sm),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NoticeBar(text: String, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xs)
            .panel(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            )
            .padding(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(vertical = Space.sm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        TextButton(onClick = onDismiss) { Text(s.gotIt) }
    }
}

@Composable
private fun MessageInput(
    viewModel: ChatViewModel,
    enabled: Boolean,
    isListening: Boolean,
    voiceDraft: String,
    searchEnabled: Boolean,
    onToggleSearch: () -> Unit,
) {
    val s = LocalStrings.current
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Voice results land in the field so they can be edited before sending.
    LaunchedEffect(voiceDraft) {
        if (voiceDraft.isNotEmpty()) text = voiceDraft
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Space.md, vertical = Space.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .panel(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                )
                .padding(Space.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Web grounding is a per-question decision, so it belongs next to
            // the question rather than buried in settings.
            SearchToggle(enabled = searchEnabled, onClick = onToggleSearch)

            if (viewModel.speechAvailable) {
                MicButton(
                    isListening = isListening,
                    enabled = enabled || isListening,
                    onClick = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) viewModel.startListening()
                            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }

            // Borderless field: the surrounding panel already is the input.
            BareTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                placeholder = if (isListening) s.listening else s.askAnything,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.sm, vertical = 10.dp),
            )

            SendButton(
                enabled = enabled && text.isNotBlank(),
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                        text = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun BareTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = Gradients.accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SearchToggle(enabled: Boolean, onClick: () -> Unit) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .size(Layout.TOUCH_TARGET_DP.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Language,
            contentDescription = if (enabled) s.webSearchOn else s.webSearchOff,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun MicButton(isListening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val s = LocalStrings.current
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.08f else 1f,
        animationSpec = spring(),
        label = "micScale",
    )
    Box(
        modifier = Modifier
            .size(Layout.TOUCH_TARGET_DP.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isListening) MaterialTheme.colorScheme.errorContainer else Color.Transparent
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isListening) s.stopListening else s.voiceInput,
                modifier = Modifier.size(20.dp),
                tint = if (isListening) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
internal fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val s = LocalStrings.current
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = spring(),
        label = "sendScale",
    )
    Box(
        modifier = Modifier
            .size(Layout.TOUCH_TARGET_DP.dp)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (enabled) Modifier.background(Gradients.accent)
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = s.send,
            modifier = Modifier.size(19.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    showSpinner: Boolean = false,
    isError: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
) {
    val accent = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Gradients.halo(accent)),
            contentAlignment = Alignment.Center,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = accent,
                )
            } else {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(Space.xl))

        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        subtitle?.let {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = it,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        action?.let { (label, onClick) ->
            Spacer(Modifier.height(Space.xl))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Gradients.accent)
                    .clickable(onClick = onClick)
                    .padding(horizontal = Space.xxl, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
