package com.basel.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import com.basel.ai.core.AppStrings
import com.basel.ai.core.ErrorLog
import com.basel.ai.ui.ChatScreen
import com.basel.ai.ui.LocalStrings
import com.basel.ai.ui.ProvideLocalization
import com.basel.ai.ui.ConversationsDialog
import com.basel.ai.ui.DiagnosticsDialog
import com.basel.ai.ui.DeviceScreen
import com.basel.ai.ui.ModelsScreen
import com.basel.ai.ui.SettingsScreen
import com.basel.ai.ui.StudioScreen
import com.basel.ai.ui.theme.BaselAiTheme
import com.basel.ai.ui.theme.Layout
import com.basel.ai.ui.theme.Space
import com.basel.ai.ui.theme.hairlineColor

private enum class Destination(val icon: ImageVector) {
    CHAT(Icons.AutoMirrored.Filled.Chat),
    STUDIO(Icons.Filled.Code),
    MODELS(Icons.Filled.ViewInAr),
    DEVICE(Icons.Filled.Memory),
    SETTINGS(Icons.Filled.Tune),
    ;

    fun title(s: AppStrings): String = when (this) {
        CHAT -> s.navChat
        STUDIO -> s.navStudio
        MODELS -> s.navModels
        DEVICE -> s.navDevice
        SETTINGS -> s.navSettings
    }

    fun subtitle(s: AppStrings): String = when (this) {
        CHAT -> s.chatSubtitle
        STUDIO -> s.studioSubtitle
        MODELS -> s.modelsSubtitle
        DEVICE -> s.deviceSubtitle
        SETTINGS -> s.settingsSubtitle
    }
}

class MainActivity : ComponentActivity() {

    /**
     * What was shared into the app, if anything.
     *
     * A flow rather than a field because a share can arrive while the app is
     * already open — `singleTask` delivers it to [onNewIntent] on the running
     * instance, and the composition has to hear about it.
     */
    private val shared = MutableStateFlow<Shared?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableSustainedPerformance()
        shared.value = readShare(intent)
        setContent { App(shared) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shared.value = readShare(intent)
    }

    /** A PDF or a passage of text sent from another app. */
    private fun readShare(intent: Intent?): Shared? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) return Shared.Document(uri)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        return text?.takeIf { it.isNotEmpty() }?.let(Shared::Text)
    }

    /**
     * Caps peak clocks at a level the phone can hold indefinitely.
     *
     * Generating tokens keeps the CPU busy for minutes at a time. Without this
     * the SoC boosts hard, overheats, and is then throttled well below the
     * sustainable rate — so the burst costs more than it buys. Asking for
     * sustained mode trades a slightly slower start for steady speed and a
     * cooler device.
     */
    private fun enableSustainedPerformance() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isSustainedPerformanceModeSupported) {
            runCatching { window.setSustainedPerformanceMode(true) }
        }
    }
}

/** Something another app sent here. */
sealed interface Shared {
    data class Text(val text: String) : Shared
    data class Document(val uri: Uri) : Shared
}

@Composable
fun App(shared: MutableStateFlow<Shared?> = MutableStateFlow(null)) {
    // Read before the theme, because the theme needs to know the language:
    // Arabic and Latin want different leading and different tracking, and
    // treating one as a translation of the other is how Arabic ends up looking
    // cramped in an app that is otherwise fine.
    val viewModel: ChatViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    BaselAiTheme(arabic = settings.language.resolve().isRtl) {
        // Wraps everything: the language switch has to change layout direction
        // too, not just the words, or Arabic ends up inside an English shell.
        // Acted on once, then cleared, so rotating the screen does not import
        // the same document again.
        val incoming by shared.collectAsStateWithLifecycle()
        LaunchedEffect(incoming) {
            when (val item = incoming) {
                is Shared.Document -> viewModel.importDocument(item.uri)
                is Shared.Text -> viewModel.setVoiceDraft(item.text)
                null -> Unit
            }
            if (incoming != null) shared.value = null
        }

        ProvideLocalization(settings.language) { AppContent(viewModel) }
    }
}

