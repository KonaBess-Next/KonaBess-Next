package com.ireddragonicy.konabessnext.utils

enum class TokenType {
    LBRACE, RBRACE, SEMICOLON, EQUALS,
    LANGLE, RANGLE, // < and >
    LBRACKET, RBRACKET, // [ and ] byte arrays
    IDENTIFIER, // node_name, property-name
    STRING_LITERAL, // "..."
    HEX_LITERAL, // 0x...
    INT_LITERAL, // 123
    REF, // &label or &{...}
    PREPROCESSOR, // #include, /delete-node/, /memreserve/
    LABEL, // label:
    EOF
}

data class Token(val type: TokenType, val value: String, val line: Int, val col: Int)

/**
 * High-performance DTS lexer optimised for large files (24K+ lines).
 *
 * Key optimisations vs. the original implementation:
 *  - Substring-based value extraction instead of per-char StringBuilder.
 *  - Inline character classification (no isLetter()/isDigit() boxing).
 *  - Pre-allocated ArrayList with estimated capacity.
 *  - Correct `/` disambiguation (preprocessor vs root-node name).
 */
class DtsLexer(private val input: String) {
    private val len = input.length
    private var pos = 0
    private var line = 1
    private var col = 1

    // ---- Low-level helpers --------------------------------------------------

    @Suppress("NOTHING_TO_INLINE")
    private inline fun peek(): Char = if (pos < len) input[pos] else '\u0000'

