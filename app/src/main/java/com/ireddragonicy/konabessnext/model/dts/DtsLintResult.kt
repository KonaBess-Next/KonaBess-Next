package com.ireddragonicy.konabessnext.model.dts

import androidx.compose.runtime.Immutable

enum class Severity { ERROR, WARNING }

@Immutable
data class DtsError(
    val line: Int,      // 0-based line index
    val column: Int,    // 0-based character index
    val message: String,
    val severity: Severity = Severity.ERROR
)

@Immutable
data class DtsLintResult(
    val isValid: Boolean,
    val errors: List<DtsError>
)
