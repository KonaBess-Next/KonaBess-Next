package com.ireddragonicy.konabessnext.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ireddragonicy.konabessnext.R

@Composable
fun MainNavigationBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_freq_table)) },
            label = { Text(stringResource(R.string.edit_freq_table)) },
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(R.drawable.ic_import_export), contentDescription = stringResource(R.string.import_export)) },
            label = { Text(stringResource(R.string.import_export)) },
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) },
            label = { Text(stringResource(R.string.settings)) },
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) }
        )
    }
}
