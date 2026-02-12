package com.ireddragonicy.konabessnext.core.model

sealed class DomainResult<out T> {
    data class Success<out T>(val data: T) : DomainResult<T>()
    data class Failure(val error: AppError) : DomainResult<Nothing>()
}

sealed class AppError(
    val message: String,
    val cause: Throwable? = null
) {
    class IoError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class ShellError(message: String, val command: String? = null) : AppError(message)
    class ParsingError(message: String) : AppError(message)
    class BootImageError(message: String, cause: Throwable? = null) : AppError(message, cause)
    class RootAccessError(message: String = "Root access denied or unavailable") : AppError(message)
    class UnknownError(message: String, cause: Throwable? = null) : AppError(message, cause)
}
