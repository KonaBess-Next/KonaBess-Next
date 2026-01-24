package com.ireddragonicy.konabessnext.core

import android.content.Context
import com.ireddragonicy.konabessnext.model.ChipDefinition
import kotlinx.serialization.json.Json
import java.io.IOException

object ChipLoader {
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    fun loadDefinitions(context: Context): List<ChipDefinition> {
        val loadedDefinitions = mutableListOf<ChipDefinition>()
        
        try {
            val assetManager = context.assets
            val chipFiles = assetManager.list("chips")?.filter { it.endsWith(".json") } ?: emptyList()
            
            for (filename in chipFiles) {
                try {
                    assetManager.open("chips/$filename").bufferedReader().use { reader ->
                        val text = reader.readText()
                        val definitions = json.decodeFromString<List<ChipDefinition>>(text)
                        loadedDefinitions.addAll(definitions)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return loadedDefinitions
    }
}
