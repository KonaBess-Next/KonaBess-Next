package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.DtsNode
import com.ireddragonicy.konabessnext.model.dts.DtsProperty
import java.util.Stack

class DtsParser(private val tokens: List<Token>) {
    private var pos = 0
    private val len = tokens.size

    fun parse(): DtsNode {
        val root = DtsNode("root")
        root.isExpanded = true
        // System.out.println("Starting parse. Tokens: " + tokens.size)
        
        while (pos < len) {
            val token = peek()
            // System.out.println("Top level token: " + token.type + " " + token.value)
            if (token.type == TokenType.EOF) break

            // Top level elements: usually /dts-v1/; or /memreserve/; or root node / { ... };
            // or &node_ref { ... };
            
            if (token.type == TokenType.PREPROCESSOR) {
                if (token.value == "/") {
                     // This is likely the root node / { ... };
                     val node = parseNodeOrRef()
                     if (node != null) {
                         root.addChild(node)
                     } else {
                         advance()
                     }
                } else {
                    // Ignore other preprocessors like /dts-v1/; or /memreserve/;
                    advance()
                }
            } else if (token.type == TokenType.SEMICOLON) {
                 // Stray semicolon?
                 advance()
            } else {
                // Try parse node
                val node = parseNodeOrRef()
                if (node != null) {
                    root.addChild(node)
                } else {
                    // Unexpected token at top level
                    advance() 
                }
            }
        }
        return root
    }

    private fun peek(): Token = if (pos < len) tokens[pos] else tokens[len - 1]
    private fun peekNext(): Token = if (pos + 1 < len) tokens[pos + 1] else tokens[len - 1]
    
    private fun advance(): Token {
        if (pos < len) pos++
        return if (pos > 0) tokens[pos - 1] else tokens[0] // Return consumed token
    }

    private fun match(type: TokenType): Boolean {
        if (peek().type == type) {
            advance()
            return true
        }
        return false
    }

    private fun parseNodeOrRef(): DtsNode? {
        // System.out.println("Processing nodeOrRef at " + peek().value)
        // Node format: name { properties/children };
        // Ref node: &ref { ... };
        // Labelled node: label: name { ... };
        // Root: / { ... };

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
            // Often / is parsed as PREPROCESSOR by our lexer if it starts with /
            // But root node is /
            // If Lexer sees / followed by space or {, it might depend on implementation.
            // Our Lexer: / followed by * or / is comment. otherwise returns Preprocessor or similar.
            // Actually our Lexer: c == '/' && isPreprocessorStart -> PREPROCESSOR.
            // isPreprocessorStart returns true always for /.
            // So / by itself is PREPROCESSOR "/".
            if (nameToken.value == "/") {
                nodeName = "/"
            } else {
                // Could be /delete-node/ name;
                 if (nameToken.value == "/delete-node/") {
                     // TODO: Handle delete node?
                     // For now, consume the identifier after and semicolon
                     if (peek().type == TokenType.IDENTIFIER) advance()
                     if (peek().type == TokenType.SEMICOLON) advance()
                     return null
                 }
                 // /memreserve/ etc
                 // Read until semicolon
                 while (peek().type != TokenType.SEMICOLON && peek().type != TokenType.EOF) {
                     advance()
                 }
                 match(TokenType.SEMICOLON)
                 return null
            }
        } else {
            // Error or unknown
            return null
        }

        // If we have a name, expect '{'
        if (match(TokenType.LBRACE)) {
            val node = DtsNode(nodeName)
            // If label exists, maybe perform some magic? For now, we strip labels as per old logic?
            // "It must handle labels attached to nodes".
            // The DtsNode class doesn't seem to have a label field. 
            // We can prepend to name or just ignore for the tree view if not needed.
            // Let's prepend "label: name" if label exists, so user sees it.
            if (label != null) {
                node.name = "$label $nodeName"
            }

            // Parse body
            while (peek().type != TokenType.RBRACE && peek().type != TokenType.EOF) {
                // Inside a node, we can have properties or child nodes.
                // Child node: name { ... };
                // Property: name = value; or name;
                // Since name looks like identifier, we need lookahead or context.
                // Both start with Identifier (or Label).
                // Diff: Node has '{', Property has '=' or ';'
                
                // Be careful with Labels: label: node_name { ... }
                
                val current = peek()
                if (current.type == TokenType.IDENTIFIER || current.type == TokenType.LABEL || current.type == TokenType.REF || (current.type == TokenType.PREPROCESSOR && current.value=="/")) {
                    // Could be node or property.
                    // Check ahead.
                    
                    if (isNextNodeStart()) {
                        val child = parseNodeOrRef()
                        if (child != null) node.addChild(child)
                    } else {
                        val prop = parseProperty()
                        if (prop != null) node.addProperty(prop)
                    }
                } else if (current.type == TokenType.PREPROCESSOR) {
                     // /delete-node/ or #include inside node
                     consumeStatement() 
                } else {
                     advance() // Consume unknown garbage to prevent infinite loop
                }
            }
            match(TokenType.RBRACE)
            match(TokenType.SEMICOLON) // Nodes usually end with };
            return node
        }

