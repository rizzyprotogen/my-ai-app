package com.example.myaiapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow(auth.currentUser != null)
    val authState = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _resetPasswordSent = MutableStateFlow(false)
    val resetPasswordSent = _resetPasswordSent.asStateFlow()

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Заполните все поля"
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        _authState.value = true
                    } else {
                        _errorMessage.value = task.exception?.localizedMessage ?: "Ошибка входа"
                    }
                }
        }
    }

    fun register(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Заполните все поля"
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        _authState.value = true
                    } else {
                        _errorMessage.value = task.exception?.localizedMessage ?: "Ошибка регистрации"
                    }
                }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = "Введите почту"
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        _resetPasswordSent.value = false
        viewModelScope.launch {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        _resetPasswordSent.value = true
                    } else {
                        _errorMessage.value = task.exception?.localizedMessage ?: "Ошибка сброса пароля"
                    }
                }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _resetPasswordSent.value = false
    }

    fun logout() {
        auth.signOut()
        _authState.value = false
    }
}
