package com.basel.ai.ui

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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basel.ai.Author
import com.basel.ai.ChatMessage
import com.basel.ai.ChatViewModel
import com.basel.ai.ModelStatus
import com.basel.ai.agent.ToolRun
import com.basel.ai.chat.Block
import com.basel.ai.chat.Markdown
import com.basel.ai.chat.Span
import com.basel.ai.core.AppStrings
import com.basel.ai.core.ThermalLevel
import com.basel.ai.llm.QueryKind
import com.basel.ai.llm.RoutingDecision
import com.basel.ai.ui.theme.Gradients
import com.basel.ai.ui.theme.Layout
import com.basel.ai.ui.theme.Space
import com.basel.ai.ui.theme.panel
import com.basel.ai.web.SearchSource

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val documentProgress by viewModel.documentProgress.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showDocuments by remember { mutableStateOf(false) }

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

        // Only when there is one. An empty document bar on every conversation
        // is a permanent reminder of a feature most messages don't use.
        AnimatedVisibility(
            visible = documentProgress != null || state.attachedDocumentId != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DocumentBar(
                name = documentProgress?.name
                    ?: documents.firstOrNull { it.id == state.attachedDocumentId }?.name.orEmpty(),
                stage = documentProgress?.stage,
                fraction = documentProgress?.fraction,
                onOpen = { showDocuments = true },
                onDetach = { viewModel.attachDocument(null) },
            )
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
            onOpenDocuments = { showDocuments = true },
        )
    }

    if (showDocuments) {
        DocumentsDialog(viewModel = viewModel, onDismiss = { showDocuments = false })
    }
}

/**
 * The document in use, above the composer.
 *
 * It sits here rather than in a menu because it changes what every answer is
 * based on: a user who has forgotten a PDF is attached will read a grounded
 * answer as the model's own knowledge.
 */
@Composable
private fun DocumentBar(
    name: String,
    stage: String?,
    fraction: Float?,
    onOpen: () -> Unit,
    onDetach: () -> Unit,
) {
    val s = LocalStrings.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .panel(shape = RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (stage != null) {
                Text(
                    stage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDetach),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = s.documentDetach,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (fraction != null) {
            Spacer(Modifier.height(Space.xs))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
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

            // Above the answer, not below it: these happened first, and the
            // reply is written from what they returned.
            if (message.toolRuns.isNotEmpty()) {
                ToolRunList(message.toolRuns)
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
/**
 * What the model did to answer, in the conversation.
 *
 * There is no terminal page, deliberately — a command run on this phone is
 * part of the reply it produced, and a log on a screen nobody opens is not
 * transparency. Collapsed to one line each, because most of the time knowing
 * *that* it ran `ls` is enough; tap to see what came back.
 */
/**
 * A reply, laid out.
 *
 * The bubble printed the model's Markdown verbatim: `**مهم**` came out with
 * the asterisks, `- بند` with the dash, a code block as a wall of text. Long
 * replies looked broken, and emphasis — whose entire job is to say which part
 * matters — was doing the opposite.
 *
 * Direction comes from the text rather than the interface throughout, so an
 * Arabic reply reads right-to-left with the app in English; code goes the
 * other way regardless, because a mirrored command is unreadable.
 */
@Composable
private fun MarkdownBody(
    text: String,
    color: Color,
    asMarkdown: Boolean,
    modifier: Modifier = Modifier,
) {
    val body = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content)

    if (!asMarkdown) {
        Text(text = text, modifier = modifier, style = body, color = color)
        return
    }

    val blocks = remember(text) { Markdown.parse(text) }
    if (blocks.isEmpty()) {
        Text(text = text, modifier = modifier, style = body, color = color)
        return
    }

    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(Space.sm))
            when (block) {
                is Block.Paragraph ->
                    Text(annotate(block.spans), style = body, color = color)

                is Block.Heading -> Text(
                    annotate(block.spans),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }.copy(textDirection = TextDirection.Content),
                    color = color,
                )

                is Block.Bullet -> ListLine("•", annotate(block.spans), body, color)
                is Block.Numbered ->
                    ListLine("${block.number}.", annotate(block.spans), body, color)

                is Block.Code -> CodeBlock(block.code, color)

                Block.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.xs)
                        .height(1.dp)
                        .background(color.copy(alpha = 0.20f))
                )
            }
        }
    }
}

