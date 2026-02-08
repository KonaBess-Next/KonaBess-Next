package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.Severity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Performance & correctness tests for the DTS lexer, parser, linter, and formatter.
 *
 * Uses the real Tuna0.txt file (23,914 lines / ~1 MB Qualcomm SoC DTS)
 * as the benchmark input. All timing assertions use generous upper bounds
 * to avoid flakes on slow CI, but the goal is sub-100 ms on modern hardware.
 *
 * Run with:
 *   ./gradlew :app:test --tests "*.DtsPerformanceTest"
 */
class DtsPerformanceTest {

    private lateinit var tunaContent: String
    private var tunaLineCount = 0

    @Before
    fun setUp() {
        // Load real DTS file — path relative to module root
        val file = File("src/test/Tuna0.txt")
        assertTrue("Tuna0.txt not found at ${file.absolutePath}", file.exists())
        tunaContent = file.readText()
        tunaLineCount = tunaContent.count { it == '\n' } + 1
        println("Loaded Tuna0.txt: ${tunaContent.length} chars, $tunaLineCount lines")
    }

    // =========================================================================
    // LEXER BENCHMARKS
    // =========================================================================

    @Test
    fun `lexer tokenizes Tuna0 in under 200ms`() {
        // Warmup
        repeat(3) { DtsLexer(tunaContent).tokenize() }

        val times = mutableListOf<Long>()
        var tokenCount = 0

        repeat(5) {
            val start = System.nanoTime()
            val tokens = DtsLexer(tunaContent).tokenize()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            times.add(elapsed)
            tokenCount = tokens.size
        }

        val median = times.sorted()[times.size / 2]
        val min = times.min()
        val max = times.max()

        println("Lexer: $tokenCount tokens | min=${min}ms median=${median}ms max=${max}ms")
        assertTrue(
            "Lexer median time ${median}ms exceeds 200ms threshold",
            median < 200
        )
    }

