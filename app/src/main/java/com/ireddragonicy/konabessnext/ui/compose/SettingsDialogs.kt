package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var query by remember { mutableStateOf("") }

    val languages = listOf(
        stringResource(R.string.english) to "en",
        stringResource(R.string.chinese) to "zh-rCN",
        stringResource(R.string.german) to "de",
        stringResource(R.string.indonesian) to "in",
        stringResource(R.string.polish) to "pl"
    )

    val filteredLanguages = remember(languages, query) {
        val q = query.trim()
        if (q.isEmpty()) languages
        else languages.filter { (name, code) ->
            name.contains(q, ignoreCase = true) || code.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search)) },
                    placeholder = { Text(stringResource(R.string.search_hint)) }
                )
                Spacer(Modifier.height(8.dp))

                if (filteredLanguages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    filteredLanguages.forEach { (name, code) ->
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
