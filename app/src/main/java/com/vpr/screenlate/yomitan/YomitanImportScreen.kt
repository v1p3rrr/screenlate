package com.vpr.screenlate.yomitan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vpr.screenlate.R
import com.vpr.screenlate.dictionaries.DictionariesViewModel
import com.vpr.screenlate.ui.components.SectionCard
import com.vpr.screenlate.ui.components.SettingsScaffold

private val BACKUP_TYPES = arrayOf("application/json", "application/octet-stream", "*/*")

/** Imports from Yomitan: its dictionary collection export. */
@Composable
fun YomitanImportScreen(onBack: () -> Unit, dictionaries: DictionariesViewModel = hiltViewModel()) {
    val collectionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) dictionaries.importYomitanBackup(uri)
    }
    SettingsScaffold(stringResource(R.string.yomitan_import_title), onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.yomitan_import_dictionaries)) {
                Text(stringResource(R.string.yomitan_import_dictionaries_hint))
                OutlinedButton(onClick = { collectionPicker.launch(BACKUP_TYPES) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dictionaries_import_yomitan_backup))
                }
            }
        }
    }
}
