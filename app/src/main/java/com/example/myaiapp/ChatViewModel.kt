package com.example.myaiapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val memoryManager = MemoryManager(application)

    private val _chats = MutableStateFlow<List<ChatSession>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId = _currentChatId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings = _settings.asStateFlow()

    // Ключ берется из секретов GitHub (BuildConfig)
    private val API_KEY = BuildConfig.GROQ_API_KEY

    init {
        try {
            val savedChats = memoryManager.loadChats()
            if (savedChats.isEmpty()) {
                createNewChat()
            } else {
                _chats.value = savedChats
                _currentChatId.value = savedChats.first().id
            }
            _settings.value = memoryManager.loadSettings()
        } catch (e: Exception) {
            createNewChat()
        }
    }

    fun createNewChat() {
        val newChat = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "Новый чат",
            messages = listOf(Message(text = "Привет! Я RizzyAI. Чем могу помочь?", isUser = false))
        )
        _chats.value = listOf(newChat) + _chats.value
        _currentChatId.value = newChat.id
        saveChats()
    }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        memoryManager.saveSettings(newSettings)
    }

    fun clearAllChats() {
        _chats.value = emptyList()
        memoryManager.clearAllChats()
        createNewChat()
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() || _isLoading.value) return
        
        val chatId = _currentChatId.value ?: return
        val currentChat = _chats.value.find { it.id == chatId } ?: return

        try {
            val userMsg = Message(text = trimmedText, isUser = true)
            val updatedMessages = currentChat.messages + userMsg
            var updatedTitle = currentChat.title
            if (currentChat.title == "Новый чат" && currentChat.messages.size <= 1) {
                updatedTitle = trimmedText.take(25) + if (trimmedText.length > 25) "..." else ""
            }
            updateChatInList(chatId, updatedMessages, updatedTitle)
            _isLoading.value = true

            viewModelScope.launch {
                try {
                    val botResponseText = fetchGroqResponse(trimmedText, updatedMessages)
                    val botMsg = Message(text = botResponseText, isUser = false)
                    
                    val latestChat = _chats.value.find { it.id == chatId }
                    if (latestChat != null) {
                        updateChatInList(chatId, latestChat.messages + botMsg, latestChat.title)
                    }
                } catch (e: Exception) {
                    val latestChat = _chats.value.find { it.id == chatId }
                    if (latestChat != null) {
                        val errorMsg = Message(text = "Ошибка: ${e.message}", isUser = false)
                        updateChatInList(chatId, latestChat.messages + errorMsg, latestChat.title)
                    }
                } finally {
                    _isLoading.value = false
                }
            }
        } catch (e: Exception) {
            _isLoading.value = false
        }
    }

    private fun updateChatInList(chatId: String, newMessages: List<Message>, newTitle: String) {
        _chats.value = _chats.value.map { 
            if (it.id == chatId) it.copy(messages = newMessages, title = newTitle) else it 
        }
        saveChats()
    }

    private fun saveChats() {
        try {
            memoryManager.saveChats(_chats.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchGroqResponse(input: String, history: List<Message>): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val userName = memoryManager.getUserName()
        val currentSettings = _settings.value
        
        val selectedPersona = AiPersona.valueOf(currentSettings.persona)
        
        // ОБНОВЛЕННЫЙ ПРОМПТ: просим нейросеть делать компактные таблицы (до 4 столбцов)
        var systemPrompt = "${selectedPersona.promptPrefix} " +
                "ВАЖНО: Всегда используй Markdown для оформления текста. " +
                "Если делаешь таблицу, ОБЯЗАТЕЛЬНО ставь пробелы вокруг палочек: '| Текст | Текст |'. " +
                "Не делай таблицы шире 4 столбцов! Если данных больше, напиши их обычным списком. " +
                "Не используй сложные математические формулы LaTeX (пиши обычным текстом)."
                
        if (userName != null) {
            systemPrompt += " Пользователя зовут $userName. Обращайся к нему по имени."
        }

        val messagesArray = org.json.JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        history.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.text)
            })
        }

        val json = JSONObject().apply {
            put("model", currentSettings.selectedModel) 
            put("messages", messagesArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $API_KEY")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                val message = choices.getJSONObject(0).getJSONObject("message")
                message.getString("content")
            } else {
                "Ошибка от сервера: ${response.code}\n$responseBody"
            }
        } catch (e: Exception) {
            "Ошибка сети: ${e.message}"
        }
    }
}
