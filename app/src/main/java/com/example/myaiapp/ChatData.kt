package com.example.myaiapp

import android.content.Context
import android.content.SharedPreferences

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class MemoryManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("ai_chat_prefs", Context.MODE_PRIVATE)

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
    }
}
