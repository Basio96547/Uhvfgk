package com.example.ondevicellm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Author { USER, MODEL }

data class ChatMessage(
    val id: Long,
    val author: Author,
    val text: String,
    val isGenerating: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isModelReady: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var model: InferenceModel? = null
    private var nextId = 0L

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                model = InferenceModel.getInstance(getApplication())
                _uiState.update { it.copy(isModelReady = true, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isModelReady = false, errorMessage = e.message ?: "Failed to load model")
                }
            }
        }
    }

    fun retryLoadModel() {
        _uiState.update { it.copy(errorMessage = null) }
        loadModel()
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        val currentModel = model
        if (prompt.isEmpty() || currentModel == null || _uiState.value.isBusy) return

        val userMessage = ChatMessage(id = nextId++, author = Author.USER, text = prompt)
        val modelMessageId = nextId++
        val modelPlaceholder = ChatMessage(
            id = modelMessageId,
            author = Author.MODEL,
            text = "",
            isGenerating = true,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + modelPlaceholder,
                isBusy = true,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                currentModel.generateResponseAsync(prompt) { partial, done ->
                    appendToMessage(modelMessageId, partial, done)
                }
            } catch (e: Exception) {
                appendToMessage(modelMessageId, "\n[error: ${e.message}]", done = true)
            }
        }
    }

    private fun appendToMessage(id: Long, chunk: String, done: Boolean) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == id) {
                    msg.copy(text = msg.text + chunk, isGenerating = !done)
                } else {
                    msg
                }
            }
            state.copy(messages = updated, isBusy = if (done) false else state.isBusy)
        }
    }

    fun clearConversation() {
        if (_uiState.value.isBusy) return
        model?.resetSession()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        // The InferenceModel is a process-wide singleton; do not close it here so
        // it survives configuration changes. It is released when the process dies.
    }
}