    @Suppress("NOTHING_TO_INLINE")
    private inline fun peekAt(offset: Int): Char {
        val idx = pos + offset
        return if (idx < len) input[idx] else '\u0000'
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun advance(): Char {
        if (pos >= len) return '\u0000'
        val c = input[pos++]
        if (c == '\n') { line++; col = 1 } else { col++ }
        return c
    }

    /** Advance `count` characters, updating line/col. */
    private fun skip(count: Int) {
        var remaining = count
        while (remaining > 0 && pos < len) {
            val c = input[pos++]
            if (c == '\n') { line++; col = 1 } else { col++ }
            remaining--
        }
    }

    // ---- Whitespace / comment skipping (hot-path) ---------------------------

    private fun skipWhitespace() {
        while (pos < len) {
            val c = input[pos]
            when {
                c == ' ' || c == '\t' || c == '\r' -> { pos++; col++ }
                c == '\n' -> { pos++; line++; col = 1 }
                c == '/' && pos + 1 < len && input[pos + 1] == '*' -> skipBlockComment()
                c == '/' && pos + 1 < len && input[pos + 1] == '/' -> skipLineComment()
                else -> return
            }
        }
    }

    private fun skipBlockComment() {
        skip(2) // /*
        while (pos < len) {
            if (input[pos] == '*' && pos + 1 < len && input[pos + 1] == '/') {
                skip(2)
                return
            }
            if (input[pos] == '\n') { line++; col = 1 } else { col++ }
            pos++
        }
    }

    private fun skipLineComment() {
        pos += 2; col += 2
        while (pos < len && input[pos] != '\n') { pos++; col++ }
    }

    // ---- Tokenisation -------------------------------------------------------

    fun tokenize(): List<Token> {
        // Rough estimate: one token every ~6 chars gives a good initial capacity
        val tokens = ArrayList<Token>(len / 6 + 16)

        while (pos < len) {
            skipWhitespace()
            if (pos >= len) break

            val startLine = line
            val startCol = col
            val c = input[pos]

            when {
                c == '{' -> { advance(); tokens.add(Token(TokenType.LBRACE, "{", startLine, startCol)) }
                c == '}' -> { advance(); tokens.add(Token(TokenType.RBRACE, "}", startLine, startCol)) }
                c == ';' -> { advance(); tokens.add(Token(TokenType.SEMICOLON, ";", startLine, startCol)) }
                c == '=' -> { advance(); tokens.add(Token(TokenType.EQUALS, "=", startLine, startCol)) }
                c == '<' -> { advance(); tokens.add(Token(TokenType.LANGLE, "<", startLine, startCol)) }
                c == '>' -> { advance(); tokens.add(Token(TokenType.RANGLE, ">", startLine, startCol)) }
                c == '[' -> { advance(); tokens.add(Token(TokenType.LBRACKET, "[", startLine, startCol)) }
                c == ']' -> { advance(); tokens.add(Token(TokenType.RBRACKET, "]", startLine, startCol)) }
                c == '"' -> tokens.add(readString(startLine, startCol))
                c == '/' -> {
                    // Disambiguate: preprocessor directive (/dts-v1/, /delete-node/, …) vs root node name "/"
                    val next = peekAt(1)
                    if (next == '*' || next == '/') {
                        // Comment — already handled, but just in case
                        skipWhitespace()
                    } else if (next.isLetter() || next == 'd' || next == 'm' || next == 'o' || next == 'p' || next == 'i') {
                        // Likely a preprocessor directive like /dts-v1/, /delete-node/, etc.
                        tokens.add(readPreprocessor(startLine, startCol))
                    } else {
                        // Bare "/" — root node name. Emit as PREPROCESSOR for parser compat.
                        advance()
                        tokens.add(Token(TokenType.PREPROCESSOR, "/", startLine, startCol))
                    }
                }
                c == '#' -> tokens.add(readPreprocessor(startLine, startCol))
                c == '&' -> tokens.add(readRef(startLine, startCol))
                isIdentStart(c) -> {
                    val ident = readIdentifierFast(startLine, startCol)
                    if (pos < len && input[pos] == ':') {
                        advance() // consume ':'
                        tokens.add(Token(TokenType.LABEL, ident.value, startLine, startCol))
                    } else {
                        tokens.add(ident)
                    }
                }
                c in '0'..'9' -> tokens.add(readNumber(startLine, startCol))
                else -> advance() // skip unknown
            }
        }
        tokens.add(Token(TokenType.EOF, "", line, col))
        return tokens
    }

    // ---- Token readers (substring-based for speed) --------------------------

    private fun readString(startLine: Int, startCol: Int): Token {
        advance() // opening "
        val startPos = pos
        var hasEscape = false
        while (pos < len) {
            val c = input[pos]
            if (c == '"') { break }
            if (c == '\\') { hasEscape = true; skip(2); continue }
            if (c == '\n') { line++; col = 1 } else { col++ }
            pos++
        }
        val raw = input.substring(startPos, pos)
        if (pos < len) advance() // closing "

        // Fast path: no escapes → raw substring is the value
        val value = if (!hasEscape) raw else unescapeString(raw)
        return Token(TokenType.STRING_LITERAL, value, startLine, startCol)
    }

    private fun unescapeString(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                i++
                sb.append(when (s[i]) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; '\\' -> '\\'; '"' -> '"'; else -> s[i] })
            } else {
                sb.append(s[i])
            }
            i++
        }
        return sb.toString()
    }

    private fun readIdentifierFast(startLine: Int, startCol: Int): Token {
        val startPos = pos
        while (pos < len && isIdentChar(input[pos])) { pos++; col++ }
        return Token(TokenType.IDENTIFIER, input.substring(startPos, pos), startLine, startCol)
    }

    private fun readNumber(startLine: Int, startCol: Int): Token {
        val startPos = pos
        if (input[pos] == '0' && pos + 1 < len && (input[pos + 1] == 'x' || input[pos + 1] == 'X')) {
            pos += 2; col += 2
            while (pos < len && isHex(input[pos])) { pos++; col++ }
            return Token(TokenType.HEX_LITERAL, input.substring(startPos, pos), startLine, startCol)
        }
        while (pos < len && input[pos] in '0'..'9') { pos++; col++ }

        // DTS byte arrays frequently use hex bytes without 0x prefix (e.g. [1d 1d]).
        // Preserve those as a single hex token instead of splitting into "1" and "d".
        val suffixStart = pos
        while (pos < len && (input[pos] in 'a'..'f' || input[pos] in 'A'..'F')) { pos++; col++ }
        if (pos > suffixStart) {
            return Token(TokenType.HEX_LITERAL, input.substring(startPos, pos), startLine, startCol)
        }

        return Token(TokenType.INT_LITERAL, input.substring(startPos, pos), startLine, startCol)
    }

    private fun readRef(startLine: Int, startCol: Int): Token {
        val startPos = pos
        advance() // &
        while (pos < len && isRefChar(input[pos])) { pos++; col++ }
        return Token(TokenType.REF, input.substring(startPos, pos), startLine, startCol)
    }

    private fun readPreprocessor(startLine: Int, startCol: Int): Token {
        val startPos = pos
        advance() // / or #
        while (pos < len) {
            val c = input[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
                c == ';' || c == '=' || c == '{' || c == '}' || c == '<' || c == '>') break
            pos++; col++
        }
        return Token(TokenType.PREPROCESSOR, input.substring(startPos, pos), startLine, startCol)
    }

    // ---- Character classification (inlined for hot loops) -------------------

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isIdentStart(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == ',' || c == '.' || c == '+' || c == '-'

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isIdentChar(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == ',' || c == '.' || c == '+' || c == '-' || c == '@' || c == '#'

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isHex(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isRefChar(c: Char): Boolean =
        isIdentChar(c) || c == '{' || c == '}' || c == '/'
}
