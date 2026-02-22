package com.ireddragonicy.konabessnext.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ireddragonicy.konabessnext.R

data class LicenseItem(
    val name: String,
    val license: String,
    val url: String? = null
)

val OpenSourceLicenses = listOf(
    LicenseItem("AndroidX & Jetpack Compose", "Apache License 2.0, Google LLC"),
    LicenseItem("Dagger Hilt", "Apache License 2.0, Google LLC"),
    LicenseItem("Kotlin", "Apache License 2.0, Kotlin Team", "https://kotlinlang.org/"),
    LicenseItem("Kotlinx Serialization", "Apache License 2.0, Kotlin Team", "https://github.com/Kotlin/kotlinx.serialization"),
    LicenseItem("LibSU", "Apache License 2.0, John Wu", "https://github.com/topjohnwu/libsu"),
    LicenseItem("MPAndroidChart", "Apache License 2.0, Philipp Jahoda", "https://github.com/PhilJay/MPAndroidChart"),
    LicenseItem("OkHttp", "Apache License 2.0, Square, Inc.", "https://square.github.io/okhttp/"),
    LicenseItem("Sora Editor", "LGPL-3.0 license, Rosemoe", "https://github.com/Rosemoe/sora-editor")
)

@Composable
fun OpenSourceLicensesDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_open_source_licenses),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(OpenSourceLicenses) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item.url != null) {
                                    item.url?.let {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                        context.startActivity(intent)
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.license,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
