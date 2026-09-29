package com.basel.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basel.ai.ChatViewModel
import com.basel.ai.chat.Conversation
import com.basel.ai.chat.ConversationIndex
import com.basel.ai.ui.theme.Space

/**
 * Everything that has been said, kept.
 *
 * The list is the feature: until this existed, closing the app threw the whole
 * history away. So it leads with search rather than a menu — a saved
 * conversation you cannot find again is barely saved.
 */
@Composable
fun ConversationsDialog(
    viewModel: ChatViewModel,
    onOpened: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val all by viewModel.conversations.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val shown = remember(all, query) { ConversationIndex.search(all, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(s.conversationsTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    s.conversationsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                // Only once there is enough to need it; a search box above two
                // conversations is furniture.
                if (all.size > SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(s.conversationsSearch) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.md))
                }

                if (shown.isEmpty()) {
                    Caption(if (all.isEmpty()) s.conversationsEmpty else s.searchNoResults)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        items(shown, key = { it.id }) { conversation ->
                            ConversationRow(
                                conversation = conversation,
                                onOpen = {
                                    viewModel.openConversation(conversation.id)
                                    onOpened()
                                },
                                onDelete = { viewModel.deleteConversation(conversation.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.newConversation()
                    onOpened()
                }
            ) { Text(s.conversationNew) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}

private const val SEARCH_THRESHOLD = 5

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                conversation.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The last thing said, which is how anyone recognises a chat they
            // half remember.
            Text(
                conversation.preview(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.sm))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = s.conversationDelete,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
