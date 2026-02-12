package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.DtsLintResult
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import com.ireddragonicy.konabessnext.model.dts.Severity

/**
 * Fault-tolerant DTS parser intended for real-time editor linting.
 *
 * Design goals:
 *  - Never throw for malformed/partial input (except cooperative cancellation).
 *  - Never infinite-loop: every loop makes progress or terminates.
 *  - Keep best-effort AST output while collecting diagnostics.
 *  - Support cooperative cancellation + parse budgets.
 */
class DtsParser(tokens: List<Token>) {

    data class ParseBudget(
        val maxTokensConsumed: Int = Int.MAX_VALUE,
        val timeBudgetMs: Long? = null,
        val checkIntervalTokens: Int = DEFAULT_CHECK_INTERVAL
    )

    data class ParseOptions(
        val checkCancelled: (() -> Unit)? = null,
        val budget: ParseBudget? = null,
        val maxErrors: Int = DEFAULT_MAX_ERRORS
    )

    private enum class RecoveryContext { TOP_LEVEL, NODE, PROPERTY }

    private data class ErrorKey(
        val line: Int,
        val column: Int,
        val message: String,
        val severity: Severity
    )

    private val eofToken: Token
    private val tokens: Array<Token>
    private val len: Int

    private var pos = 0
    private var consumedTokens = 0
    private var parseStartNs = 0L
    private var nextCheckpointToken = 0
    private var hardTokenLimit = 0
    private var stopParsing = false
    private var budgetWarningEmitted = false
    private var tooManyErrorsEmitted = false
    private var options: ParseOptions = DEFAULT_OPTIONS
    private var maxErrors = DEFAULT_MAX_ERRORS

    private val errors = ArrayList<DtsError>(16)
    private val dedupe = HashSet<ErrorKey>(16)

    init {
        val last = tokens.lastOrNull()
        eofToken = if (last?.type == TokenType.EOF) {
            last
        } else {
            Token(
                type = TokenType.EOF,
                value = "",
                line = last?.line ?: 1,
                col = last?.col ?: 1
            )
        }
        this.tokens = if (last?.type == TokenType.EOF) {
            tokens.toTypedArray()
        } else {
            (tokens + eofToken).toTypedArray()
        }
        this.len = this.tokens.size
    }

    /**
     * Returns lint diagnostics collected by the last [parse] call.
     */
    fun getLintResult(): DtsLintResult {
        return DtsLintResult(
            isValid = errors.none { it.severity == Severity.ERROR },
            errors = errors
        )
    }

    /**
     * Default parser entrypoint used by existing callers.
     */
    fun parse(): DtsNode = parse(DEFAULT_OPTIONS)

    /**
     * Fault-tolerant parse with optional cooperative cancellation and budgeting.
     *
     * Cancellation callback is allowed to throw [kotlinx.coroutines.CancellationException]
     * and is intentionally not caught here.
     */
    fun parse(options: ParseOptions): DtsNode {
        resetState(options)

        val root = DtsNode("root")
        root.isExpanded = true

        var iterations = 0
        val iterationLimit = (len * 6 + 2048).coerceAtLeast(4096)

        while (!stopParsing && !isAtEnd()) {
            checkpoint()
            if (stopParsing) break

            iterations++
            if (iterations > iterationLimit) {
                stopByBudget("Parser safeguard triggered; stopped parse early")
                break
            }

            val beforePos = pos
            when (current().type) {
                TokenType.EOF -> break
                TokenType.SEMICOLON -> consume() // stray semicolon
                TokenType.PREPROCESSOR -> {
                    if (current().value == "/") {
                        val node = parseNodeOrRef()
                        if (node != null) root.addChild(node)
                    } else {
                        consumeStatement()
                    }
                }
                TokenType.IDENTIFIER, TokenType.LABEL, TokenType.REF -> {
                    val node = parseNodeOrRef()
                    if (node != null) root.addChild(node)
                    else synchronize(RecoveryContext.TOP_LEVEL)
                }
                else -> {
                    report("Unexpected token '${current().value}' at top level", Severity.WARNING)
                    consume()
                }
            }
            ensureProgress(beforePos)
        }

        return root
    }

    private fun resetState(options: ParseOptions) {
        this.options = options
        this.maxErrors = options.maxErrors.coerceAtLeast(1)

        pos = 0
        consumedTokens = 0
        parseStartNs = System.nanoTime()
        nextCheckpointToken = 0
        hardTokenLimit = (len * 4 + 4096).coerceAtLeast(4096)
        stopParsing = false
        budgetWarningEmitted = false
        tooManyErrorsEmitted = false

        errors.clear()
        dedupe.clear()
    }

