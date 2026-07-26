package com.example.ondevicellm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ondevicellm.ui.ChatScreen
import com.example.ondevicellm.ui.DeviceScreen
import com.example.ondevicellm.ui.ModelsScreen
import com.example.ondevicellm.ui.SettingsScreen
import com.example.ondevicellm.ui.theme.OnDeviceLLMTheme

private enum class Destination(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    MODELS("Models", Icons.Filled.Storage),
    DEVICE("Device", Icons.Filled.Memory),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    OnDeviceLLMTheme {
        val viewModel: ChatViewModel = viewModel()
        var destination by remember { mutableStateOf(Destination.CHAT) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(title = { Text("On-Device LLM") })
            },
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (destination) {
                Destination.CHAT -> ChatScreen(
                    viewModel = viewModel,
                    onOpenModels = { destination = Destination.MODELS },
                    modifier = contentModifier,
                )

                Destination.MODELS -> ModelsScreen(viewModel, contentModifier)
                Destination.DEVICE -> DeviceScreen(viewModel, contentModifier)
                Destination.SETTINGS -> SettingsScreen(viewModel, contentModifier)
            }
        }
    }
}
