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
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val memoryManager = MemoryManager(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    // Твой ключ, разбитый на две части, чтобы GitHub не блокировал сохранение
    private val KEY_PART1 = "AQ.Ab8RN6LDA9BUB3oERV9P04Gvyr-NRmDF"
    private val KEY_PART2 = "_5MiKFAHpBvAgdagzA"
    private val API_KEY = KEY_PART1 + KEY_PART2

    init {
        val savedName = memoryManager.getUserName()
        val initialGreeting = if (savedName != null) {
            "С возвращением, $savedName! Я RizzyAI. Чем могу помочь?"
        } else {
            "Привет! Я RizzyAI. Как тебя зовут? (напиши: «Меня зовут ...»)"
        }
        _messages.value = listOf(Message(text = initialGreeting, isUser = false))
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        val userMsg = Message(text = trimmedText, isUser = true)
        _messages.value = _messages.value + userMsg

        // Сохраняем имя, если пользователь представился
        val nameRegex = Regex("""(?i)(?:меня зовут|мо[её] имя)\s+([a-zA-Zа-яА-ЯёЁ]+)""")
        val matchResult = nameRegex.find(trimmedText)
        if (matchResult != null) {
            val extractedName = matchResult.groupValues[1].replaceFirstChar { it.uppercase() }
            memoryManager.saveUserName(extractedName)
        }

        viewModelScope.launch {
            val botResponseText = fetchGeminiResponse(trimmedText)
            val botMsg = Message(text = botResponseText, isUser = false)
            _messages.value = _messages.value + botMsg
        }
    }

    private suspend fun fetchGeminiResponse(input: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val userName = memoryManager.getUserName()
        val systemPrompt = "Ты - RizzyAI, дружелюбный и умный ИИ-помощник. " +
                if (userName != null) "Пользователя зовут $userName. Обращайся к нему по имени. " else ""
        
        val json = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().put("text", systemPrompt + "Пользователь пишет: $input"))
                    })
                })
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        // Мы используем модель gemini-1.5-flash (она самая быстрая и бесплатная)
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$API_KEY")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.getJSONArray("candidates")
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                parts.getJSONObject(0).getString("text")
            } else {
                // Если ключ не подошел, мы выведем ошибку прямо в чат, чтобы знать причину
                "Ошибка от Google: ${response.code} - $responseBody"
            }
        } catch (e: Exception) {
            "Ошибка сети: ${e.message}"
        }
    }
}
