package com.vpr.screenlate.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.note.Sentence
import com.vpr.screenlate.core.common.settings.ThemeMode
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.overlay.anki.NoteContext
import com.vpr.screenlate.overlay.anki.PopupNotes
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.vpr.screenlate.overlay.R as OverlayR

/**
 * Dictionary search: the same result page as the overlay popup, embedded in a screen with a text field.
 *
 * @param initialQuery text to search right away, e.g. from the text selection menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val focus = remember { FocusRequester() }
    val currentDark by rememberUpdatedState(dark)

    // The page and its buttons live as long as the screen; the view model only does lookups.
    val holder = remember {
        object {
            lateinit var page: LookupPage
            lateinit var notes: PopupNotes
        }
    }
    val page = remember {
        LookupPage(
            context,
            object : LookupPage.Callbacks {
                override fun onClose() = Unit

                override fun onLookup(query: String, primaryReading: String?) {
                    scope.launch {
                        val found = viewModel.search(query, primaryReading)
                        holder.page.push(state(context, currentDark, found))
                        holder.notes.onResultsShown(found.results.firstOrNull()?.term?.let { it.expression to it.reading })
                    }
                }

                override fun onOpenUrl(url: String) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }

                override fun onAddNote(index: Int, noteData: String, withScreenshot: Boolean) =
                    holder.notes.add(index, noteData, withScreenshot = false)

                override fun onPlayAudio(expression: String, reading: String) = holder.notes.play(expression, reading)

                override fun onKanji(character: String) {
                    scope.launch {
                        val result = viewModel.kanji(character)
                        holder.page.push(
                            PageState.kanji(context, currentDark, result, context.getString(OverlayR.string.overlay_no_kanji)),
                        )
                    }
                }

                override fun media(dictionary: String, path: String): ByteArray? =
                    runBlocking { viewModel.dictionaryLookup.media(dictionary, path) }
            },
            embedded = true,
        ).also { holder.page = it }
    }
    remember {
        PopupNotes(
            context = context,
            scope = scope,
            page = page,
            anki = viewModel.ankiDroid,
            notes = viewModel.notes,
            audio = viewModel.audio,
            audioSettings = viewModel.audioSettings,
            lookup = viewModel.dictionaryLookup,
            // The search text is the sentence; there is no screenshot.
            noteContext = { NoteContext(Sentence("", viewModel.query.value.trim(), ""), null) },
            cropEditor = null,
        ).also { holder.notes = it }
    }
    DisposableEffect(Unit) {
        onDispose {
            holder.notes.release()
            page.destroy()
        }
    }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank() && viewModel.query.value.isBlank()) viewModel.query.value = initialQuery
        page.setStyles(Json.encodeToJsonElement(ListSerializer(DictionaryStyle.serializer()), viewModel.styles()))
        focus.requestFocus()
    }
    LaunchedEffect(results, dark) {
        val current = results ?: return@LaunchedEffect
        holder.notes.refreshActions()
        page.render(state(context, dark, current))
        holder.notes.onResultsShown(current.results.firstOrNull()?.term?.let { it.expression to it.reading })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            AndroidView(factory = { page.container }, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun state(context: android.content.Context, dark: Boolean, results: SearchResults) = PageState.build(
    context = context,
    dark = dark,
    text = results.text,
    matched = PageState.matchedLength(results.results),
    results = results.results,
    message = when {
        results.results.isNotEmpty() -> null
        results.noDictionaries -> context.getString(OverlayR.string.overlay_no_dictionaries)
        else -> context.getString(OverlayR.string.overlay_no_results)
    },
)