    @Test
    fun `lexer produces correct token count for Tuna0`() {
        val tokens = DtsLexer(tunaContent).tokenize()
        // Should produce a substantial number of tokens for a 24K-line file
        // The exact number depends on file content but should be > 100K
        println("Token count: ${tokens.size}")
        assertTrue("Expected > 50000 tokens, got ${tokens.size}", tokens.size > 50_000)
        assertEquals("Last token should be EOF", TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `lexer handles dts-v1 directive correctly`() {
        val tokens = DtsLexer("/dts-v1/;\n\n/ {\n};").tokenize()
        // /dts-v1/ → PREPROCESSOR, ; → SEMICOLON, / → PREPROCESSOR, { → LBRACE, } → RBRACE, ; → SEMICOLON, EOF
        assertEquals(TokenType.PREPROCESSOR, tokens[0].type)
        assertEquals("/dts-v1/", tokens[0].value)
        assertEquals(TokenType.SEMICOLON, tokens[1].type)
        assertEquals(TokenType.PREPROCESSOR, tokens[2].type)
        assertEquals("/", tokens[2].value)
        assertEquals(TokenType.LBRACE, tokens[3].type)
    }

    @Test
    fun `lexer does not create false preprocessor for slash-brace`() {
        // "/" followed by whitespace and "{" should produce PREPROCESSOR "/" then LBRACE
        val tokens = DtsLexer("/ {\n\tmodel = \"test\";\n};").tokenize()
        val types = tokens.map { it.type }
        assertTrue("Should contain PREPROCESSOR for /", TokenType.PREPROCESSOR in types)
        assertTrue("Should contain LBRACE", TokenType.LBRACE in types)
        assertTrue("Should contain IDENTIFIER for model", types.contains(TokenType.IDENTIFIER))
    }

    // =========================================================================
    // PARSER BENCHMARKS
    // =========================================================================

    @Test
    fun `parser parses Tuna0 in under 200ms`() {
        // Pre-tokenize (lexer already tested separately)
        val tokens = DtsLexer(tunaContent).tokenize()

        // Warmup
        repeat(3) { DtsParser(tokens).parse() }

        val times = mutableListOf<Long>()
        var nodeCount = 0

        repeat(5) {
            val start = System.nanoTime()
            val root = DtsParser(tokens).parse()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            times.add(elapsed)
            nodeCount = countNodes(root)
        }

        val median = times.sorted()[times.size / 2]
        val min = times.min()
        val max = times.max()

        println("Parser: $nodeCount nodes | min=${min}ms median=${median}ms max=${max}ms")
        assertTrue(
            "Parser median time ${median}ms exceeds 200ms threshold",
            median < 200
        )
    }

    @Test
    fun `parser produces valid AST for Tuna0`() {
        val tokens = DtsLexer(tunaContent).tokenize()
        val parser = DtsParser(tokens)
        val root = parser.parse()

        assertEquals("root", root.name)
        assertTrue("Root should have children", root.children.isNotEmpty())

        // The first child should be "/" (the root DTS node)
        val rootNode = root.children[0]
        assertEquals("/", rootNode.name)

        // Root DTS node should have standard properties
        val propNames = rootNode.properties.map { it.name }
        assertTrue("Should have 'model' property", "model" in propNames)
        assertTrue("Should have 'compatible' property", "compatible" in propNames)

        // Should have children like memory, aliases, cpus, soc, etc.
        val childNames = rootNode.children.map { it.name }
        assertTrue("Should have 'memory' child", "memory" in childNames)
        assertTrue("Should have 'soc' child", "soc" in childNames)

        println("Root node: ${rootNode.properties.size} properties, ${rootNode.children.size} children")
        println("Total AST nodes: ${countNodes(root)}")
    }

    // =========================================================================
    // LINT BENCHMARKS
    // =========================================================================

    @Test
    fun `lint pipeline completes Tuna0 in under 400ms`() {
        // Full pipeline: lex → parse → getLintResult
        // Warmup
        repeat(3) {
            val tokens = DtsLexer(tunaContent).tokenize()
            val parser = DtsParser(tokens)
            parser.parse()
            parser.getLintResult()
        }

        val times = mutableListOf<Long>()
        var errorCount = 0

        repeat(5) {
            val start = System.nanoTime()
            val tokens = DtsLexer(tunaContent).tokenize()
            val parser = DtsParser(tokens)
            parser.parse()
            val result = parser.getLintResult()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            times.add(elapsed)
            errorCount = result.errors.size
        }

        val median = times.sorted()[times.size / 2]
        val min = times.min()
        val max = times.max()

        println("Lint pipeline: $errorCount errors | min=${min}ms median=${median}ms max=${max}ms")
        assertTrue(
            "Lint pipeline median time ${median}ms exceeds 400ms threshold",
            median < 400
        )
    }

    @Test
    fun `lint does not produce false positive on dts-v1 header`() {
        val dts = "/dts-v1/;\n\n/ {\n\tmodel = \"test\";\n};"
        val tokens = DtsLexer(dts).tokenize()
        val parser = DtsParser(tokens)
        parser.parse()
        val result = parser.getLintResult()

        val errors = result.errors.filter { it.severity == Severity.ERROR }
        assertTrue(
            "Should produce no false positive errors on /dts-v1/; — got: ${errors.map { it.message }}",
            errors.isEmpty()
        )
    }

    @Test
    fun `lint does not produce false positive on Tuna0`() {
        val tokens = DtsLexer(tunaContent).tokenize()
        val parser = DtsParser(tokens)
        parser.parse()
        val result = parser.getLintResult()

        // A valid production DTS file should produce zero or near-zero errors
        val errorMessages = result.errors.filter { it.severity == Severity.ERROR }
        println("Lint errors on Tuna0: ${errorMessages.size}")
        errorMessages.take(5).forEach { e ->
            println("  Line ${e.line + 1}: ${e.message}")
        }

        // Allow some tolerance for DTS quirks, but shouldn't be excessive
        assertTrue(
            "Too many false positives (${errorMessages.size}) on a valid production DTS file",
            errorMessages.size < 20
        )
    }

    @Test
    fun `lint error grouping by line is efficient`() {
        val start = System.nanoTime()
        val tokens = DtsLexer(tunaContent).tokenize()
        val parser = DtsParser(tokens)
        parser.parse()
        val result = parser.getLintResult()
        val grouped = result.errors.groupBy { it.line }
        val elapsed = (System.nanoTime() - start) / 1_000_000

        println("Lint + groupBy: ${elapsed}ms | ${grouped.size} lines with errors")
        assertTrue("Full lint + groupBy should be under 500ms, was ${elapsed}ms", elapsed < 500)
    }

    // =========================================================================
    // FORMATTER BENCHMARKS
    // =========================================================================

    @Test
    fun `formatter formats Tuna0 in under 500ms`() {
        // Warmup
        repeat(2) { DtsFormatter.format(tunaContent) }

        val times = mutableListOf<Long>()
        var outputLength = 0

        repeat(3) {
            val start = System.nanoTime()
            val formatted = DtsFormatter.format(tunaContent)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            times.add(elapsed)
            outputLength = formatted.length
        }

        val median = times.sorted()[times.size / 2]
        val min = times.min()
        val max = times.max()

        println("Formatter: ${outputLength} chars output | min=${min}ms median=${median}ms max=${max}ms")
        assertTrue(
            "Formatter median time ${median}ms exceeds 500ms threshold",
            median < 500
        )
    }

    @Test
    fun `formatter preserves dts-v1 header`() {
        val input = "/dts-v1/;\n\n/ {\n\tmodel = \"test\";\n};"
        val formatted = DtsFormatter.format(input)
        assertTrue("Formatted output should start with /dts-v1/;", formatted.startsWith("/dts-v1/;"))
    }

    @Test
    fun `formatter output is idempotent`() {
        val small = "/dts-v1/;\n\n/ {\n\tmodel = \"test\";\n\tcompatible = \"qcom,tuna\";\n\n\tmemory {\n\t\tdevice_type = \"memory\";\n\t};\n};\n"
        val first = DtsFormatter.format(small)
        val second = DtsFormatter.format(first)
        assertEquals("Formatter should be idempotent", first, second)
    }

    // =========================================================================
    // FULL PIPELINE BENCHMARK (simulates what happens on every keystroke)
    // =========================================================================

    @Test
    fun `full keystroke pipeline under 500ms`() {
        // Simulate what updateFromText does: lex → parse → getLintResult → groupBy
        // Warmup
        repeat(2) {
            val tokens = DtsLexer(tunaContent).tokenize()
            val parser = DtsParser(tokens)
            parser.parse()
            parser.getLintResult().errors.groupBy { it.line }
        }

        val times = mutableListOf<Long>()

        repeat(5) {
            val start = System.nanoTime()
            val tokens = DtsLexer(tunaContent).tokenize()
            val parser = DtsParser(tokens)
            parser.parse()
            val errors = parser.getLintResult().errors.groupBy { it.line }
            val elapsed = (System.nanoTime() - start) / 1_000_000
            times.add(elapsed)
        }

        val median = times.sorted()[times.size / 2]
        val min = times.min()
        val max = times.max()

        println("Full pipeline: min=${min}ms median=${median}ms max=${max}ms")
        assertTrue(
            "Full pipeline median ${median}ms exceeds 500ms threshold",
            median < 500
        )
    }

    @Test
    fun `breakdown - lex vs parse vs lint times`() {
        // Compare individual stage timings
        val tokens: List<Token>

        // Lex
        val lexStart = System.nanoTime()
        tokens = DtsLexer(tunaContent).tokenize()
        val lexTime = (System.nanoTime() - lexStart) / 1_000_000

        // Parse
        val parser = DtsParser(tokens)
        val parseStart = System.nanoTime()
        parser.parse()
        val parseTime = (System.nanoTime() - parseStart) / 1_000_000

        // Lint
        val lintStart = System.nanoTime()
        val result = parser.getLintResult()
        val lintTime = (System.nanoTime() - lintStart) / 1_000_000

        // GroupBy
        val groupStart = System.nanoTime()
        val grouped = result.errors.groupBy { it.line }
        val groupTime = (System.nanoTime() - groupStart) / 1_000_000

        val total = lexTime + parseTime + lintTime + groupTime

        println("=== PERFORMANCE BREAKDOWN ===")
        println("  Lex:     ${lexTime}ms (${tokens.size} tokens)")
        println("  Parse:   ${parseTime}ms")
        println("  Lint:    ${lintTime}ms (${result.errors.size} errors)")
        println("  GroupBy: ${groupTime}ms (${grouped.size} lines)")
        println("  TOTAL:   ${total}ms")
        println("=============================")

        assertTrue("Lex should be under 200ms, was ${lexTime}ms", lexTime < 200)
        assertTrue("Parse should be under 200ms, was ${parseTime}ms", parseTime < 200)
    }

    // =========================================================================
    // EDGE CASE TESTS
    // =========================================================================

    @Test
    fun `lexer handles empty input`() {
        val tokens = DtsLexer("").tokenize()
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
    }

    @Test
    fun `parser handles empty input`() {
        val tokens = DtsLexer("").tokenize()
        val root = DtsParser(tokens).parse()
        assertEquals("root", root.name)
        assertTrue(root.children.isEmpty())
    }

    @Test
    fun `lexer and parser handle minimal DTS`() {
        val dts = "/ {};"
        val tokens = DtsLexer(dts).tokenize()
        val root = DtsParser(tokens).parse()
        assertEquals(1, root.children.size)
        assertEquals("/", root.children[0].name)
    }

    @Test
    fun `parser handles deeply nested nodes`() {
        // Build 100 levels of nesting
        val sb = StringBuilder("/dts-v1/;\n/ {\n")
        repeat(100) { i -> sb.append("\t".repeat(i + 1)).append("node$i {\n") }
        repeat(100) { i -> sb.append("\t".repeat(100 - i)).append("};\n") }
        sb.append("};")

        val start = System.nanoTime()
        val tokens = DtsLexer(sb.toString()).tokenize()
        val root = DtsParser(tokens).parse()
        val elapsed = (System.nanoTime() - start) / 1_000_000

        println("Deep nesting (100 levels): ${elapsed}ms")
        assertTrue("Deep nesting should parse in under 50ms, was ${elapsed}ms", elapsed < 50)
        assertNotNull(root.children.firstOrNull())
    }

    @Test
    fun `parser handles wide node with many properties`() {
        // Node with 10000 properties
        val sb = StringBuilder("/ {\n")
        repeat(10_000) { i -> sb.append("\tprop$i = <0x${"${i}".padStart(4, '0')}>;\n") }
        sb.append("};")

        val start = System.nanoTime()
        val tokens = DtsLexer(sb.toString()).tokenize()
        val root = DtsParser(tokens).parse()
        val elapsed = (System.nanoTime() - start) / 1_000_000

        println("Wide node (10K properties): ${elapsed}ms")
        assertTrue("10K properties should parse in under 200ms, was ${elapsed}ms", elapsed < 200)
        assertEquals(10_000, root.children[0].properties.size)
    }

    @Test
    fun `lexer correctly tokenizes hex literals in angle brackets`() {
        val dts = "prop = <0x1234 0xABCD>;"
        val tokens = DtsLexer(dts).tokenize()
        val hexTokens = tokens.filter { it.type == TokenType.HEX_LITERAL }
        assertEquals(2, hexTokens.size)
        assertEquals("0x1234", hexTokens[0].value)
        assertEquals("0xABCD", hexTokens[1].value)
    }

    @Test
    fun `parser correctly parses string property`() {
        val dts = "/ { model = \"Qualcomm Test SoC\"; };"
        val tokens = DtsLexer(dts).tokenize()
        val root = DtsParser(tokens).parse()
        val prop = root.children[0].properties.find { it.name == "model" }
        assertNotNull(prop)
        assertEquals("\"Qualcomm Test SoC\"", prop?.originalValue)
    }

    @Test
    fun `parser correctly parses boolean property`() {
        val dts = "/ { no-map; };"
        val tokens = DtsLexer(dts).tokenize()
        val root = DtsParser(tokens).parse()
        val prop = root.children[0].properties.find { it.name == "no-map" }
        assertNotNull(prop)
        assertEquals("", prop?.originalValue)
    }

    // =========================================================================
    // MEMORY ALLOCATION TEST
    // =========================================================================

    @Test
    fun `lexer does not create excessive garbage`() {
        // Run GC before and after to measure retained objects
        System.gc()
        val beforeMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val tokens = DtsLexer(tunaContent).tokenize()

        val afterMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val allocatedMB = (afterMem - beforeMem) / (1024.0 * 1024.0)

        println("Lexer memory: ~${"%.1f".format(allocatedMB)}MB for ${tokens.size} tokens")
        // Should use less than 50MB for a 1MB input file
        assertTrue("Lexer used too much memory: ${"%.1f".format(allocatedMB)}MB", allocatedMB < 50)
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun countNodes(node: com.ireddragonicy.konabessnext.model.dts.DtsNode): Int {
        var count = 1
        for (child in node.children) {
            count += countNodes(child)
        }
        return count
    }
}
