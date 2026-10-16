package com.vpr.screenlate.languages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.ui.components.Hint
import com.vpr.screenlate.ui.theme.AccentDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FirstRunViewModel @Inject constructor(private val switch: LanguageSwitch) : ViewModel() {
    /** The preselected language first ([LanguageSwitch.bundledLanguage]); then the others by name. */
    val languages: List<Language> = Language.entries.sortedWith(
        compareBy<Language> { it != switch.bundledLanguage }.thenBy(Collator.getInstance()) { it.displayName() },
    )

    val preselected: Language get() = switch.bundledLanguage

    private val steps = Channel<FirstRunStep>(Channel.BUFFERED)

    /** Where to go after [choose]; delivered once, also to a screen recreated while the check ran. */
    val next: Flow<FirstRunStep> = steps.receiveAsFlow()

    private var choosing: Job? = null

    /** A language with its dictionaries installed is turned on at once; another opens its download screen. */
    fun choose(language: Language) {
        if (choosing?.isActive == true) return
        choosing = viewModelScope.launch {
            if (switch.needsDownloads(language)) {
                steps.send(FirstRunStep.Setup(language))
            } else {
                switch.turnOn(language, firstRun = true)
                steps.send(FirstRunStep.Done)
            }
        }
    }
}

sealed interface FirstRunStep {
    data class Setup(val language: Language) : FirstRunStep

    data object Done : FirstRunStep
}

/** Asked once on a fresh install: the language the user learns. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunScreen(onSetup: (Language) -> Unit, onDone: () -> Unit, viewModel: FirstRunViewModel = hiltViewModel()) {
    var chosen by rememberSaveable { mutableStateOf(viewModel.preselected) }
    var busy by remember { mutableStateOf(false) }
    val currentOnSetup by rememberUpdatedState(onSetup)
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) {
        viewModel.next.collect { step ->
            when (step) {
                is FirstRunStep.Setup -> {
                    busy = false
                    currentOnSetup(step.language)
                }
                FirstRunStep.Done -> currentOnDone()
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.first_run_question), style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.selectableGroup()) {
                viewModel.languages.forEach { language ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = language == chosen, role = Role.RadioButton, onClick = { chosen = language })
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = language == chosen, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(language.displayName(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Hint(stringResource(R.string.first_run_hint))
            Button(
                onClick = {
                    busy = true
                    viewModel.choose(chosen)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = AccentDefaults.buttonColors(),
            ) { Text(stringResource(R.string.first_run_continue), textAlign = TextAlign.Center) }
        }
    }
}
