package com.ireddragonicy.konabessnext.utils

import com.ireddragonicy.konabessnext.model.dts.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtsParserFaultToleranceTest {

    @Test
    fun `parser is fault tolerant for missing semicolons and braces`() {
        val dts = """
/dts-v1/;
/ {
    model = "fault-tolerant"
    soc {
        compatible = "qcom,soc"
        test-prop = <0x1 0x2>;
""".trimIndent()

        val parser = DtsParser(DtsLexer(dts).tokenize())
        val root = parser.parse(
            DtsParser.ParseOptions(
                budget = DtsParser.ParseBudget(
                    maxTokensConsumed = 50_000,
                    timeBudgetMs = 100
                )
            )
        )

        val lint = parser.getLintResult()
        assertEquals("root", root.name)
        assertTrue("Best-effort AST should keep top-level nodes", root.children.isNotEmpty())
        assertTrue("Broken DTS should produce diagnostics", lint.errors.isNotEmpty())
    }

    @Test
    fun `parser handles random garbage and truncated string without throwing`() {
        val dts = """
/ {
    model = "unterminated
    $$$%%%%^^^@@@###
    node@0 {
        reg = <0x1 0x2
    ???
""".trimIndent()

        val parser = DtsParser(DtsLexer(dts).tokenize())
        val root = parser.parse(
            DtsParser.ParseOptions(
                budget = DtsParser.ParseBudget(
                    maxTokensConsumed = 20_000,
                    timeBudgetMs = 100
                )
            )
        )

        val lint = parser.getLintResult()
        assertNotNull(root)
        assertTrue("Garbage input should still return diagnostics", lint.errors.isNotEmpty())
    }

    @Test
    fun `parser keeps partial node header as placeholder node`() {
        val dts = "gpu-bin@0"
        val parser = DtsParser(DtsLexer(dts).tokenize())
        val root = parser.parse()
        val lint = parser.getLintResult()

        assertTrue("Placeholder node should be retained for partial header", root.children.any { it.name == "gpu-bin@0" })
        assertTrue("Partial header should emit warning diagnostics", lint.errors.any { it.severity == Severity.WARNING })
    }

    @Test
    fun `parse budget stops early and emits warning`() {
        val malformed = buildLargeMalformedInput()
        val tokens = DtsLexer(malformed).tokenize()
        val parser = DtsParser(tokens)

        val start = System.nanoTime()
        val root = parser.parse(
            DtsParser.ParseOptions(
                budget = DtsParser.ParseBudget(
                    maxTokensConsumed = 256,
                    timeBudgetMs = 25
                ),
                maxErrors = 200
            )
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        val diagnostics = parser.getLintResult().errors
        assertEquals("root", root.name)
        assertTrue("Budgeted parse should finish quickly, was ${elapsedMs}ms", elapsedMs < 250)
        assertTrue(
            "Expected a stop-early warning",
            diagnostics.any { it.severity == Severity.WARNING && it.message.contains("stopped parse early") }
        )
    }

    private fun buildLargeMalformedInput(): String {
        val sb = StringBuilder("/dts-v1/;\n/ {\n\tgpu {\n")
        repeat(8_000) { i ->
            sb.append("\t\tprop").append(i).append(" = <0x").append(i.toString(16)).append('\n')
        }
        return sb.toString()
    }
}
