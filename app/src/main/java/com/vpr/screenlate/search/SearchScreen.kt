package com.vpr.screenlate.search

import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.anki.note.Sentence
import com.vpr.screenlate.core.common.language.support
import com.vpr.screenlate.core.common.settings.isDark
import com.vpr.screenlate.dictionary.api.model.DictionaryStyle
import com.vpr.screenlate.dictionary.api.model.DictionaryTagNotes
import com.vpr.screenlate.overlay.R as OverlayR
import com.vpr.screenlate.overlay.anki.NoteContext
import com.vpr.screenlate.overlay.anki.NoteSource
import com.vpr.screenlate.overlay.anki.PopupNotes
import com.vpr.screenlate.overlay.web.LookupPage
import com.vpr.screenlate.overlay.web.PageState
import com.vpr.screenlate.overlay.web.PageTheme
import com.vpr.screenlate.overlay.web.noResultsText
import com.vpr.screenlate.ui.components.BackButton
import com.vpr.screenlate.ui.components.TooltipIconButton
import com.vpr.screenlate.ui.theme.LocalEInk
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Dictionary search: the same result page as the overlay popup, embedded in a screen with a text field.
 *
 * @param initialQuery text to search right away, e.g. from the text selection menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAnkiSettings: () -> Unit,
    onOpenDictionaries: () -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val eInk = LocalEInk.current
    val theme = when {
        eInk -> PageTheme.E_INK
        dark -> PageTheme.DARK
        else -> PageTheme.LIGHT
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentTheme by rememberUpdatedState(theme)
    val noKanji by rememberUpdatedState(stringResource(OverlayR.string.overlay_no_kanji))

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
                    val shown = viewModel.results.value
                    scope.launch {
                        val found = viewModel.search(query, primaryReading = primaryReading)
                        // A new search text replaced the page meanwhile; this lookup belonged to the old one.
                        if (viewModel.results.value !== shown) return@launch
                        holder.page.push(state(context, currentTheme, found))
                        holder.notes.onResultsShown(found.results.firstOrNull()?.term?.let { it.expression to it.reading })
                    }
                }

                override fun onOpenUrl(url: String) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                }

                override fun onCopy(text: String, html: String?) = PageState.copy(context, text, html)

                override fun onKanji(character: String) {
                    val shown = viewModel.results.value
                    scope.launch {
                        val result = viewModel.kanji(character)
                        if (viewModel.results.value !== shown) return@launch
                        holder.page.push(
                            PageState.kanji(context, currentTheme, result, noKanji),
                        )
                    }
                }

                override fun media(dictionary: String, path: String): ByteArray? =
                    runBlocking { viewModel.dictionaryLookup.media(dictionary, path, viewModel.language.value) }
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
            player = viewModel.audioPlayer,
            lookup = viewModel.dictionaryLookup,
            language = { viewModel.language.value },
            // The search text is the sentence; there is no screenshot.
            noteSource = {
                val query = viewModel.query.value.trim()
                NoteSource { NoteContext(Sentence("", query, ""), null) }
            },
            translation = viewModel.translation,
            cropEditor = null,
            onAnkiOpened = {},
            // The app's own search keeps its results: auto-hide is for the popup over other apps.
            onNoteAdded = {},
            onOpenAnkiSettings = onOpenAnkiSettings,
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
        page.setTagNotes(Json.encodeToJsonElement(ListSerializer(DictionaryTagNotes.serializer()), viewModel.tagNotes()))
        // Text from the selection menu is already searched: the keyboard would only cover the results.
        if (viewModel.query.value.isBlank()) focus.requestFocus()
    }
    LaunchedEffect(language) {
        page.setStyles(Json.encodeToJsonElement(ListSerializer(DictionaryStyle.serializer()), viewModel.styles(language)))
    }
    LaunchedEffect(Unit) { viewModel.appearance.collect { page.setAppearance(it) } }
    LaunchedEffect(results) {
        val current = results ?: return@LaunchedEffect
        holder.notes.refreshActions()
        page.render(state(context, currentTheme, current))
        holder.notes.onResultsShown(current.results.firstOrNull()?.term?.let { it.expression to it.reading })
    }
    // A theme change restyles the page and keeps the view shown, e.g. a kanji opened from the results.
    var styledTheme by remember { mutableStateOf(theme) }
    LaunchedEffect(theme) {
        if (theme == styledTheme) return@LaunchedEffect
        styledTheme = theme
        val current = results ?: return@LaunchedEffect
        page.update(state(context, theme, current))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                // The language's glyph forms for the typed text, whatever the UI language.
                textStyle = LocalTextStyle.current.copy(localeList = LocaleList(language.support.languageTag)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TooltipIconButton(R.drawable.ic_close, stringResource(R.string.action_clear), onClick = { viewModel.query.value = "" })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            // Nothing can be found until a dictionary with definitions is on; the page says why.
            if (query.isNotBlank() && results?.noTermDictionary != null) {
                OutlinedButton(
                    onClick = onOpenDictionaries,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.home_dictionaries_open), textAlign = TextAlign.Center) }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                // The page stays alive while hidden so the next search renders without a reload.
                AndroidView(
                    factory = { page.container },
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (query.isBlank()) 0f else 1f),
                )
                if (query.isBlank()) EmptySearch()
            }
        }
    }
}

/** Shown instead of an empty page before anything is typed. */
@Composable
private fun EmptySearch() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 48.dp)
                .size(48.dp),
        )
        Text(
            stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.search_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun state(context: android.content.Context, theme: PageTheme, results: SearchResults): String = PageState.build(
    context = context,
    theme = theme,
    text = results.text,
    matched = PageState.matchedLength(results.results, results.kanji),
    results = results.results,
    message = when {
        results.results.isNotEmpty() || results.kanji != null -> null
        else -> context.getString(noResultsText(results.noTermDictionary))
    },
    kanji = results.kanji,
)
