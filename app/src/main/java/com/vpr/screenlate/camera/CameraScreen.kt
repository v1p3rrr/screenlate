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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpr.screenlate.R
import com.vpr.screenlate.ui.components.ConfirmDelete
import com.vpr.screenlate.ui.components.TooltipIconButton

/** Camera capture and the persistent current photograph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val flash by viewModel.session.flash.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmingDelete by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onForeground()
        onPauseOrDispose { viewModel.onBackground() }
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                resources.getString(
                    when (event) {
                        CameraError.CAPTURE -> R.string.camera_capture_failed
                        CameraError.DELETE -> R.string.camera_delete_failed
                        CameraError.LOAD -> R.string.camera_photo_unreadable
                    },
                ),
            )
        }
    }
    BackHandler(state.takingNew && state.photo != null) { viewModel.backToPhoto() }
    if (confirmingDelete) {
        ConfirmDelete(
            stringResource(R.string.camera_delete_title),
            stringResource(R.string.camera_delete_text),
            onDelete = viewModel::deletePhoto,
            onDismiss = { confirmingDelete = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_title)) },
                actions = {
                    if (state.live) {
                        if (flashAvailable) {
                            TooltipIconButton(
                                flash.icon, stringResource(flash.label),
                                onClick = { viewModel.session.flash.value = flash.next },
                                enabled = !state.busy,
                            )
                        }
                        if (state.photo != null) {
                            TooltipIconButton(
                                R.drawable.ic_close, stringResource(R.string.camera_back_to_photo),
                                viewModel::backToPhoto, enabled = !state.busy,
                            )
                        }
                    } else if (state.photo != null) {
                        TooltipIconButton(
                            R.drawable.ic_photo_camera, stringResource(R.string.camera_new_photo),
                            viewModel::newPhoto, enabled = !state.busy,
                        )
                        TooltipIconButton(
                            R.drawable.ic_delete, stringResource(R.string.camera_delete),
                            onClick = { confirmingDelete = true }, enabled = !state.busy,
                        )
                    }
                },
            )
        },
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
                    CircularProgressIndicator()
                }
            } else if (state.live) {
                CameraPermission(viewModel.session) {
                    LiveCamera(flash, state.busy, { flashAvailable = it }, viewModel::capture)
                }
            } else {
                state.photo?.let { PhotoViewer(it, viewModel::showPhoto) }
                if (state.busy) CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun CameraPermission(session: CameraSession, content: @Composable () -> Unit) {
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(if (blocked) R.string.camera_permission_blocked else R.string.camera_permission_text))
            Button(
                modifier = Modifier.padding(top = 16.dp),
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
