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

    // ВСТАВЬ СВОЙ КЛЮЧ ОТ GROQ (разбитый на две части, как ты делал)
    private val KEY_PART1 = "gsk_BnNgQ7PBrdiVVtUROZSoWGdyb"
    private val KEY_PART2 = "3FYBFVM0dD7lMjfCvwRYdkTnh5e"
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
            val botResponseText = fetchGroqResponse(trimmedText)
            val botMsg = Message(text = botResponseText, isUser = false)
            _messages.value = _messages.value + botMsg
        }
    }

    private suspend fun fetchGroqResponse(input: String): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val userName = memoryManager.getUserName()
        val systemPrompt = "Ты - RizzyAI, дружелюбный и умный ИИ-помощник. " +
                if (userName != null) "Пользователя зовут $userName. Обращайся к нему по имени. " else ""

        // Собираем историю сообщений для отправки в Groq
        val messagesArray = org.json.JSONArray()
        
        // 1. Системный промпт (характер ИИ)
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // 2. Вся история из текущего чата (чтобы она помнила "селедку")
        _messages.value.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.isUser) "user" else "assistant")
                put("content", msg.text)
            })
        }

        // 3. Итоговый JSON
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
