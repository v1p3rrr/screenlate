package com.vpr.screenlate.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.core.common.Language
import com.vpr.screenlate.core.common.displayName
import com.vpr.screenlate.core.photos.PhotoWork

/** Camera capture and the persistent current photograph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onOpenDictionaries: (Language) -> Unit = {},
    onOpenModels: (Language) -> Unit = {},
    onOpenLanguages: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val flash by viewModel.session.flash.collectAsStateWithLifecycle()
    val recognition by viewModel.recognition.state.collectAsStateWithLifecycle()
    val block by viewModel.recognition.blocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmingClose by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    var toolbarMenu by remember { mutableStateOf(false) }
    var warning by remember(state.photo?.id) { mutableStateOf(false) }
    val blocked = block?.takeIf { it.documentId == state.photo?.id && !state.live }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.importImage(it.toString()) }
    }
    val importImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // Read here, not inside SideEffect: reads there do not recompose the page when a menu opens.
    val obscured = confirmingClose || state.askingImport || languageMenu || toolbarMenu || warning || blocked != null
    SideEffect { viewModel.setObscured(obscured) }
    DisposableEffect(viewModel) { onDispose { viewModel.setObscured(false) } }
    // The page is the bubble's source from the start of its entry, frames before it resumes, so that a pull-out
    // meanwhile does not scan the screen; it stops being one as soon as it pauses.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> viewModel.onForeground()
                Lifecycle.Event.ON_PAUSE -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.onBackground()
        }
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                resources.getString(
                    when (event) {
                        CameraEvent.CAPTURE -> R.string.camera_capture_failed
                        CameraEvent.IMPORT -> R.string.camera_import_failed
                        CameraEvent.IMPORT_TOO_LARGE -> R.string.camera_import_too_large
                        CameraEvent.DELETE -> R.string.camera_close_failed
                        CameraEvent.LOAD -> R.string.camera_photo_unreadable
                        CameraEvent.EXPORT_FAILED -> R.string.camera_export_failed
                        CameraEvent.EXPORTED -> R.string.camera_exported
                        CameraEvent.DELETED -> R.string.camera_deleted
                        CameraEvent.SAVED -> R.string.camera_saved_history
                        CameraEvent.SAVE_FAILED -> R.string.camera_save_history_failed
                    },
                ),
            )
        }
    }
    BackHandler(state.takingNew && state.photo != null) { viewModel.backToPhoto() }
    if (confirmingClose) {
        UnsavedPhotoDialog(
            onSave = { viewModel.closePhoto(save = true) },
            onDelete = { viewModel.closePhoto() },
            onDismiss = { confirmingClose = false },
        )
    }
    if (state.askingImport) {
        UnsavedPhotoDialog(
            onSave = { viewModel.answerImport(true) },
            onDelete = { viewModel.answerImport(false) },
            onDismiss = { viewModel.answerImport(null) },
        )
    }
    if (warning) state.photo?.let { photo ->
        CameraRecognitionWarning(photo, recognition, { viewModel.recognition.retry(photo) },
            { viewModel.recognition.recognize(photo) }, { warning = false })
    }
    if (blocked != null) {
        AlertDialog(
            onDismissRequest = viewModel.recognition::dismissBlock,
            title = { Text(stringResource(R.string.camera_cannot_recognize, blocked.status.language.displayName())) },
            text = { Text(blocked.status.message(context, scanning = true), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.recognition.dismissBlock()
                    if (blocked.status.requiresDevice && !blocked.status.deviceReady) onOpenModels(blocked.status.language)
                    else onOpenDictionaries(blocked.status.language)
                }) { Text(stringResource(com.vpr.screenlate.overlay.R.string.installation_open)) }
            },
            dismissButton = { TextButton(onClick = viewModel.recognition::dismissBlock) { Text(stringResource(R.string.action_close)) } },
        )
    }
    val hasWarning = cameraHasWarning(state.photo, recognition?.work)
    val working = (recognition?.work as? PhotoWork.Running)?.documentId == state.photo?.id && state.photo != null
    val actions = buildList {
        val photo = state.photo
        if (state.live) {
            if (flashAvailable) add(CameraAction(flash.icon, stringResource(flash.label), !state.busy) { viewModel.session.flash.value = flash.next })
        } else if (photo != null) {
            add(CameraAction(R.drawable.ic_photo_camera, stringResource(R.string.camera_new_photo), !state.busy, run = viewModel::newPhoto))
            add(CameraAction(R.drawable.ic_download, stringResource(R.string.camera_export), !state.busy && !state.exporting, inMenu = true,
                run = viewModel::exportPhoto))
            // Also with automatic saving, after it failed for this photo.
            if (!photo.saved) {
                add(CameraAction(R.drawable.ic_library_add, stringResource(R.string.camera_save_history), !state.busy, inMenu = true,
                    run = viewModel::saveToHistory))
            }
        }
        // As the round button: a photo opened from the history meanwhile would be replaced by the one being taken.
        add(CameraAction(R.drawable.ic_history, stringResource(R.string.camera_history), !state.busy, inMenu = true, run = onOpenHistory))
        add(CameraAction(R.drawable.ic_image, stringResource(R.string.camera_import), !state.busy, inMenu = true, run = importImage))
        add(CameraAction(R.drawable.ic_settings, stringResource(R.string.camera_settings), inMenu = true, run = onOpenSettings))
    }
    val navigation = state.photo?.let { photo ->
        if (state.live) {
            CameraAction(R.drawable.ic_close, stringResource(R.string.camera_back_to_photo), !state.busy, run = viewModel::backToPhoto)
        } else {
            CameraAction(R.drawable.ic_close, stringResource(R.string.camera_close), !state.busy) {
                if (closingAsks(photo)) confirmingClose = true else viewModel.closePhoto()
            }
        }
    }
    Scaffold(
        topBar = { CameraToolbar(actions, navigation, onMenu = { open -> toolbarMenu = open }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (!state.loaded) {
                if (state.loadFailed) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.camera_photo_unreadable))
                        Button(onClick = viewModel::retryLoading) { Text(stringResource(R.string.update_retry)) }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.camera_opening), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (state.live) {
                CameraPermission(viewModel.session, onImport = importImage, importEnabled = !state.busy) {
                    LiveCamera(
                        flash, state.busy, { flashAvailable = it }, viewModel::capture,
                        start = { ImportButton(enabled = !state.busy, onClick = importImage) },
                        end = { HistoryButton(latest?.thumbnail, enabled = !state.busy, onClick = onOpenHistory) },
                    ) {
                        recognition?.let {
                            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                                CameraLanguageChip(it, viewModel.recognition::select, onOpenLanguages, { open -> languageMenu = open }, overlay = true)
                            }
                        }
                    }
                }
            } else {
                state.photo?.let { photo ->
                    Column(Modifier.fillMaxSize()) {
                        recognition?.let {
                            CameraStatusStrip(
                                photo, it, hasWarning, enabled = !state.busy && !working,
                                onSelect = viewModel.recognition::select,
                                onManageLanguages = onOpenLanguages,
                                onMenu = { open -> languageMenu = open },
                                onRecognize = { viewModel.recognition.recognize(photo) },
                                onWarning = { warning = true },
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PhotoViewer(photo, viewModel::showPhoto)
                            if (state.busy) CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermission(
    session: CameraSession,
    onImport: () -> Unit,
    importEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var blocked by remember { mutableStateOf(session.permissionBlocked) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        blocked = !allowed && context.activity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
        session.permissionBlocked = blocked
    }
    LifecycleResumeEffect(context) {
        granted = hasCameraPermission(context)
        if (granted) {
            session.permissionBlocked = false
            blocked = false
        }
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        if (!granted && !session.permissionAsked) {
            session.permissionAsked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_no_photography), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(if (blocked) R.string.camera_permission_blocked_title else R.string.camera_permission_title),
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (blocked) R.string.camera_permission_blocked else R.string.camera_permission_text),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Button(
                modifier = Modifier.padding(top = 4.dp),
                onClick = {
                    if (blocked) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        session.permissionAsked = true
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                },
            ) {
                Text(stringResource(if (blocked) R.string.camera_open_settings else R.string.camera_allow))
            }
            OutlinedButton(onClick = onImport, enabled = importEnabled) { Text(stringResource(R.string.camera_import)) }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
