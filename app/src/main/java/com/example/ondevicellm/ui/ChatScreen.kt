package com.example.ondevicellm.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.Author
import com.example.ondevicellm.ChatMessage
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.ModelStatus

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            StatusStrip(
                modelName = state.activeModel?.displayName.orEmpty(),
                backendLabel = backend.actualLabel,
                note = backend.note,
            )
        }

        state.importState?.let { import ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "${import.fileName}  ${(import.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.size(6.dp))
                ProgressBar(import.fraction)
            }
        }

        Box(Modifier.weight(1f)) {
            when (state.status) {
                ModelStatus.LOADING -> EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Loading model",
                    subtitle = state.activeModel?.displayName,
                    showSpinner = true,
                )

                ModelStatus.ERROR -> EmptyState(
                    icon = Icons.Filled.ErrorOutline,
                    title = "Couldn't load the model",
                    subtitle = state.errorMessage,
                    isError = true,
                    action = "Retry" to viewModel::retryLoad,
                )

                ModelStatus.NONE -> EmptyState(
                    icon = Icons.Filled.Download,
                    title = "No model yet",
                    subtitle = "Add a model file to start chatting — everything " +
                        "runs offline on your device.",
                    action = "Add a model" to onOpenModels,
                )

                ModelStatus.READY -> if (state.messages.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = "Ready",
                        subtitle = "Running fully on-device. Nothing leaves your phone.",
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(
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

        state.notice?.let { notice ->
            NoticeBar(text = notice, onDismiss = viewModel::dismissNotice)
        }

        MessageInput(
            viewModel = viewModel,
            enabled = state.status == ModelStatus.READY && !state.isBusy,
            isListening = state.isListening,
            voiceDraft = state.voiceDraft,
        )
    }
}

@Composable
private fun StatusStrip(modelName: String, backendLabel: String, note: String) {
    val hasNote = note.isNotEmpty()
    Surface(
        color = if (hasNote) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            if (hasNote) MaterialTheme.colorScheme.tertiary
                            else Color(0xFF3BA55D),
                            CircleShape,
                        )
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(8.dp))
                StatusPill(
                    text = backendLabel,
                    icon = Icons.Filled.Bolt,
                    color = if (hasNote) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (hasNote) {
                Spacer(Modifier.size(5.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showThinking: Boolean,
    isSpeaking: Boolean,
    isSynthesizing: Boolean,
    onToggleThinking: () -> Unit,
    onToggleSpeak: () -> Unit,
    onSaveAudio: () -> Unit,
) {
    val isUser = message.author == Author.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Column(modifier = Modifier.widthIn(max = 330.dp)) {

            if (!isUser && showThinking && message.thinking.isNotEmpty()) {
                ThinkingBlock(
                    text = message.thinking,
                    expanded = message.thinkingExpanded,
                    stillThinking = message.isGenerating && message.text.isEmpty(),
                    onToggle = onToggleThinking,
                )
                Spacer(Modifier.size(6.dp))
            }

            val showEmptyGenerating = !isUser && message.isGenerating && message.text.isEmpty()

            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                // Asymmetric corner points the bubble at its author.
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 20.dp,
                ),
            ) {
                if (showEmptyGenerating) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        TypingIndicator()
                    }
                } else {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            // Speech controls appear once a reply has settled.
            if (!isUser && !message.isGenerating && message.text.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeakButton(
                        isSpeaking = isSpeaking,
                        isSynthesizing = isSynthesizing,
                        onClick = onToggleSpeak,
                    )
                    IconButton(onClick = onSaveAudio, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "Save as WAV",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeakButton(isSpeaking: Boolean, isSynthesizing: Boolean, onClick: () -> Unit) {
    // Gentle pulse while audio is playing, so the active message is obvious.
    val scale by animateFloatAsState(
        targetValue = if (isSpeaking) 1.12f else 1f,
        label = "speakScale",
    )

    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        when {
            isSynthesizing -> CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
            )

            isSpeaking -> Icon(
                Icons.Filled.Stop,
                contentDescription = "Stop speaking",
                modifier = Modifier
                    .size(17.dp)
                    .scale(scale),
                tint = MaterialTheme.colorScheme.primary,
            )

            else -> Icon(
                Icons.Filled.VolumeUp,
                contentDescription = "Speak",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThinkingBlock(
    text: String,
    expanded: Boolean,
    stillThinking: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = if (stillThinking) "Thinking" else "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (stillThinking) {
                    Spacer(Modifier.size(7.dp))
                    TypingIndicator(dotColor = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = text,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun MessageInput(
    viewModel: ChatViewModel,
    enabled: Boolean,
    isListening: Boolean,
    voiceDraft: String,
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Voice results land in the field so they can be edited before sending.
    LaunchedEffect(voiceDraft) {
        if (voiceDraft.isNotEmpty()) text = voiceDraft
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                Spacer(Modifier.size(4.dp))
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (isListening) "Listening…" else "Message",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                enabled = enabled,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )

            Spacer(Modifier.size(6.dp))

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
private fun MicButton(isListening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = if (isListening) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (isListening) "Stop listening" else "Voice input",
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
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val background = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(background, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
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
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
            } else {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.size(18.dp))

        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )

        subtitle?.let {
            Spacer(Modifier.size(7.dp))
            Text(
                text = it,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        action?.let { (label, onClick) ->
            Spacer(Modifier.size(18.dp))
            FilledTonalButton(onClick = onClick) { Text(label) }
        }
    }
}
