package com.example.ondevicellm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ondevicellm.ui.ChatScreen
import com.example.ondevicellm.ui.theme.OnDeviceLLMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

@Composable
fun App() {
    OnDeviceLLMTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val viewModel: ChatViewModel = viewModel()
            ChatScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
