package com.ireddragonicy.konabessnext.core.native

import java.io.IOException

object DtcNative {
    init {
        System.loadLibrary("konadts")
    }

    /**
     * Converts a DTB file to DTS format.
     *
     * @param inputPath Absolute path to the input DTB file.
     * @param outputPath Absolute path where the output DTS file will be written.
     * @throws IOException If the conversion fails (non-zero return code from dtc).
     */
    @Throws(IOException::class)
    external fun dtbToDts(inputPath: String, outputPath: String)

    /**
     * Converts a DTS file to DTB format.
     *
     * @param inputPath Absolute path to the input DTS file.
     * @param outputPath Absolute path where the output DTB file will be written.
     * @throws IOException If the conversion fails (non-zero return code from dtc).
     */
    @Throws(IOException::class)
    external fun dtsToDtb(inputPath: String, outputPath: String)
}
