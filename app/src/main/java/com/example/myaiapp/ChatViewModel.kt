package com.example.myaiapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val memoryManager = MemoryManager(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    init {
        val savedName = memoryManager.getUserName()
        val initialGreeting = if (savedName != null) {
            "С возвращением, $savedName! Рад снова тебя видеть. Чем могу помочь?"
        } else {
            "Привет! Я твой персональный AI-ассистент. Как тебя зовут? (напиши: «Меня зовут ...»)"
        }
        _messages.value = listOf(Message(text = initialGreeting, isUser = false))
    }

    fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        val userMsg = Message(text = trimmedText, isUser = true)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            delay(600)
            val botResponseText = generateResponse(trimmedText)
            val botMsg = Message(text = botResponseText, isUser = false)
            _messages.value = _messages.value + botMsg
        }
    }

    private fun generateResponse(input: String): String {
        val lower = input.lowercase()

        val nameRegex = Regex("""(?i)(?:меня зовут|мо[её] имя)\s+([a-zA-Zа-яА-ЯёЁ]+)""")
        val matchResult = nameRegex.find(input)

        if (matchResult != null) {
            val extractedName = matchResult.groupValues[1].replaceFirstChar { it.uppercase() }
            memoryManager.saveUserName(extractedName)
            return "Приятно познакомиться, $extractedName! Я сохранил твоё имя и буду помнить его."
        }

        val userName = memoryManager.getUserName()

        if (lower.contains("как меня зовут") || lower.contains("помнишь меня")) {
            return if (userName != null) {
                "Тебя зовут $userName! Я ничего не забыл."
            } else {
                "Ты мне пока не представлялся. Напиши: «Меня зовут [Твое имя]»."
            }
        }

        if (lower.contains("привет") || lower.contains("здравствуй") || lower.contains("хай")) {
            return if (userName != null) {
                "Привет, $userName! Чем я могу быть полезен сегодня?"
            } else {
                "Приветствую! Чем могу помочь?"
            }
        }

        return if (userName != null) {
            "$userName, я зафиксировал твой запрос: «$input». Я пока работаю в демонстрационном режиме, но отлично храню твои данные!"
        } else {
            "Я понял ваш запрос: «$input». Кстати, мы ещё не знакомы — вы можете написать «Меня зовут ...», и я запомню вас!"
        }
    }
}
