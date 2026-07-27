package com.example.ondevicellm.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.pdf.PdfDoc
import com.example.ondevicellm.ui.theme.Space

/**
 * The documents this conversation can read.
 *
 * Each one says how it was read — text layer, or read off the page as an image
 * — because that decides how much to trust an answer from it. OCR of a poor
 * scan is approximate, and a user who does not know a page went through it has
 * no way to weigh what comes back.
 */
@Composable
fun DocumentsDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val progress by viewModel.documentProgress.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importDocument) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(s.documentsTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    s.documentsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                progress?.let { active ->
                    Text(
                        "${active.name} · ${active.stage.orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Space.xs))
                    // Determinate: "page 12 of 90" is the difference between
                    // waiting and wondering whether it has hung.
                    LinearProgressIndicator(
                        progress = { active.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Space.md))
                }

                if (documents.isEmpty()) {
                    Text(
                        s.documentsEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Space.sm))
                    Caption(s.documentsNote)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            DocumentRow(
                                doc = doc,
                                isAttached = doc.id == state.attachedDocumentId,
                                onToggle = {
                                    viewModel.attachDocument(
                                        if (doc.id == state.attachedDocumentId) null else doc.id
                                    )
                                },
                                onRemove = { viewModel.removeDocument(doc.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                enabled = progress == null,
            ) { Text(s.attachDocument) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.close) } },
    )
}

@Composable
private fun DocumentRow(
    doc: PdfDoc,
    isAttached: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isAttached) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .clickable(onClick = onToggle)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isAttached) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text(
                doc.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(s.documentPages(doc.pageCount))
                    // How it was read, because it decides how far to trust it.
                    if (doc.ocrPageCount > 0) {
                        append(" · ")
                        append(s.documentOcrPages(doc.ocrPageCount))
                    }
                    if (doc.imageCount > 0) {
                        append(" · ")
                        append(s.documentImages(doc.imageCount))
                    }
                    if (doc.unreadablePageCount > 0) {
                        append(" · ")
                        append(s.documentUnreadablePages(doc.unreadablePageCount))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            doc.problem?.let { problem ->
                Text(
                    problem,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = s.removeDocument,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
