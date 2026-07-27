package com.example.ondevicellm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.terminal.TerminalLineKind
import com.example.ondevicellm.ui.theme.Space

/**
 * A real terminal, and a log of what the model did in it.
 *
 * Forced left-to-right, even in Arabic. Shell output is not prose: a path, a
 * `ls -la` column, an error with a file name in it all become unreadable when
 * the paragraph direction flips, and mirroring them would be a translation of
 * the layout rather than of the language. The chrome around it still follows
 * the interface language.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val state by viewModel.terminalState.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the output as it arrives, the way a terminal does.
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = Space.lg)) {

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            if (state.lines.isEmpty()) {
                Column(Modifier.padding(Space.lg)) {
                    Caption(s.terminalEmpty)
                    Spacer(Modifier.height(Space.sm))
                    Caption(s.terminalWorkspaceNote)
                    Spacer(Modifier.height(Space.sm))
                    Caption(s.terminalLimitsNote, isWarning = true)
                }
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(Space.md),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(state.lines, key = { it.id }) { line ->
                            TerminalLineText(line.kind, line.text)
                        }
                    }
                }
            }
        }

        // Recent commands, so a long one does not have to be retyped on a
        // phone keyboard — which is the difference between a terminal you use
        // and one you look at.
        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.history.takeLast(4).reversed().forEach { previous ->
                    SuggestionChip(
                        onClick = { draft = previous },
                        label = {
                            Text(
                                previous,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("${state.prompt} ·  ${s.terminalHint}") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.sm))
            IconAction(
                icon = Icons.Filled.PlayArrow,
                description = s.terminalRun,
                enabled = draft.isNotBlank() && !state.isRunning,
                busy = state.isRunning,
            ) {
                viewModel.runTerminalCommand(draft)
                draft = ""
            }
            Spacer(Modifier.width(Space.xs))
            IconAction(
                icon = Icons.Filled.DeleteSweep,
                description = s.terminalClear,
                enabled = state.lines.isNotEmpty(),
            ) { viewModel.clearTerminal() }
        }

        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
private fun TerminalLineText(kind: TerminalLineKind, text: String) {
    val colors = MaterialTheme.colorScheme
    val color = when (kind) {
        TerminalLineKind.COMMAND -> colors.primary
        // The model's commands are tinted differently on purpose: on a page
        // that mixes both, "who ran this" is the first thing you need.
        TerminalLineKind.AGENT -> colors.tertiary
        TerminalLineKind.ERROR -> colors.error
        TerminalLineKind.NOTE -> colors.onSurfaceVariant
        TerminalLineKind.OUTPUT -> colors.onSurface
    }
    val weight = if (kind == TerminalLineKind.COMMAND || kind == TerminalLineKind.AGENT) {
        FontWeight.SemiBold
    } else {
        FontWeight.Normal
    }

    // Long lines scroll sideways rather than wrapping: a wrapped `ls -la` row
    // stops being a table, and wrapped output is how a terminal stops being one.
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = weight,
        style = MaterialTheme.typography.bodySmall,
        softWrap = false,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    )
}

@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
