package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsError
import com.ireddragonicy.konabessnext.model.dts.DtsLintResult
import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import com.ireddragonicy.konabessnext.model.dts.Severity

/**
 * High-performance recursive-descent DTS parser with error recovery.
 *
 * Key optimisations:
 *  - Array-based token access (no list indirection).
 *  - Bounded lookahead in [isNextNodeStart] — max 4 tokens instead of unbounded O(n).
 *  - Minimal object allocation in the hot path.
 */
class DtsParser(tokens: List<Token>) {
    // Store as array for faster indexed access (no virtual dispatch)
    private val tokens: Array<Token> = tokens.toTypedArray()
    private var pos = 0
    private val len = this.tokens.size

    // Error-recovery: collect errors instead of throwing
    private val errors = ArrayList<DtsError>(8)

    /**
     * Returns the lint result after parsing.
     * Call after [parse] to retrieve accumulated errors.
     */
    fun getLintResult(): DtsLintResult {
        return DtsLintResult(
            isValid = errors.none { it.severity == Severity.ERROR },
            errors = errors
        )
    }

    /**
     * Records a diagnostic and optionally attempts to recover by skipping
     * to the next synchronisation point (; or }).
     */
    private fun recordError(message: String, severity: Severity = Severity.ERROR, recover: Boolean = true) {
        val token = peek()
        errors.add(
            DtsError(
                line = token.line - 1,   // Convert to 0-based
                column = token.col - 1,  // Convert to 0-based
                message = message,
                severity = severity
            )
        )
        if (recover) synchronize()
    }

    /**
     * Skip tokens until a synchronisation point (`;` or `}`) is found,
     * then resume normal parsing.
     */
    private fun synchronize() {
        while (pos < len) {
            val t = tokens[pos]
            if (t.type == TokenType.SEMICOLON) { pos++; return }
            if (t.type == TokenType.RBRACE) return      // Don't consume — let caller handle
            if (t.type == TokenType.EOF) return
            pos++
        }
    }

    fun parse(): DtsNode {
        errors.clear()
        val root = DtsNode("root")
        root.isExpanded = true

        while (pos < len) {
            val token = tokens[pos]
            if (token.type == TokenType.EOF) break

            if (token.type == TokenType.PREPROCESSOR) {
                if (token.value == "/") {
                    // Root node / { … };
                    val node = parseNodeOrRef()
                    if (node != null) root.addChild(node) else pos++
                } else {
                    // /dts-v1/;  /memreserve/ …;  etc.
                    consumeStatement()
                }
            } else if (token.type == TokenType.SEMICOLON) {
                pos++  // stray semicolon
            } else {
                val node = parseNodeOrRef()
                if (node != null) root.addChild(node)
                else recordError("Unexpected token '${token.value}' at top level")
            }
        }
        return root
    }

    // ---- Inline helpers (hot path) ------------------------------------------

    @Suppress("NOTHING_TO_INLINE")
    private inline fun peek(): Token = if (pos < len) tokens[pos] else tokens[len - 1]