@Composable
private fun AppContent(viewModel: ChatViewModel) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.CHAT) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showConversations by remember { mutableStateOf(false) }
    val unseenProblems by ErrorLog.unseenCount.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // enableEdgeToEdge() draws behind the system bars, so the keyboard has
        // to be accounted for explicitly or it sits on top of the text field.
        // Applied once at the root: every screen with an input gets it, and the
        // navigation bar rides up with the content rather than being buried.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {

            AppHeader(
                destination = destination,
                isSpeaking = state.speakingMessageId != null,
                isGenerating = state.isBusy,
                canReset = destination == Destination.CHAT &&
                    state.status == ModelStatus.READY && !state.isBusy,
                unseenProblems = unseenProblems,
                onStopSpeaking = viewModel::stopSpeaking,
                onStopGenerating = viewModel::stopGeneration,
                onReset = viewModel::clearConversation,
                onOpenDiagnostics = {
                    ErrorLog.markSeen()
                    showDiagnostics = true
                },
                onOpenConversations = { showConversations = true },
            )

            Box(Modifier.weight(1f)) {
                // Crossfade keeps tab switches calm; a slide would fight the
                // bottom bar's own motion.
                Crossfade(
                    targetState = destination,
                    animationSpec = tween(220),
                    label = "screen",
                ) { current ->
                    when (current) {
                        Destination.CHAT -> ChatScreen(
                            viewModel = viewModel,
                            onOpenModels = { destination = Destination.MODELS },
                        )

                        Destination.STUDIO -> StudioScreen(viewModel)
                        Destination.MODELS -> ModelsScreen(viewModel)
                        Destination.DEVICE -> DeviceScreen(viewModel)
                        Destination.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }

            // Hidden while the keyboard is up. The root already applies
            // imePadding, so the bar would otherwise sit squeezed between the
            // composer and the keyboard, taking about seventy dp of the little
            // vertical room left — on the one screen where every line counts.
            if (!isKeyboardOpen()) {
                BottomBar(
                    selected = destination,
                    onSelect = { destination = it },
                )
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(onDismiss = { showDiagnostics = false })
    }

    if (showConversations) {
        ConversationsDialog(
            viewModel = viewModel,
            onOpened = {
                showConversations = false
                destination = Destination.CHAT
            },
            onDismiss = { showConversations = false },
        )
    }
}

/** True while the soft keyboard is showing. */
@Composable
private fun isKeyboardOpen(): Boolean =
    WindowInsets.ime.getBottom(LocalDensity.current) > 0

@Composable
private fun AppHeader(
    destination: Destination,
    isSpeaking: Boolean,
    isGenerating: Boolean,
    canReset: Boolean,
    unseenProblems: Int,
    onStopSpeaking: () -> Unit,
    onStopGenerating: () -> Unit,
    onReset: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenConversations: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.xl, end = Space.md, top = Space.md, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                destination.title(strings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                destination.subtitle(strings),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Only appears while there is something to interrupt.
        AnimatedVisibility(
            visible = isGenerating,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            HeaderButton(Icons.Filled.Stop, strings.stopGenerating, onStopGenerating, accent = true)
        }

        AnimatedVisibility(
            visible = isSpeaking,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            HeaderButton(Icons.Filled.VolumeOff, strings.stopSpeaking, onStopSpeaking, accent = true)
        }

        // Badge shows only when something actually failed.
        if (unseenProblems > 0) {
            Spacer(Modifier.width(Space.xs))
            Box {
                HeaderButton(
                    Icons.Filled.ReportProblem,
                    strings.diagnostics,
                    onOpenDiagnostics,
                    accent = true,
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }

        if (destination == Destination.CHAT) {
            Spacer(Modifier.width(Space.xs))
            // Saved chats first: it is the one you reach for when you want
            // something back, and "new" is a click away inside it too.
            HeaderButton(
                Icons.AutoMirrored.Filled.Chat,
                strings.conversationsTitle,
                onOpenConversations,
            )
            HeaderButton(Icons.Filled.RestartAlt, strings.newChat, onReset, enabled = canReset)
        }
    }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(Layout.TOUCH_TARGET_DP.dp)
            .clip(CircleShape)
            .background(
                if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/**
 * Custom bottom bar rather than Material's NavigationBar: each item animates its
 * own pill, which reads more deliberate than the stock indicator and lets the
 * label fade in only for the active tab.
 */
@Composable
private fun BottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairlineColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = Space.sm, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val widthDp = LocalConfiguration.current.screenWidthDp
            val pillPadding = Layout.navPillPadding(widthDp, Destination.entries.size).dp
            // The user's text size, which nothing here used to look at.
            val showLabels = Layout.showNavLabels(
                widthDp = widthDp,
                tabs = Destination.entries.size,
                fontScale = LocalDensity.current.fontScale,
            )
            Destination.entries.forEach { item ->
                NavItem(
                    item = item,
                    isSelected = item == selected,
                    pillPadding = pillPadding,
                    showLabel = showLabels,
                    // Equal shares of the row. Five tabs at the padding four
                    // used needed 425dp of a 395dp row and the last one fell
                    // off the edge, which is why the pill padding is computed
                    // rather than fixed.
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item: Destination,
    isSelected: Boolean,
    pillPadding: androidx.compose.ui.unit.Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val label = item.title(LocalStrings.current)
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.92f,
        animationSpec = spring(),
        label = "navIconScale",
    )
    val tint by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "navTint",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = pillPadding, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(Layout.NAV_ICON_DP.dp).scale(iconScale),
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
