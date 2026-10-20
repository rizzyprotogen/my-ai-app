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

    // Список всех чатов
    private val _chats = MutableStateFlow<List<ChatSession>>(emptyList())
    val chats = _chats.asStateFlow()

    // Текущий активный чат
    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId = _currentChatId.asStateFlow()

    // Индикатор загрузки (для анимации "думания")
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val KEY_PART1 = "gsk_BnNgQ7PBrdiVVtUROZSoWGdyb" // твоя первая часть
    private val KEY_PART2 = "3FYBFVM0dD7lMjfCvwRYdkTnh5e"     // твоя вторая часть
    private val API_KEY = KEY_PART1 + KEY_PART2

    init {
        // Загружаем сохраненные чаты при запуске
        val savedChats = memoryManager.loadChats()
        if (savedChats.isEmpty()) {
            createNewChat() // Если чатов нет, создаем первый
        } else {
            _chats.value = savedChats
            _currentChatId.value = savedChats.first().id
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

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() || _isLoading.value) return
        
        val chatId = _currentChatId.value ?: return
        val currentChat = _chats.value.find { it.id == chatId } ?: return

        // Сохраняем имя, если пользователь представился
        val nameRegex = Regex("""(?i)(?:меня зовут|мо[её] имя)\s+([a-zA-Zа-яА-ЯёЁ]+)""")
        val matchResult = nameRegex.find(trimmedText)
        if (matchResult != null) {
            val extractedName = matchResult.groupValues[1].replaceFirstChar { it.uppercase() }
            memoryManager.saveUserName(extractedName)
        }

        // Добавляем сообщение пользователя
        val userMsg = Message(text = trimmedText, isUser = true)
        var updatedMessages = currentChat.messages + userMsg
        var updatedTitle = currentChat.title

        // Если это первый вопрос в чате, делаем его название по первым словам
        if (currentChat.title == "Новый чат" && currentChat.messages.size <= 1) {
            updatedTitle = trimmedText.take(25) + if (trimmedText.length > 25) "..." else ""
        }

        // Обновляем чат
        updateChatInList(chatId, updatedMessages, updatedTitle)
        _isLoading.value = true

        viewModelScope.launch {
            val botResponseText = fetchGroqResponse(trimmedText, updatedMessages)
            val botMsg = Message(text = botResponseText, isUser = false)
            
            // Получаем актуальный чат (вдруг пользователь переключился, пока ИИ думала)
            val latestChat = _chats.value.find { it.id == chatId }
            if (latestChat != null) {
                updateChatInList(chatId, latestChat.messages + botMsg, latestChat.title)
            }
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
        memoryManager.saveChats(_chats.value)
    }

    private suspend fun fetchGroqResponse(input: String, history: List<Message>): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val userName = memoryManager.getUserName()
        val systemPrompt = "Ты - RizzyAI, дружелюбный и умный ИИ-помощник. " +
                if (userName != null) "Пользователя зовут $userName. Обращайся к нему по имени. " else ""

        val messagesArray = org.json.JSONArray()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // Передаем историю только текущего чата
        history.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.text)
            })
        }

        val json = JSONObject().apply {
            put("model", "openai/gpt-oss-120b") 
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
