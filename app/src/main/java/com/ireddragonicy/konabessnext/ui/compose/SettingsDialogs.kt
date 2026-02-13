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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ireddragonicy.konabessnext.R

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val languages = listOf(
        stringResource(R.string.english) to "en",
        stringResource(R.string.chinese) to "zh-rCN",
        stringResource(R.string.german) to "de",
        stringResource(R.string.indonesian) to "in",
        stringResource(R.string.polish) to "pl"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
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
                Text(stringResource(R.string.cancel))
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
    val palettes = listOf(
        stringResource(R.string.palette_dynamic) to "Dynamic",
        stringResource(R.string.palette_purple_teal) to "Purple",
        stringResource(R.string.palette_blue_orange) to "Blue",
        stringResource(R.string.palette_green_red) to "Green",
        stringResource(R.string.palette_pink_cyan) to "Pink"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_color_palette)) },
        text = {
            Column {
                palettes.forEach { (label, value) ->
                    Surface(
                        onClick = { onPaletteSelected(value) },
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (value == currentColorPalette),
                                onClick = { onPaletteSelected(value) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