    // ---- Token helpers ------------------------------------------------------

    private fun current(): Token = tokenAt(pos)

    private fun tokenAt(index: Int): Token {
        if (index < 0 || index >= len) return eofToken
        return tokens[index]
    }

    private fun isAtEnd(): Boolean = current().type == TokenType.EOF

    private fun consume(): Token {
        val t = current()
        if (pos < len - 1) {
            pos++
            consumedTokens++
        }
        return t
    }

    private fun match(type: TokenType): Boolean {
        if (current().type != type) return false
        consume()
        return true
    }

    private fun expect(
        type: TokenType,
        message: String,
        recovery: RecoveryContext? = null,
        severity: Severity = Severity.ERROR
    ): Boolean {
        if (match(type)) return true
        report(message, severity)
        if (recovery != null && !stopParsing) synchronize(recovery)
        return false
    }

    private fun ensureProgress(beforePos: Int) {
        if (stopParsing || pos != beforePos) return
        if (!isAtEnd()) consume() else stopParsing = true
    }

    // ---- Cooperative cancellation + budget ---------------------------------

    private fun checkpoint() {
        val budget = options.budget
        val interval = (budget?.checkIntervalTokens ?: DEFAULT_CHECK_INTERVAL).coerceAtLeast(1)
        if (consumedTokens < nextCheckpointToken) return
        nextCheckpointToken = consumedTokens + interval

        options.checkCancelled?.invoke()

        if (consumedTokens > hardTokenLimit) {
            stopByBudget("Parser safeguard triggered; stopped parse early")
            return
        }

        if (budget != null) {
            if (consumedTokens >= budget.maxTokensConsumed.coerceAtLeast(1)) {
                stopByBudget("Token budget exceeded; stopped parse early")
                return
            }
            val timeBudgetMs = budget.timeBudgetMs
            if (timeBudgetMs != null && timeBudgetMs >= 0L) {
                val elapsedNs = System.nanoTime() - parseStartNs
                if (elapsedNs >= timeBudgetMs * 1_000_000L) {
                    stopByBudget("Time budget exceeded; stopped parse early")
                }
            }
        }
    }

    private fun stopByBudget(message: String) {
        stopParsing = true
        if (budgetWarningEmitted) return
        budgetWarningEmitted = true
        report(message, Severity.WARNING)
    }

    // ---- Diagnostics --------------------------------------------------------

    private fun report(message: String, severity: Severity = Severity.ERROR, token: Token = current()) {
        val line = (token.line - 1).coerceAtLeast(0)
        val column = (token.col - 1).coerceAtLeast(0)
        val key = ErrorKey(line, column, message, severity)
        if (!dedupe.add(key)) return

        if (errors.size >= maxErrors) {
            emitTooManyErrors(line, column)
            return
        }

        errors.add(
            DtsError(
                line = line,
                column = column,
                message = message,
                severity = severity
            )
        )
    }

    private fun emitTooManyErrors(line: Int, column: Int) {
        stopParsing = true
        if (tooManyErrorsEmitted) return
        tooManyErrorsEmitted = true

        val message = "Too many errors, stopping parse early"
        val key = ErrorKey(line, column, message, Severity.WARNING)
        if (dedupe.add(key)) {
            errors.add(
                DtsError(
                    line = line,
                    column = column,
                    message = message,
                    severity = Severity.WARNING
                )
            )
        }
    }

    // ---- Node/property parsing ---------------------------------------------