    @Suppress("NOTHING_TO_INLINE")
    private inline fun advance(): Token {
        val t = tokens[pos]
        if (pos < len) pos++
        return t
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun match(type: TokenType): Boolean {
        if (pos < len && tokens[pos].type == type) { pos++; return true }
        return false
    }

    // ---- Node / property parsing -------------------------------------------

    private fun parseNodeOrRef(): DtsNode? {
        var label: String? = null
        var nameToken = peek()

        // Handle Labels
        if (nameToken.type == TokenType.LABEL) {
            label = nameToken.value
            advance()
            nameToken = peek()
        }

        var nodeName = ""
        if (match(TokenType.IDENTIFIER)) {
            nodeName = nameToken.value
        } else if (match(TokenType.REF)) {
            nodeName = nameToken.value
        } else if (match(TokenType.PREPROCESSOR)) {
            if (nameToken.value == "/") {
                nodeName = "/"
            } else {
                if (nameToken.value == "/delete-node/") {
                    if (peek().type == TokenType.IDENTIFIER) advance()
                    if (peek().type == TokenType.SEMICOLON) advance()
                    return null
                }
                consumeStatement()
                return null
            }
        } else {
            recordError("Expected node name or reference, got '${nameToken.value}'")
            return null
        }

        if (match(TokenType.LBRACE)) {
            val node = DtsNode(nodeName)
            if (label != null) node.name = "$label $nodeName"

            while (pos < len) {
                val current = tokens[pos]
                if (current.type == TokenType.RBRACE || current.type == TokenType.EOF) break
                val beforePos = pos

                val isDirective = current.type == TokenType.PREPROCESSOR &&
                        (current.value == "#include" ||
                                current.value.startsWith("/delete-node") ||
                                current.value.startsWith("/omit-if-no-ref") ||
                                current.value.startsWith("/memreserve"))

                if (isDirective) {
                    consumeStatement()
                } else if (current.type == TokenType.IDENTIFIER ||
                    current.type == TokenType.LABEL ||
                    current.type == TokenType.REF ||
                    current.type == TokenType.PREPROCESSOR
                ) {
                    if (isNextNodeStart()) {
                        val child = parseNodeOrRef()
                        if (child != null) node.addChild(child)
                    } else {
                        val prop = parseProperty()
                        if (prop != null) node.addProperty(prop)
                    }
                } else {
                    recordError("Unexpected token '${current.value}' inside node '${node.name}'", Severity.WARNING, recover = false)
                    advance()
                }

                if (pos == beforePos) advance()  // infinite-loop guard
            }

            if (!match(TokenType.RBRACE)) {
                recordError("Missing closing '}' for node '${node.name}'", recover = false)
            }
            match(TokenType.SEMICOLON)
            return node
        } else {
            recordError("Expected '{' after node name '${nodeName}', got '${peek().value}'")
            return null
        }
    }

    /**
     * Bounded lookahead — checks whether the current element is a child node
     * (has `{` before `=` or `;`). Looks at most [MAX_LOOKAHEAD] tokens ahead.
     *
     * **Critical optimisation**: the original implementation scanned to the end
     * of the token list (O(n) per call → O(n²) total). In valid DTS, `{`, `=`,
     * or `;` always appears within 3 tokens of the element name, so a small
     * constant bound is correct and fast.
     */
    private fun isNextNodeStart(): Boolean {
        val limit = minOf(pos + MAX_LOOKAHEAD, len)
        var i = pos
        while (i < limit) {
            when (tokens[i].type) {
                TokenType.LBRACE -> return true
                TokenType.EQUALS, TokenType.SEMICOLON, TokenType.RBRACE -> return false
                else -> i++
            }
        }
        return false
    }

    private fun parseProperty(): DtsProperty? {
        val nameToken = peek()
        if (!match(TokenType.IDENTIFIER) && !match(TokenType.PREPROCESSOR)) {
            recordError("Expected property name, got '${nameToken.value}'")
            return null
        }

        val name = nameToken.value

        if (match(TokenType.SEMICOLON)) return DtsProperty(name, "")

        if (match(TokenType.EQUALS)) {
            val value = parseValue()
            if (!match(TokenType.SEMICOLON)) {
                recordError("Missing ';' after property '$name'", Severity.WARNING, recover = false)
            }
            return DtsProperty(name, value)
        }

        recordError("Expected '=' or ';' after property name '$name', got '${peek().value}'")
        return null
    }

    private fun parseValue(): String {
        val sb = StringBuilder()
        while (pos < len) {
            val t = tokens[pos]
            when (t.type) {
                TokenType.SEMICOLON, TokenType.RBRACE, TokenType.EOF -> break
                TokenType.STRING_LITERAL -> { sb.append('"').append(t.value).append('"'); pos++ }
                TokenType.LANGLE -> sb.append(parseArray())
                TokenType.LBRACKET -> sb.append(parseByteArray())
                TokenType.REF -> { sb.append(t.value); pos++ }
                TokenType.HEX_LITERAL, TokenType.INT_LITERAL, TokenType.IDENTIFIER -> { sb.append(t.value); pos++ }
                TokenType.EQUALS -> { sb.append('='); pos++ }
                else -> { sb.append(t.value); pos++ }
            }
            // Space between value tokens (but not before terminator or >)
            if (pos < len) {
                val next = tokens[pos].type
                if (next != TokenType.SEMICOLON && next != TokenType.EOF && next != TokenType.RANGLE) {
                    sb.append(' ')
                }
            }
        }
        return sb.toString().trim()
    }

    private fun parseArray(): String {
        val sb = StringBuilder("<")
        pos++ // <
        while (pos < len && tokens[pos].type != TokenType.RANGLE && tokens[pos].type != TokenType.EOF) {
            sb.append(tokens[pos].value)
            pos++
            if (pos < len && tokens[pos].type != TokenType.RANGLE) sb.append(' ')
        }
        match(TokenType.RANGLE)
        sb.append('>')
        return sb.toString()
    }

    private fun parseByteArray(): String {
        val sb = StringBuilder("[")
        pos++ // [
        while (pos < len && tokens[pos].type != TokenType.RBRACKET && tokens[pos].type != TokenType.EOF) {
            sb.append(tokens[pos].value)
            pos++
            if (pos < len && tokens[pos].type != TokenType.RBRACKET) sb.append(' ')
        }
        match(TokenType.RBRACKET)
        sb.append(']')
        return sb.toString()
    }

    private fun consumeStatement() {
        while (pos < len && tokens[pos].type != TokenType.SEMICOLON && tokens[pos].type != TokenType.EOF) {
            pos++
        }
        if (pos < len && tokens[pos].type == TokenType.SEMICOLON) pos++
    }

    companion object {
        /**
         * Maximum number of tokens to look ahead when deciding whether
         * the next element is a child node or a property. In valid DTS the
         * answer is always within 3–4 tokens (name [label:] {).
         */
        private const val MAX_LOOKAHEAD = 6
    }
}
