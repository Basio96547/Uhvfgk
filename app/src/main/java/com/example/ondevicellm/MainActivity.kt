package com.example.ondevicellm

import android.content.Context
import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ondevicellm.core.AppStrings
import com.example.ondevicellm.core.ErrorLog
import com.example.ondevicellm.ui.ChatScreen
import com.example.ondevicellm.ui.LocalStrings
import com.example.ondevicellm.ui.ProvideLocalization
import com.example.ondevicellm.ui.DiagnosticsDialog
import com.example.ondevicellm.ui.DeviceScreen
import com.example.ondevicellm.ui.ModelsScreen
import com.example.ondevicellm.ui.SettingsScreen
import com.example.ondevicellm.ui.theme.OnDeviceLLMTheme
import com.example.ondevicellm.ui.theme.Space
import com.example.ondevicellm.ui.theme.hairlineColor

private enum class Destination(val icon: ImageVector) {
    CHAT(Icons.AutoMirrored.Filled.Chat),
    MODELS(Icons.Filled.ViewInAr),
    DEVICE(Icons.Filled.Memory),
    SETTINGS(Icons.Filled.Tune),
    ;

    fun title(s: AppStrings): String = when (this) {
        CHAT -> s.navChat
        MODELS -> s.navModels
        DEVICE -> s.navDevice
        SETTINGS -> s.navSettings
    }

    fun subtitle(s: AppStrings): String = when (this) {
        CHAT -> s.chatSubtitle
        MODELS -> s.modelsSubtitle
        DEVICE -> s.deviceSubtitle
        SETTINGS -> s.settingsSubtitle
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableSustainedPerformance()
        setContent { App() }
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

@Composable
fun App() {
    OnDeviceLLMTheme {
        val viewModel: ChatViewModel = viewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()

        // Wraps everything: the language switch has to change layout direction
        // too, not just the words, or Arabic ends up inside an English shell.
        ProvideLocalization(settings.language) { AppContent(viewModel) }
    }
}

@Composable
private fun AppContent(viewModel: ChatViewModel) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf(Destination.CHAT) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val unseenProblems by ErrorLog.unseenCount.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

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

                        Destination.MODELS -> ModelsScreen(viewModel)
                        Destination.DEVICE -> DeviceScreen(viewModel)
                        Destination.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }

            BottomBar(
                selected = destination,
                onSelect = { destination = it },
            )
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(onDismiss = { showDiagnostics = false })
    }
}

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
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
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
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { item ->
                NavItem(
                    item = item,
                    isSelected = item == selected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(item: Destination, isSelected: Boolean, onClick: () -> Unit) {
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
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Space.lg, vertical = 6.dp),
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
                .padding(horizontal = Space.lg, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(21.dp).scale(iconScale),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