    private fun parseNodeOrRef(): DtsNode? {
        checkpoint()
        var label: String? = null
        if (current().type == TokenType.LABEL) {
            label = consume().value
        }

        val nameToken = current()
        val nodeName = when (nameToken.type) {
            TokenType.IDENTIFIER, TokenType.REF -> {
                consume()
                nameToken.value
            }
            TokenType.PREPROCESSOR -> {
                if (nameToken.value == "/") {
                    consume()
                    "/"
                } else if (isDirectiveToken(nameToken.value)) {
                    consumeStatement()
                    return null
                } else {
                    report("Expected node name or reference, got '${nameToken.value}'")
                    return null
                }
            }
            else -> {
                report("Expected node name or reference, got '${nameToken.value}'")
                return null
            }
        }

        val effectiveName = if (label != null) "$label $nodeName" else nodeName

        if (match(TokenType.LBRACE)) {
            return parseNodeBody(effectiveName)
        }

        if (recoverOpeningBraceWithinBound()) {
            report("Missing '{' after node name '$effectiveName' (recovered)", Severity.WARNING)
            expect(
                type = TokenType.LBRACE,
                message = "Expected '{' after node name '$effectiveName'",
                recovery = RecoveryContext.NODE,
                severity = Severity.WARNING
            )
            return parseNodeBody(effectiveName)
        }

        val incompleteNode = DtsNode(effectiveName)
        val severity = if (current().type == TokenType.EOF || current().type == TokenType.RBRACE) {
            Severity.WARNING
        } else {
            Severity.ERROR
        }
        report("Incomplete node '$effectiveName': expected '{'", severity)
        if (current().type == TokenType.SEMICOLON) {
            consume()
        } else if (severity == Severity.ERROR) {
            synchronize(RecoveryContext.NODE)
        }
        return incompleteNode
    }

    private fun parseNodeBody(nodeName: String): DtsNode {
        val node = DtsNode(nodeName)

        var iterations = 0
        val iterationLimit = (len * 4 + 1024).coerceAtLeast(2048)

        while (!stopParsing && !isAtEnd()) {
            checkpoint()
            if (stopParsing) break

            if (current().type == TokenType.RBRACE) break

            iterations++
            if (iterations > iterationLimit) {
                stopByBudget("Parser safeguard triggered in node '$nodeName'; stopped parse early")
                break
            }

            val beforePos = pos
            val token = current()
            when (token.type) {
                TokenType.SEMICOLON -> consume() // stray semicolon
                TokenType.PREPROCESSOR -> {
                    if (isDirectiveToken(token.value)) {
                        consumeStatement()
                    } else {
                        parseNodeElement(node)
                    }
                }
                TokenType.IDENTIFIER, TokenType.LABEL, TokenType.REF -> parseNodeElement(node)
                else -> {
                    report("Unexpected token '${token.value}' inside node '$nodeName'", Severity.WARNING)
                    consume()
                }
            }
            ensureProgress(beforePos)
        }

        if (match(TokenType.RBRACE)) {
            match(TokenType.SEMICOLON)
            return node
        }

        val severity = if (isAtEnd() || stopParsing) Severity.WARNING else Severity.ERROR
        report("Missing closing '}' for node '$nodeName'", severity)
        match(TokenType.SEMICOLON)
        return node
    }

    private fun parseNodeElement(node: DtsNode) {
        if (isNextNodeStart()) {
            val child = parseNodeOrRef()
            if (child != null) {
                node.addChild(child)
            } else {
                synchronize(RecoveryContext.NODE)
            }
            return
        }

        val property = parseProperty()
        if (property != null) {
            node.addProperty(property)
        }
    }

    private fun parseProperty(): DtsProperty? {
        val nameToken = current()
        if (nameToken.type != TokenType.IDENTIFIER && nameToken.type != TokenType.PREPROCESSOR) {
            report("Expected property name, got '${nameToken.value}'")
            if (!isAtEnd()) consume()
            return null
        }
        consume()

        val name = nameToken.value
        if (match(TokenType.SEMICOLON)) {
            return DtsProperty(name, "")
        }

        if (!match(TokenType.EQUALS)) {
            val severity = if (current().type == TokenType.EOF || current().type == TokenType.RBRACE) {
                Severity.WARNING
            } else {
                Severity.ERROR
            }
            report("Expected '=' or ';' after property name '$name', got '${current().value}'", severity)
            if (severity == Severity.ERROR) synchronize(RecoveryContext.PROPERTY)
            return DtsProperty(name, "")
        }

        val value = parseValue()
        if (match(TokenType.SEMICOLON)) {
            return DtsProperty(name, value)
        }

        val incomplete = current().type == TokenType.EOF ||
                current().type == TokenType.RBRACE ||
                isLikelyStatementStartAt(pos)
        report("Missing ';' after property '$name'", if (incomplete) Severity.WARNING else Severity.ERROR)
        recoverAfterMissingSemicolon()
        return DtsProperty(name, value)
    }

