package com.example.myaiapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Модель одного сообщения
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// Модель целого чата (сессии)
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Новый чат",
    var messages: List<Message> = emptyList()
)

// Хранилище всех чатов и настроек
class MemoryManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("rizzy_ai_prefs", Context.MODE_PRIVATE)

    // --- 1. Управление именем пользователя ---
    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    // --- 2. Управление списком чатов ---
    fun saveChats(chats: List<ChatSession>) {
        val jsonArray = JSONArray()
        chats.forEach { chat ->
            val chatObj = JSONObject()
            chatObj.put("id", chat.id)
            chatObj.put("title", chat.title)
            
            val msgArray = JSONArray()
            chat.messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("id", msg.id)
                msgObj.put("text", msg.text)
                msgObj.put("isUser", msg.isUser)
                msgObj.put("timestamp", msg.timestamp)
                msgArray.put(msgObj)
            }
            chatObj.put("messages", msgArray)
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
                val id = chatObj.getString("id")
                val title = chatObj.getString("title")
                
                val msgArray = chatObj.getJSONArray("messages")
                val messages = mutableListOf<Message>()
                for (j in 0 until msgArray.length()) {
                    val msgObj = msgArray.getJSONObject(j)
                    messages.add(
                        Message(
                            id = msgObj.getString("id"),
                            text = msgObj.getString("text"),
                            isUser = msgObj.getBoolean("isUser"),
                            timestamp = msgObj.getLong("timestamp")
                        )
                    )
                }
                chats.add(ChatSession(id, title, messages))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return chats
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_CHATS = "all_chats"
    }
}
