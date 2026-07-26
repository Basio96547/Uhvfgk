package com.example.ondevicellm.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
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
            BackendBanner(
                label = state.activeModel?.displayName.orEmpty(),
                backendLabel = backend.actualLabel,
                note = backend.note,
            )
        }

        state.importState?.let { import ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(import.fileName, style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    progress = { import.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (state.status) {
                ModelStatus.LOADING -> CenteredStatus(
                    title = "Loading model…",
                    subtitle = state.activeModel?.displayName,
                    showSpinner = true,
                )

                ModelStatus.ERROR -> CenteredStatus(
                    title = state.errorMessage ?: "Something went wrong.",
                    isError = true,
                    action = "Retry" to viewModel::retryLoad,
                )

                ModelStatus.NONE -> CenteredStatus(
                    title = "No model loaded",
                    subtitle = "Add a .task model to start chatting offline.",
                    action = "Add model" to onOpenModels,
                )

                ModelStatus.READY -> if (state.messages.isEmpty()) {
                    CenteredStatus(
                        title = "Ready",
                        subtitle = "Running fully on-device. Ask anything.",
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                showThinking = settings.showThinking,
                                onToggleThinking = { viewModel.toggleThinkingExpanded(message.id) },
                            )
                        }
                    }
                }
            }
        }

        state.notice?.let { notice ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        notice,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = viewModel::dismissNotice) { Text("OK") }
                }
            }
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
private fun BackendBanner(label: String, backendLabel: String, note: String) {
    Surface(
        color = if (note.isEmpty()) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                text = "$label · running on $backendLabel",
                style = MaterialTheme.typography.labelMedium,
            )
            if (note.isNotEmpty()) {
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
    onToggleThinking: () -> Unit,
) {
    val isUser = message.author == Author.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(modifier = Modifier.widthIn(max = 340.dp)) {

            if (!isUser && showThinking && message.thinking.isNotEmpty()) {
                ThinkingBlock(
                    text = message.thinking,
                    expanded = message.thinkingExpanded,
                    stillThinking = message.isGenerating && message.text.isEmpty(),
                    onToggle = onToggleThinking,
                )
                Spacer(Modifier.size(4.dp))
            }

            val hasBody = message.text.isNotEmpty() || isUser || !message.isGenerating
            if (hasBody) {
                Surface(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = message.text.ifEmpty { "…" },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = if (stillThinking) "Thinking…" else "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = text,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

    // Voice results flow into the text field so they can be edited before sending.
    LaunchedEffect(voiceDraft) {
        if (voiceDraft.isNotEmpty()) text = voiceDraft
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startListening()
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.speechAvailable) {
            IconButton(
                onClick = {
                    if (isListening) {
                        viewModel.stopListening()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.startListening()
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                enabled = enabled || isListening,
            ) {
                Icon(
                    if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Voice input",
                    tint = if (isListening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (isListening) "Listening…" else "Ask something…") },
            enabled = enabled,
            maxLines = 5,
        )

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    viewModel.sendMessage(text)
                    text = ""
                }
            },
            enabled = enabled && text.isNotBlank(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

@Composable
private fun CenteredStatus(
    title: String,
    subtitle: String? = null,
    showSpinner: Boolean = false,
    isError: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator()
            Spacer(Modifier.size(16.dp))
        }
        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        subtitle?.let {
            Text(
                text = it,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) {
                Text(label)
            }
        }
    }
}
