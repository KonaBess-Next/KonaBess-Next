package com.ireddragonicy.konabessnext.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserMessageManager @Inject constructor() {

    sealed class UserMessage {
        data class Error(val title: String, val message: String) : UserMessage()
        data class Warning(val message: String) : UserMessage()
        data class Info(val message: String) : UserMessage()
    }

    private val _messages = MutableSharedFlow<UserMessage>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun emitError(title: String, message: String) {
        _messages.tryEmit(UserMessage.Error(title, message))
    }
    
    fun emitError(t: Throwable) {
        _messages.tryEmit(UserMessage.Error("Error", t.localizedMessage ?: "Unknown error"))
    }

    fun emitWarning(message: String) {
        _messages.tryEmit(UserMessage.Warning(message))
    }

    fun emitInfo(message: String) {
        _messages.tryEmit(UserMessage.Info(message))
    }
}
