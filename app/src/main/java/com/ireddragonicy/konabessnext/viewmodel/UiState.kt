package com.ireddragonicy.konabessnext.viewmodel

import com.ireddragonicy.konabessnext.model.UiText

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: UiText, val exception: Throwable? = null) : UiState<Nothing>()

    companion object {
        fun <T> loading(): UiState<T> = Loading
        fun <T> success(data: T): UiState<T> = Success(data)
        fun <T> error(message: String, exception: Throwable? = null): UiState<T> = Error(UiText.DynamicString(message), exception)
        fun <T> error(message: UiText, exception: Throwable? = null): UiState<T> = Error(message, exception)
    }
}