    private fun parseValue(): String {
        val sb = StringBuilder()
        var parts = 0

        while (!stopParsing && !isAtEnd()) {
            checkpoint()
            if (stopParsing) break

            val token = current()
            when (token.type) {
                TokenType.SEMICOLON, TokenType.RBRACE, TokenType.EOF -> break
                TokenType.LANGLE -> appendValuePart(sb, parseArray())
                TokenType.LBRACKET -> appendValuePart(sb, parseByteArray())
                TokenType.STRING_LITERAL -> {
                    appendValuePart(sb, "\"${token.value}\"")
                    consume()
                }
                else -> {
                    appendValuePart(sb, token.value)
                    consume()
                }
            }

            parts++
            if (parts > MAX_VALUE_TOKENS) {
                report("Property value too long; truncated for recovery", Severity.WARNING)
                synchronize(RecoveryContext.PROPERTY)
                break
            }
        }
        return sb.toString().trim()
    }

    private fun parseArray(): String {
        val sb = StringBuilder("<")
        consume() // <

        var appended = 0
        while (!stopParsing && !isAtEnd()) {
            checkpoint()
            if (stopParsing) break

            val token = current()
            if (token.type == TokenType.RANGLE ||
                token.type == TokenType.EOF ||
                token.type == TokenType.SEMICOLON ||
                token.type == TokenType.RBRACE
            ) {
                break
            }
            if (appended > 0) sb.append(' ')
            if (token.type == TokenType.STRING_LITERAL) {
                sb.append('"').append(token.value).append('"')
            } else {
                sb.append(token.value)
            }
            consume()
            appended++

            if (appended > MAX_VALUE_TOKENS) {
                report("Array value too long; truncated for recovery", Severity.WARNING)
                break
            }
        }

        if (!match(TokenType.RANGLE)) {
            report("Missing closing '>' in array value", Severity.WARNING)
        }
        sb.append('>')
        return sb.toString()
    }

    private fun parseByteArray(): String {
        val sb = StringBuilder("[")
        consume() // [

        var appended = 0
        while (!stopParsing && !isAtEnd()) {
            checkpoint()
            if (stopParsing) break

            val token = current()
            if (token.type == TokenType.RBRACKET ||
                token.type == TokenType.EOF ||
                token.type == TokenType.SEMICOLON ||
                token.type == TokenType.RBRACE
            ) {
                break
            }
            if (appended > 0) sb.append(' ')
            sb.append(token.value)
            consume()
            appended++

            if (appended > MAX_VALUE_TOKENS) {
                report("Byte-array value too long; truncated for recovery", Severity.WARNING)
                break
            }
        }

        if (!match(TokenType.RBRACKET)) {
            report("Missing closing ']' in byte-array value", Severity.WARNING)
        }
        sb.append(']')
        return sb.toString()
    }

    // ---- Recovery -----------------------------------------------------------

    private fun recoverAfterMissingSemicolon() {
        when (current().type) {
            TokenType.SEMICOLON -> consume()
            TokenType.RBRACE, TokenType.EOF -> return
            else -> {
                if (!isLikelyStatementStartAt(pos)) {
                    synchronize(RecoveryContext.PROPERTY)
                }
            }
        }
    }

    private fun synchronize(context: RecoveryContext) {
        if (isAtEnd()) return
        if (tryBoundedSynchronize(context)) return

        var skipped = 0
        while (!stopParsing && !isAtEnd() && skipped < MAX_RECOVERY_SKIP) {
            checkpoint()
            if (stopParsing) return

            when (current().type) {
                TokenType.EOF -> return
                TokenType.SEMICOLON -> {
                    consume()
                    return
                }
                TokenType.RBRACE -> {
                    if (context == RecoveryContext.TOP_LEVEL) consume()
                    return
                }
                TokenType.LBRACE -> {
                    if (context != RecoveryContext.PROPERTY) return
                    consume()
                    skipped++
                }
                else -> {
                    if (skipped > 0 && isLikelyStatementStartAt(pos)) return
                    consume()
                    skipped++
                }
            }
        }
    }

    private fun tryBoundedSynchronize(context: RecoveryContext): Boolean {
        val limit = minOf(pos + MAX_RECOVERY_LOOKAHEAD, len - 1)
        var i = pos
        while (i <= limit) {
            val token = tokenAt(i)
            when (token.type) {
                TokenType.EOF, TokenType.RBRACE -> {
                    pos = i
                    return true
                }
                TokenType.SEMICOLON -> {
                    pos = (i + 1).coerceAtMost(len - 1)
                    return true
                }
                TokenType.LBRACE -> {
                    if (context != RecoveryContext.PROPERTY) {
                        pos = i
                        return true
                    }
                }
                else -> {
                    if (i > pos && isLikelyStatementStartAt(i)) {
                        pos = i
                        return true
                    }
                }
            }
            i++
        }
        return false
    }

