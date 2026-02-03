package com.ireddragonicy.konabessnext.core.impl

import com.ireddragonicy.konabessnext.core.interfaces.AssetDataSource
import com.ireddragonicy.konabessnext.core.interfaces.ChipDefinitionLoader
import com.ireddragonicy.konabessnext.model.ChipDefinition
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonChipDefinitionLoader @Inject constructor(
    private val assetDataSource: AssetDataSource
) : ChipDefinitionLoader {

    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }

    override fun loadDefinitions(): List<ChipDefinition> {
        val loadedDefinitions = mutableListOf<ChipDefinition>()
        
        try {
            val chipFiles = assetDataSource.list("chips")?.filter { it.endsWith(".json") } ?: emptyList()
            
            for (filename in chipFiles) {
                try {
                    assetDataSource.open("chips/$filename").bufferedReader().use { reader ->
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
