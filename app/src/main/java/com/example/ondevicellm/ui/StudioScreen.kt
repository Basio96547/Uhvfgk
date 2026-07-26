package com.example.ondevicellm.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ondevicellm.ChatViewModel
import com.example.ondevicellm.ModelStatus
import com.example.ondevicellm.studio.StudioProject
import com.example.ondevicellm.ui.theme.Layout
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.hairlineColor
import com.example.ondevicellm.ui.theme.panel

/**
 * Describe a page, watch the model write it, see it run.
 *
 * Scoped to a **single self-contained HTML file** on purpose. That is the only
 * kind of program a phone can build and execute with nothing installed: no
 * toolchain, no package manager, no server. A WebView is a complete runtime
 * that is already on the device.
 */
@Composable
fun StudioScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val state by viewModel.studioState.collectAsStateWithLifecycle()
    val projects by viewModel.studioProjects.collectAsStateWithLifecycle()
    val chat by viewModel.uiState.collectAsStateWithLifecycle()

    val ready = chat.status == ModelStatus.READY

    Column(modifier = modifier.fillMaxSize()) {

        StudioToolbar(
            name = state.name.ifBlank { s.studioUntitled },
            showCode = state.showCode,
            canSave = state.code.isNotBlank(),
            onToggleView = viewModel::studioToggleView,
            onRun = viewModel::studioRun,
            onSave = viewModel::studioSave,
            onNew = viewModel::studioNew,
        )

        Box(Modifier.weight(1f)) {
            when {
                state.isBuilding -> BuildingView(state.streaming)
                state.code.isBlank() -> StudioEmpty(projects, viewModel)
                state.showCode -> CodeEditor(state.code, viewModel::studioEditCode)
                else -> PagePreview(state.code, state.previewRevision)
            }
        }

        AnimatedVisibility(
            visible = state.notice != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.notice?.let { NoticeBar(it, viewModel::studioDismissNotice) }
        }

        BuildBar(
            enabled = ready && !chat.isBusy,
            onBuild = viewModel::studioBuild,
        )
    }
}

@Composable
private fun StudioToolbar(
    name: String,
    showCode: Boolean,
    canSave: Boolean,
    onToggleView: () -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onNew: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ToolbarButton(
            if (showCode) Icons.Filled.Visibility else Icons.Filled.Code,
            if (showCode) s.studioPreview else s.studioCode,
            onToggleView,
        )
        ToolbarButton(Icons.Filled.PlayArrow, s.studioRun, onRun)
        ToolbarButton(Icons.Filled.Save, s.studioSave, onSave, enabled = canSave)
        ToolbarButton(Icons.Filled.Add, s.studioNewProject, onNew)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(hairlineColor))
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(Layout.TOUCH_TARGET_DP.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(21.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
        )
    }
}

/**
 * Runs the generated page.
 *
 * Sandboxed deliberately: `loadDataWithBaseURL(null, …)` gives the document an
 * opaque origin, network loads are blocked outright, and file and content
 * access are off. A page the model wrote is untrusted input — it should not be
 * able to reach the network, the filesystem, or anything else on the device.
 * Blocking the network also makes the failure honest, since there is no
 * connection to a CDN to depend on in the first place.
 */
@Composable
private fun PagePreview(code: String, revision: Int) {
    // Which revision this WebView has already rendered. A plain box rather than
    // View.setTag(int, …), which throws unless the key is a resource id.
    val rendered = remember { intArrayOf(-1) }

    // The preview is the page's own world: force LTR so an Arabic interface
    // doesn't silently mirror a layout the model wrote for LTR.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.White),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(false)
                    // Lay out at the device width. Left to itself a WebView
                    // uses a 980px viewport and scales the result down, so a
                    // page written for a phone arrives looking like a shrunken
                    // desktop site — worst of all on the largest screen.
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
                    // The system font scale belongs to the app's own text, not
                    // to a page whose layout the model sized itself.
                    settings.textZoom = 100
                }
            },
            // Keyed on the revision so typing in the editor doesn't reload the
            // page under the user on every keystroke.
            update = { web ->
                if (rendered[0] != revision) {
                    rendered[0] = revision
                    web.loadDataWithBaseURL(null, code, "text/html", "UTF-8", null)
                }
            },
        )
    }
}

@Composable
private fun CodeEditor(code: String, onChange: (String) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
            value = code,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(Space.md),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun BuildingView(streaming: String) {
    val s = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(Space.lg)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                s.studioBuilding,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Space.md))
        // The raw stream, so a long build is visibly working rather than hung.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                streaming.takeLast(2000),
                style = MaterialTheme.typography.labelSmall
                    .copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioEmpty(projects: List<StudioProject>, viewModel: ChatViewModel) {
    val s = LocalStrings.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val heightDp = LocalConfiguration.current.screenHeightDp

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.lg),
        // On a tall window a short empty state pinned to the top reads as
        // stranded rather than deliberate. With projects listed there is enough
        // to fill the column, so it goes back to the top.
        verticalArrangement = if (Layout.isTall(widthDp, heightDp) && projects.isEmpty()) {
            Arrangement.Center
        } else {
            Arrangement.Top
        },
    ) {
        Text(
            s.studioEmptyTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Space.sm))
        Caption(s.studioEmptyBody)
        Spacer(Modifier.height(Space.md))
        Caption(s.studioOfflineNote, isWarning = true)

        Spacer(Modifier.height(Space.lg))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..3).forEach { index ->
                val example = s.studioExample(index)
                FilterChip(
                    selected = false,
                    onClick = { viewModel.studioBuild(example) },
                    label = { Text(example) },
                )
            }
        }

        if (projects.isNotEmpty()) {
            Spacer(Modifier.height(Space.xl))
            GroupLabel(s.studioProjects)
            projects.forEach { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .panel(shape = MaterialTheme.shapes.small)
                        .clickable { viewModel.studioOpen(project) }
                        .padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        project.name.ifBlank { s.studioUntitled },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    ToolbarButton(
                        Icons.Filled.Delete,
                        s.remove,
                        { viewModel.studioDelete(project.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildBar(enabled: Boolean, onBuild: (String) -> Unit) {
    val s = LocalStrings.current
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(s.studioPromptHint) },
            shape = MaterialTheme.shapes.medium,
            maxLines = 4,
            enabled = enabled,
        )
        Spacer(Modifier.width(Space.sm))
        SendButton(
            enabled = enabled && text.isNotBlank(),
            onClick = {
                onBuild(text.trim())
                text = ""
            },
        )
    }
}
