package com.ireddragonicy.konabessnext.utils

enum class TokenType {
    LBRACE, RBRACE, SEMICOLON, EQUALS,
    LANGLE, RANGLE, // < and >
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

class DtsLexer(private val input: String) {
    private val len = input.length
    private var pos = 0
    private var line = 1
    private var col = 1

    private fun peek(): Char = if (pos < len) input[pos] else '\u0000'
    private fun peekNext(): Char = if (pos + 1 < len) input[pos + 1] else '\u0000'

    private fun advance(): Char {
        if (pos >= len) return '\u0000'
        val c = input[pos++]
        if (c == '\n') {
            line++
            col = 1
        } else {
            col++
        }
        return c
    }

    private fun skipWhitespace() {
        while (pos < len) {
            val c = peek()
            if (c.isWhitespace()) {
                advance()
            } else if (c == '/' && peekNext() == '*') {
                // Multi-line comment
                advance() // /
                advance() // *
                while (pos < len) {
                    if (peek() == '*' && peekNext() == '/') {
                        advance()
                        advance()
                        break
                    }
                    advance()
                }
            } else if (c == '/' && peekNext() == '/') {
                // Single line comment
                while (pos < len && peek() != '\n') {
                    advance()
                }
            } else {
                break
            }
        }
    }

    fun tokenize(): List<Token> {
        val tokens = ArrayList<Token>()
        while (pos < len) {
            skipWhitespace()
            if (pos >= len) break

            val startLine = line
            val startCol = col
            val c = peek()

            when {
                c == '{' -> { advance(); tokens.add(Token(TokenType.LBRACE, "{", startLine, startCol)) }
                c == '}' -> { advance(); tokens.add(Token(TokenType.RBRACE, "}", startLine, startCol)) }
                c == ';' -> { advance(); tokens.add(Token(TokenType.SEMICOLON, ";", startLine, startCol)) }
                c == '=' -> { advance(); tokens.add(Token(TokenType.EQUALS, "=", startLine, startCol)) }
                c == '<' -> { advance(); tokens.add(Token(TokenType.LANGLE, "<", startLine, startCol)) }
                c == '>' -> { advance(); tokens.add(Token(TokenType.RANGLE, ">", startLine, startCol)) }
                c == '"' -> { tokens.add(readString()) }
                c == '/' && isPreprocessorStart(c) -> { tokens.add(readPreprocessor()) } // /delete-node/ etc
                c == '#' -> { tokens.add(readPreprocessor()) } // #include
                c == '&' -> { tokens.add(readRef()) }
                isIdentifierStart(c) -> {
                    val ident = readIdentifier()
                    if (peek() == ':') {
                        advance() // consume ':'
                        tokens.add(Token(TokenType.LABEL, ident.value, startLine, startCol))
                    } else {
                        tokens.add(ident)
                    }
                }
                c.isDigit() -> { tokens.add(readNumber()) }
                else -> {
                    // Unknown char, skip or handle error?
                    // For robustness, treat as part of identifier or single char
                     advance()
                }
            }
        }
        tokens.add(Token(TokenType.EOF, "", line, col))
        return tokens
    }

    private fun isPreprocessorStart(c: Char): Boolean {
        // DTS uses /delete-node/, /memreserve/ etc.
        // Check if it looks like a preprocessor directive or hidden command, not a comment
        // Comments are handled in skipWhitespace.
        // Here we just check logic. But wait, skipWhitespace handles / followed by * or /.
        // So simply starting with / is likely a node path or command.
        // However, paths usually appear in identifiers.
        // /delete-node/ is special.
        return true
    }

    private fun readString(): Token {
        val startLine = line
        val startCol = col
        advance() // "
        val sb = StringBuilder()
        while (pos < len) {
            val c = peek()
            if (c == '"') {
                advance()
                break
            }
            if (c == '\\') {
                advance()
                val escaped = advance()
                // Handle basic escapes
                sb.append(when(escaped) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '\\' -> '\\'
                    '"' -> '"'
                    else -> escaped
                })
            } else {
                sb.append(advance())
            }
        }
        return Token(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol)
    }

    private fun readIdentifier(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        while (pos < len && isIdentifierChar(peek())) {
            sb.append(advance())
        }
        val value = sb.toString()
        // Check for specific keywords if needed, but DTS mostly uses them as identifiers
        return Token(TokenType.IDENTIFIER, value, startLine, startCol)
    }

    private fun readNumber(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        
        // Check hex
        if (peek() == '0' && (peekNext() == 'x' || peekNext() == 'X')) {
            sb.append(advance()) // 0
            sb.append(advance()) // x
            while (pos < len && isHexChar(peek())) {
                sb.append(advance())
            }
            return Token(TokenType.HEX_LITERAL, sb.toString(), startLine, startCol)
        } else {
            // Int
            while (pos < len && peek().isDigit()) {
                sb.append(advance())
            }
            return Token(TokenType.INT_LITERAL, sb.toString(), startLine, startCol)
        }
    }
    
    private fun readRef(): Token {
        val startLine = line
        val startCol = col
        advance() // &
        val sb = StringBuilder("&")
        // Ref can be &label or &{...}
        if (peek() == '{') {
            // &{ ... }
            // This is complex, usually a phandle ref to a full path.
            // For simple tokenizing, we might just consume until }?
            // Or treat & and { as separate? standard tokenizer says & is operator, { is Brace.
            // But in DTS &{/path/to/node} is a reference.
            // Let's treat it as a REF token containing the whole thing for now, or split it.
            // Requirement says: REF (references starting with &)
            
            // Simpler approach: If it starts with &, read until space or ; or >?
            // Actually, references are identifiers.
        }
        
        while (pos < len && isRefChar(peek())) {
            sb.append(advance())
        }
        return Token(TokenType.REF, sb.toString(), startLine, startCol)
    }

    private fun readPreprocessor(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        sb.append(advance()) // / or #
        while (pos < len) {
            val c = peek()
            if (c.isWhitespace() || c == ';' || c == '=' || c == '{' || c == '}' || c == '<' || c == '>') {
                break
            }
            sb.append(advance())
        }
        return Token(TokenType.PREPROCESSOR, sb.toString(), startLine, startCol)
    }

    private fun isIdentifierStart(c: Char): Boolean {
        return c.isLetter() || c == '_' || c == ',' || c == '.' || c == '+' || c == '-'
        // Note: property names can start with numbers? usually not.
        // Node names can start with numbers (e.g. 100mw-cp).
        // Let's allow digits too if we are loose, but standard identifier usually alpha.
        // DTS allows "node-name", "property-name", "compatible", "#address-cells"
    }

    private fun isIdentifierChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == ',' || c == '.' || c == '+' || c == '-' || c == '@' || c == '#'
    }
    
    private fun isHexChar(c: Char): Boolean {
        return c.isDigit() || (c in 'a'..'f') || (c in 'A'..'F')
    }
    
    private fun isRefChar(c: Char): Boolean {
         return isIdentifierChar(c) || c == '{' || c == '}' || c == '/' // For &{/path/to/node}
    }
}