/** A marker and its text, with the marker on the reading side. */
@Composable
private fun ListLine(
    marker: String,
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
) {
    Row(Modifier.padding(start = Space.xs)) {
        Text(
            marker,
            style = style,
            color = color.copy(alpha = 0.62f),
            // Fixed width so wrapped lines align under the text, not the dot.
            modifier = Modifier.width(22.dp),
        )
        Text(text, style = style, color = color, modifier = Modifier.weight(1f))
    }
}

/**
 * A fenced block: monospaced, left-to-right, and scrolled rather than wrapped.
 *
 * Wrapping code is how a snippet stops being copy-pasteable — the line breaks
 * become part of it — so it scrolls sideways instead.
 */
@Composable
private fun CodeBlock(code: String, color: Color) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(color.copy(alpha = 0.07f))
                .padding(Space.md)
        ) {
            Text(
                code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color,
                softWrap = false,
            )
        }
    }
}

/** Spans to an AnnotatedString. */
@Composable
private fun annotate(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    for (span in spans) {
        val style = when {
            span.code -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = codeBackground,
            )
            span.bold && span.italic ->
                SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
            span.bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
            span.italic -> SpanStyle(fontStyle = FontStyle.Italic)
            else -> null
        }
        if (style == null) append(span.text) else withStyle(style) { append(span.text) }
    }
}

@Composable
private fun ToolRunList(runs: List<ToolRun>) {
    Column(Modifier.fillMaxWidth()) {
        runs.forEach { run ->
            var open by remember(run) { mutableStateOf(false) }
            val accent = if (run.ok) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.error
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.xs)
                    .panel(
                        shape = MaterialTheme.shapes.small,
                        color = accent.copy(alpha = 0.07f),
                    )
                    .clickable { open = !open }
                    .padding(horizontal = Space.md, vertical = Space.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (run.ok) Icons.Filled.Bolt else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = accent,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        run.tool,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    if (run.detail.isNotBlank()) {
                        Spacer(Modifier.width(Space.sm))
                        // Monospace and left-to-right: a command is not prose,
                        // and mirroring `ls -la` makes it unreadable.
                        CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Ltr
                        ) {
                            Text(
                                run.detail,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                AnimatedVisibility(visible = open) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr
                    ) {
                        Text(
                            run.output.ifBlank { "—" },
                            modifier = Modifier
                                .padding(top = Space.sm)
                                .horizontalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

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
            MarkdownBody(
                text = message.text,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                // What the user typed is what they typed. Styling their own
                // asterisks would be the app editing them.
                asMarkdown = !isUser,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
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
    onOpenDocuments: () -> Unit,
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
        // Field above, actions below.
        //
        // They shared one row until the fourth button went in, and the
        // arithmetic is unforgiving: four 48dp targets and their padding leave
        // 171dp of a 411dp screen to type in — about twenty Arabic characters
        // before the text starts scrolling out of sight while you write it.
        // Stacked, the field gets the whole 363dp and the buttons keep their
        // full touch targets. See Layout.composerFieldWidth.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .panel(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                )
                .padding(Space.xs),
        ) {
            // Borderless field: the surrounding panel already is the input.
            BareTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                placeholder = if (isListening) s.listening else s.askAnything,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm, vertical = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Web grounding is a per-question decision, so it belongs next
                // to the question rather than buried in settings.
                SearchToggle(enabled = searchEnabled, onClick = onToggleSearch)

                // Where everyone looks for it. Opens the list rather than the
                // file picker directly, because "which PDF am I asking about"
                // is the question more often than "add another one".
                AttachButton(onClick = onOpenDocuments)

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

                Spacer(Modifier.weight(1f))

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
private fun AttachButton(onClick: () -> Unit) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier
            .size(Layout.TOUCH_TARGET_DP.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.AttachFile,
            contentDescription = s.documentsTitle,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
