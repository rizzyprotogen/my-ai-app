package com.example.myaiapp

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

enum class AiPersona(val displayName: String, val promptPrefix: String) {
    NORMAL("Обычный", "Ты - RizzyAI, дружелюбный и умный ИИ-помощник. Отвечай понятно."),
    SARCASTIC("Дерзкий", "Ты - RizzyAI. Отвечай с сарказмом, дерзко, но по делу."),
    PIRATE("Пират", "Ты - RizzyAI. Отвечай как старый пират: 'Йо-хо-хо', 'Тысяча чертей'."),
    CODER("Программист", "Ты - RizzyAI, эксперт по коду. Отвечай технически грамотно, используй примеры кода.")
}

data class AppSettings(
    val selectedModel: String = "openai/gpt-oss-120b",
    val temperature: Float = 0.7f,
    val persona: String = "NORMAL"
)

class MemoryManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("rizzy_ai_prefs", Context.MODE_PRIVATE)
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
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

    // --- СОХРАНЕНИЕ В ОБЛАКО ---
    fun saveChatsToCloud(chats: List<ChatSession>) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("RIZZY_DEBUG", "Ошибка сохранения: пользователь не авторизован")
            return
        }
        
        val chatsCollection = db.collection("users").document(userId).collection("chats")
        
        chatsCollection.get().addOnSuccessListener { documents ->
            for (document in documents) {
                document.reference.delete()
            }
            
            chats.forEach { chat ->
                val chatData = hashMapOf(
                    "id" to chat.id,
                    "title" to chat.title,
                    "messages" to chat.messages.map { msg ->
                        hashMapOf(
                            "id" to msg.id,
                            "text" to msg.text,
                            "isUser" to msg.isUser,
                            "timestamp" to msg.timestamp
                        )
                    }
                )
                chatsCollection.document(chat.id).set(chatData)
                    .addOnFailureListener { e ->
                        Log.e("RIZZY_DEBUG", "Ошибка записи в Firestore: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("RIZZY_DEBUG", "Ошибка чтения Firestore: ${e.message}")
        }
    }

    // --- ЗАГРУЗКА ИЗ ОБЛАКА ---
    fun loadChatsFromCloud(onSuccess: (List<ChatSession>) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e("RIZZY_DEBUG", "Ошибка загрузки: пользователь не авторизован")
            onSuccess(emptyList())
            return
        }
        
        val chatsCollection = db.collection("users").document(userId).collection("chats")
        
        chatsCollection.get().addOnSuccessListener { documents ->
            val chats = mutableListOf<ChatSession>()
            for (document in documents) {
                val id = document.getString("id") ?: continue
                val title = document.getString("title") ?: "Новый чат"
                
                val messagesList = mutableListOf<Message>()
                val messagesRaw = document.get("messages") as? List<Map<String, Any>>
                messagesRaw?.forEach { msgMap ->
                    messagesList.add(
                        Message(
                            id = msgMap["id"] as? String ?: UUID.randomUUID().toString(),
                            text = msgMap["text"] as? String ?: "",
                            isUser = msgMap["isUser"] as? Boolean ?: false,
                            timestamp = (msgMap["timestamp"] as? Long) ?: System.currentTimeMillis()
                        )
                    )
                }
                chats.add(ChatSession(id, title, messagesList))
            }
            Log.d("RIZZY_DEBUG", "Загружено чатов из облака: ${chats.size}")
            onSuccess(chats)
        }.addOnFailureListener { e ->
            Log.e("RIZZY_DEBUG", "Ошибка загрузки из Firestore: ${e.message}")
            onSuccess(emptyList())
        }
    }

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_PERSONA = "persona"
    }
}