    private fun recoverOpeningBraceWithinBound(): Boolean {
        val limit = minOf(pos + MISSING_BRACE_LOOKAHEAD, len - 1)
        var i = pos
        while (i <= limit) {
            when (tokenAt(i).type) {
                TokenType.LBRACE -> {
                    pos = i
                    return true
                }
                TokenType.SEMICOLON, TokenType.RBRACE, TokenType.EOF, TokenType.EQUALS -> return false
                else -> i++
            }
        }
        return false
    }

    private fun consumeStatement() {
        val startLine = current().line
        var skipped = 0

        while (!stopParsing && !isAtEnd() && skipped < MAX_RECOVERY_SKIP) {
            checkpoint()
            if (stopParsing) return

            when (current().type) {
                TokenType.SEMICOLON -> {
                    consume()
                    return
                }
                TokenType.EOF, TokenType.RBRACE -> return
                else -> {
                    if (skipped > 0 && current().line != startLine && isLikelyStatementStartAt(pos)) {
                        return
                    }
                    consume()
                    skipped++
                }
            }
        }
    }

    private fun isLikelyStatementStartAt(index: Int): Boolean {
        val t0 = tokenAt(index)
        when (t0.type) {
            TokenType.EOF, TokenType.RBRACE, TokenType.SEMICOLON -> return true
            TokenType.PREPROCESSOR -> {
                if (t0.value == "/") return true
                return isDirectiveToken(t0.value)
            }
            TokenType.LABEL -> {
                val t1 = tokenAt(index + 1)
                return t1.type == TokenType.IDENTIFIER ||
                        t1.type == TokenType.REF ||
                        (t1.type == TokenType.PREPROCESSOR && t1.value == "/")
            }
            TokenType.IDENTIFIER, TokenType.REF -> {
                val t1 = tokenAt(index + 1)
                if (t1.type == TokenType.EQUALS || t1.type == TokenType.SEMICOLON || t1.type == TokenType.LBRACE) {
                    return true
                }
                if (t1.type == TokenType.IDENTIFIER || t1.type == TokenType.REF || t1.type == TokenType.PREPROCESSOR) {
                    val t2 = tokenAt(index + 2)
                    if (t2.type == TokenType.LBRACE) return true
                }
            }
            else -> Unit
        }
        return false
    }

    private fun isNextNodeStart(): Boolean {
        var i = pos
        val hasLabel = tokenAt(i).type == TokenType.LABEL
        if (hasLabel) i++

        val name = tokenAt(i)
        if (name.type != TokenType.IDENTIFIER &&
            name.type != TokenType.REF &&
            !(name.type == TokenType.PREPROCESSOR && name.value == "/")
        ) {
            return false
        }
        i++

        val limit = minOf(i + MAX_LOOKAHEAD, len - 1)
        while (i <= limit) {
            when (tokenAt(i).type) {
                TokenType.LBRACE -> return true
                TokenType.EQUALS, TokenType.SEMICOLON -> return false
                TokenType.RBRACE, TokenType.EOF -> {
                    // Typing-in-progress heuristic for partial node headers.
                    return hasLabel || name.type == TokenType.REF || name.value == "/" || name.value.contains("@")
                }
                else -> i++
            }
        }
        return false
    }

    private fun isDirectiveToken(value: String): Boolean {
        return value.startsWith("#include") ||
                value.startsWith("/dts-v1/") ||
                value.startsWith("/plugin/") ||
                value.startsWith("/memreserve/") ||
                value.startsWith("/delete-node/") ||
                value.startsWith("/delete-property/") ||
                value.startsWith("/omit-if-no-ref/")
    }

    private fun appendValuePart(sb: StringBuilder, part: String) {
        if (part.isEmpty()) return
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(part)
    }

    companion object {
        private const val DEFAULT_CHECK_INTERVAL = 1024
        private const val DEFAULT_MAX_ERRORS = 200
        private const val MAX_LOOKAHEAD = 8
        private const val MISSING_BRACE_LOOKAHEAD = 16
        private const val MAX_RECOVERY_LOOKAHEAD = 48
        private const val MAX_RECOVERY_SKIP = 512
        private const val MAX_VALUE_TOKENS = 4096

        private val DEFAULT_OPTIONS = ParseOptions()
    }
}
