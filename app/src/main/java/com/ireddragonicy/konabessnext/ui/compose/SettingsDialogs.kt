package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val languages = mapOf(
        "English" to "en",
        "Chinese (Simplified)" to "zh-rCN",
        "German" to "de",
        "Indonesian" to "in"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Language") },
        text = {
            Column {
                languages.forEach { (name, code) ->
                    Surface(
                        onClick = { onLanguageSelected(code) },
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (code == currentLanguage),
                                onClick = { onLanguageSelected(code) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PaletteSelectionDialog(
    currentColorPalette: String,
    onDismiss: () -> Unit,
    onPaletteSelected: (String) -> Unit
) {
    val palettes = listOf("Dynamic", "Purple", "Blue", "Green", "Pink")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color Palette") },
        text = {
            Column {
                palettes.forEach { palette ->
                    Surface(
                        onClick = { onPaletteSelected(palette) },
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (palette == currentColorPalette),
                                onClick = { onPaletteSelected(palette) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = palette)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