        return null
    }

    private fun isNextNodeStart(): Boolean {
        // Look ahead to see if we find '{' before '=' or ';'
        var offset = 0
        while (pos + offset < len) {
            val t = tokens[pos + offset]
            if (t.type == TokenType.LBRACE) return true
            if (t.type == TokenType.EQUALS || t.type == TokenType.SEMICOLON) return false
            if (t.type == TokenType.RBRACE) return false // End of current block
            offset++
        }
        return false
    }

    private fun parseProperty(): DtsProperty? {
        // property-name = value;
        // or property-name; (boolean)
        
        val nameToken = peek()
        if (!match(TokenType.IDENTIFIER) && !match(TokenType.PREPROCESSOR)) {
            // PREPROCESSOR allowed for weird property names if any, but mostly identifiers.
            return null
        }
        
        val name = nameToken.value
        
        if (match(TokenType.SEMICOLON)) {
            // Boolean property
            return DtsProperty(name, "")
        }
        
        if (match(TokenType.EQUALS)) {
            // Value
            val value = parseValue()
            match(TokenType.SEMICOLON)
            return DtsProperty(name, value)
        }
        
        // Fallback?
        return null
    }

    private fun parseValue(): String {
        val sb = StringBuilder()
        while (peek().type != TokenType.SEMICOLON && peek().type != TokenType.RBRACE && peek().type != TokenType.EOF) {
            val t = peek()
            when (t.type) {
                TokenType.STRING_LITERAL -> {
                    sb.append("\"").append(t.value).append("\"")
                    advance()
                }
                TokenType.LANGLE -> {
                     // Array < ... >
                     sb.append(parseArray())
                }
                TokenType.REF -> {
                    sb.append(t.value)
                    advance()
                }
                TokenType.HEX_LITERAL, TokenType.INT_LITERAL, TokenType.IDENTIFIER -> {
                    sb.append(t.value)
                    advance()
                }
                TokenType.EQUALS -> {
                    // Unexpected equals in value? 
                    sb.append("=")
                    advance()
                }
                else -> {
                    // Append raw value for other things
                    sb.append(t.value)
                    advance()
                }
            }
            
            // Add space if next is not semicolon/array end?
            if (peek().type != TokenType.SEMICOLON && peek().type != TokenType.EOF && peek().type != TokenType.RANGLE) {
                 // Don't add space if current was < or previous was something that doesn't need space?
                 // DTS values are usually comma separated (lists of strings) or space separated (cells).
                 // List of strings: "a", "b" -> comma is in identifier? no.
                 // We didn't tokenize comma. IDENTIFIER char set included comma.
                 // If tokens are separated, add space.
                 sb.append(" ")
            }
        }
        return sb.toString().trim()
    }
    
    private fun parseArray(): String {
        val sb = StringBuilder("<")
        advance() // <
        while (peek().type != TokenType.RANGLE && peek().type != TokenType.EOF) {
             val t = peek()
             // Just consume tokens inside array
             sb.append(t.value)
             advance()
             if (peek().type != TokenType.RANGLE) {
                 sb.append(" ")
             }
        }
        match(TokenType.RANGLE)
        sb.append(">")
        return sb.toString()
    }

    private fun consumeStatement() {
        while (peek().type != TokenType.SEMICOLON && peek().type != TokenType.EOF) {
            advance()
        }
        match(TokenType.SEMICOLON)
    }
}
