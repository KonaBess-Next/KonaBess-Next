package com.ireddragonicy.konabessnext.repository

import com.ireddragonicy.konabessnext.repository.HistoryManager.LineDiff
import org.junit.Assert.assertEquals
import org.junit.Test

class UndoRedoTest {

    @Test
    fun testBlockDeleteUndoOrder() {
        val historyManager = HistoryManager()
        
        // Initial: 5 lines
        val original = listOf("Line 1", "Line 2", "Line 3", "Line 4", "Line 5")
        
        // Delete block: 2, 3, 4 (indices 1, 2, 3)
        // Result: 1, 5
        val afterDelete = listOf("Line 1", "Line 5")
        
        // Snapshot
        historyManager.snapshot(original, afterDelete, "Delete Block")
        
        // Verify Undo
        val restored = historyManager.undo(afterDelete)
        
        println("Original: $original")
        println("Restored: $restored")
        
        // Check integrity
        assertEquals("Undo should restore exact string list", original, restored)
    }

    @Test
    fun testBlockInsertUndo() {
        // Validation for Insert Undo as well
        val historyManager = HistoryManager()
        val original = listOf("A", "E")
        val afterInsert = listOf("A", "B", "C", "D", "E")
        
        historyManager.snapshot(original, afterInsert, "Insert Block")
        
        val restored = historyManager.undo(afterInsert)
        assertEquals(original, restored)
    }

    @Test
    fun testGpuRenameUndoRedo() {
        val historyManager = HistoryManager()

        // 1. Initial State
        val initialLines = listOf(
            "/ {",
            "    model = \"Qualcomm Technologies, Inc. Kona\";",
            "    compatible = \"qcom,kona\";",
            "    qcom,board-id = <0 0>;",
            "",
            "    fragment@0 {",
            "        target = <&gmu>;",
            "        __overlay__ {",
            "            qcom,gpu-model = \"Adreno 640\";",
            "            qcom,gmu-pwrlevel@0 {",
            "                reg = <0>;",
            "                qcom,gmu-freq = <585000000>;",
            "            };",
            "        };",
            "    };",
            "};"
        )

        // 2. Rename Action (Simulated Repository Logic)
        val renamedLines = ArrayList(initialLines)
        // Find line with "qcom,gpu-model"
        val index = renamedLines.indexOfFirst { it.contains("qcom,gpu-model") }
        if (index != -1) {
            renamedLines[index] = "            qcom,gpu-model = \"Adreno 999\";"
        }

        // Snapshot
        historyManager.snapshot(initialLines, renamedLines, "Rename GPU")

        // 3. Verify Mutation
        assertEquals("Adreno 999", extractGpuName(renamedLines))

        // 4. Undo
        val afterUndo = historyManager.undo(renamedLines) ?: renamedLines
        assertEquals("Undo should revert to original", "Adreno 640", extractGpuName(afterUndo))

        // 5. Redo
        val afterRedo = historyManager.redo(afterUndo) ?: afterUndo
        assertEquals("Redo should restore rename", "Adreno 999", extractGpuName(afterRedo))
    }

    private fun extractGpuName(lines: List<String>): String {
        val pattern = java.util.regex.Pattern.compile("""qcom,gpu-model\s*=\s*"([^"]+)";""")
        for (line in lines) {
            val matcher = pattern.matcher(line.trim())
            if (matcher.find()) {
                return matcher.group(1) ?: ""
            }
        }
        return ""
    }
}
