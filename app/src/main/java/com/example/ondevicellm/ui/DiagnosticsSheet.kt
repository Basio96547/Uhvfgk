package com.example.ondevicellm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.core.AppError
import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.core.Severity
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.panel

@Composable
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    Severity.WARNING -> Color(0xFFE08A00)
    Severity.ERROR -> MaterialTheme.colorScheme.error
    Severity.CRASH -> MaterialTheme.colorScheme.error
}

/**
 * Readable view of everything that went wrong.
 *
 * Failures used to be swallowed, so the app could quietly do nothing and look
 * broken for no reason. Each entry keeps the subsystem, a plain-language
 * summary and the trace, and the whole log can be copied out in one tap for a
 * bug report.
 */
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val entries by ErrorLog.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    // Lower-severity entries are kept in memory; make sure they survive from here.
    LaunchedEffect(Unit) { ErrorLog.flush() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (entries.isEmpty()) {
                        "Nothing has gone wrong."
                    } else {
                        "${entries.size} recorded ${if (entries.size == 1) "event" else "events"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            if (entries.isEmpty()) {
                EmptyDiagnostics()
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(entries) { entry -> DiagnosticRow(entry) }
                }
            }
        },
        confirmButton = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = { copyToClipboard(context, ErrorLog.exportText()) }) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy all")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
            }
        },
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear diagnostics?") },
            text = { Text("The recorded events will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    ErrorLog.clear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyDiagnostics() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(Space.md))
        Text(
            "No problems recorded. Anything that fails — a model that won't " +
                "load, a search that times out, a crash — will show up here.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticRow(entry: AppError) {
    var expanded by remember { mutableStateOf(false) }
    val accent = severityColor(entry.severity)

    Column(
        Modifier
            .fillMaxWidth()
            .panel(shape = MaterialTheme.shapes.small)
            .clickable(enabled = entry.detail.isNotBlank()) { expanded = !expanded }
            .padding(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(Space.sm))
            Text(
                entry.area,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Text(
                entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(5.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall)

        if (entry.detail.isNotBlank()) {
            if (expanded) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Diagnostics", text))
}
