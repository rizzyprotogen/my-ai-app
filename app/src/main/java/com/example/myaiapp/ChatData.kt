package com.example.myaiapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Новый чат",
    var messages: List<Message> = emptyList()
)

// Характеры ИИ
enum class AiPersona(val displayName: String, val promptPrefix: String) {
    NORMAL("Обычный", "Ты - RizzyAI, дружелюбный и умный ИИ-помощник. Отвечай понятно."),
    SARCASTIC("Дерзкий", "Ты - RizzyAI. Отвечай с сарказмом, дерзко, но по делу."),
    PIRATE("Пират", "Ты - RizzyAI. Отвечай как старый пират: 'Йо-хо-хо', 'Тысяча чертей'."),
    CODER("Программист", "Ты - RizzyAI, эксперт по коду. Отвечай технически грамотно, используй примеры кода.")
}

data class AppSettings(
    val selectedModel: String = "openai/gpt-oss-120b",
    val temperature: Float = 0.7f,
    val persona: String = "NORMAL" // Храним название характера (enum)
)

class MemoryManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("rizzy_ai_prefs", Context.MODE_PRIVATE)

    fun saveUserName(name: String) { prefs.edit().putString(KEY_USER_NAME, name).apply() }
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun saveChats(chats: List<ChatSession>) {
        val jsonArray = JSONArray()
        chats.forEach { chat ->
            val chatObj = JSONObject().apply {
                put("id", chat.id)
                put("title", chat.title)
                val msgArray = JSONArray()
                chat.messages.forEach { msg ->
                    msgArray.put(JSONObject().apply {
                        put("id", msg.id); put("text", msg.text); put("isUser", msg.isUser); put("timestamp", msg.timestamp)
                    })
                }
                put("messages", msgArray)
            }
            jsonArray.put(chatObj)
        }
        prefs.edit().putString(KEY_CHATS, jsonArray.toString()).apply()
    }

    fun loadChats(): List<ChatSession> {
        val jsonString = prefs.getString(KEY_CHATS, null) ?: return emptyList()
        val chats = mutableListOf<ChatSession>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val chatObj = jsonArray.getJSONObject(i)
                val msgArray = chatObj.getJSONArray("messages")
                val messages = mutableListOf<Message>()
                for (j in 0 until msgArray.length()) {
                    val msgObj = msgArray.getJSONObject(j)
                    messages.add(Message(msgObj.getString("id"), msgObj.getString("text"), msgObj.getBoolean("isUser"), msgObj.getLong("timestamp")))
                }
                chats.add(ChatSession(chatObj.getString("id"), chatObj.getString("title"), messages))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return chats
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_MODEL, settings.selectedModel)
            .putFloat(KEY_TEMPERATURE, settings.temperature)
            .putString(KEY_PERSONA, settings.persona)
            .apply()
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            selectedModel = prefs.getString(KEY_MODEL, "openai/gpt-oss-120b") ?: "openai/gpt-oss-120b",
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            persona = prefs.getString(KEY_PERSONA, "NORMAL") ?: "NORMAL"
        )
    }

    fun clearAllChats() { prefs.edit().remove(KEY_CHATS).apply() }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CHATS = "all_chats"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_PERSONA = "persona"
    }
}
