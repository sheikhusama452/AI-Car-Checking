package com.aicarchecking.ui.inspection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicarchecking.domain.model.CaptureMode
import com.aicarchecking.ui.theme.Brand
import com.aicarchecking.ui.common.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/**
 * In-app capture with the AI Inspection Coach overlay. Photos/videos are written straight into
 * app-managed temporary storage; nothing is saved to the public gallery.
 */
@Composable
fun CaptureScreen(mode: CaptureMode, onDone: () -> Unit) {
    val vm = appViewModel { c, h -> CaptureViewModel(c, h) }
    val context = LocalContext.current
    val error by vm.error.collectAsStateWithLifecycle()
    val needed = if (mode == CaptureMode.AUDIO) Manifest.permission.RECORD_AUDIO else Manifest.permission.CAMERA
    var granted by remember { mutableStateOf(context.hasPermission(needed)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[needed] == true
        asked = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!granted) {
            PermissionRationale(
                mode = mode,
                denied = asked,
                onRequest = {
                    val perms = buildList {
                        add(needed)
                        if (mode == CaptureMode.VIDEO) add(Manifest.permission.RECORD_AUDIO) // optional: sound helps smoke/cold-start analysis
                    }
                    launcher.launch(perms.toTypedArray())
                },
                onCancel = onDone,
            )
        } else if (mode == CaptureMode.AUDIO) {
            AudioRecorderContent(vm, onDone)
        } else {
            CameraContent(vm, mode, onDone)
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            text = { Text(it) },
        )
    }
}

@Composable
private fun PermissionRationale(mode: CaptureMode, denied: Boolean, onRequest: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        val (title, body) = when (mode) {
            CaptureMode.AUDIO -> "Microphone access" to "AI Car Checking needs the microphone to record engine sound for this step. Recordings stay on your phone unless you choose to analyze them."
            CaptureMode.VIDEO -> "Camera access" to "The camera is used to record inspection video. Microphone access is optional and lets you capture engine/exhaust sound. Media stays on your phone unless you choose to analyze it."
            else -> "Camera access" to "The camera is used to photograph the car for inspection. Photos are stored privately in the app and never uploaded automatically."
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(body, color = Color.White.copy(alpha = 0.85f))
        if (denied) {
            Spacer(Modifier.height(10.dp))
            Text("Permission was denied. You can still add evidence from your gallery with 'Add Evidence', or enable the permission in Android Settings.", color = Brand.Amber)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Allow") }
        TextButton(onClick = onCancel) { Text("Not now", color = Color.White) }
    }
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
}

@SuppressLint("MissingPermission")
@Composable
private fun CameraContent(vm: CaptureViewModel, mode: CaptureMode, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val videoCapture = remember {
        VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
    }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mode) {
        runCatching {
            val provider = cameraProvider(context)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview,
                if (mode == CaptureMode.VIDEO) videoCapture else imageCapture,
            )
        }.onFailure { cameraError = "Camera unavailable. Close other camera apps and try again, or add evidence from your gallery." }
    }
    DisposableEffect(Unit) { onDispose { recording?.stop() } }

    LaunchedEffect(recording) {
        seconds = 0
        while (recording != null) {
            delay(1000)
            seconds++
            if (seconds >= MAX_VIDEO_SECONDS) recording?.stop()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // Framing guide for the coach
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth(0.86f).height(260.dp)
                .border(2.dp, Brand.Cyan.copy(alpha = 0.8f), MaterialTheme.shapes.medium),
        )
        Surface(color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Column(Modifier.statusBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Inspection Coach · ${vm.step.title}", color = Brand.Amber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                }
                vm.step.coach.take(3).forEach { Text("• $it", color = Color.White, style = MaterialTheme.typography.bodySmall) }
                cameraError?.let { Text(it, color = Brand.Red) }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (mode == CaptureMode.VIDEO) {
                Text(
                    if (recording != null) "● REC ${seconds}s / ${MAX_VIDEO_SECONDS}s" else "Keep clips short (10–30 s)",
                    color = if (recording != null) Brand.Red else Color.White,
                )
                Spacer(Modifier.height(10.dp))
            }
            fun onShutter() {
                        if (mode == CaptureMode.PHOTO) {
                            val file = vm.newFile("jpg") ?: return
                            busy = true
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        vm.onCaptured(file, "image/jpeg") { busy = false; onDone() }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        busy = false
                                        file.delete()
                                        vm.onError("Couldn't save the photo. Please try again.")
                                    }
                                },
                            )
                        } else {
                            val active = recording
                            if (active != null) {
                                active.stop()
                            } else {
                                val file = vm.newFile("mp4") ?: return
                                val pending = videoCapture.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
                                    .let { if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) it.withAudioEnabled() else it }
                                recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                    if (event is VideoRecordEvent.Finalize) {
                                        recording = null
                                        if (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
                                            busy = true
                                            vm.onCaptured(file, "video/mp4") { busy = false; onDone() }
                                        } else {
                                            file.delete()
                                            vm.onError("Video recording failed. Please try again.")
                                        }
                                    }
                                }
                            }
                        }
            }
            Box(
                Modifier.size(78.dp).clip(CircleShape)
                    .background(if (recording != null) Brand.Red else Color.White)
                    .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(enabled = !busy && cameraError == null, onClickLabel = "Capture") { onShutter() },
            )
        }
    }
}

private const val MAX_VIDEO_SECONDS = 60
private const val MAX_AUDIO_SECONDS = 30

@Composable
private fun AudioRecorderContent(vm: CaptureViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var seconds by remember { mutableIntStateOf(0) }

    fun stop(save: Boolean) {
        val r = recorder ?: return
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file
        if (save && ok && f != null) vm.onCaptured(f, "audio/mp4", onDone) else f?.delete()
    }

    DisposableEffect(Unit) { onDispose { stop(save = false) } }
    LaunchedEffect(recorder) {
        seconds = 0
        while (recorder != null) {
            delay(1000)
            seconds++
            if (seconds >= MAX_AUDIO_SECONDS) stop(save = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AI Inspection Coach · ${vm.step.title}", color = Brand.Amber, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        vm.step.coach.forEach { Text("• $it", color = Color.White, style = MaterialTheme.typography.bodyMedium) }
        vm.step.safetyNote?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Brand.Amber) }
        Spacer(Modifier.height(30.dp))
        Text(if (recorder != null) "● Recording ${seconds}s (aim for 10–30 s)" else "Ready", color = if (recorder != null) Brand.Red else Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (recorder != null) {
                    stop(save = true)
                } else {
                    val f = vm.newFile("m4a") ?: return@Button
                    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                    runCatching {
                        r.setAudioSource(MediaRecorder.AudioSource.MIC)
                        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        r.setAudioSamplingRate(44_100)
                        r.setAudioEncodingBitRate(128_000)
                        r.setOutputFile(f.absolutePath)
                        r.prepare()
                        r.start()
                    }.onSuccess {
                        file = f
                        recorder = r
                    }.onFailure {
                        r.release()
                        f.delete()
                        vm.onError("Microphone unavailable. Close other apps using the microphone and try again.")
                    }
                }
            },
            modifier = Modifier.size(width = 220.dp, height = 60.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (recorder != null) Brand.Red else Brand.Cyan),
        ) {
            Icon(if (recorder != null) Icons.Filled.Stop else Icons.Filled.Mic, null)
            Spacer(Modifier.size(8.dp))
            Text(if (recorder != null) "Stop & save" else "Start recording")
        }
        TextButton(onClick = { stop(save = false); onDone() }) { Text("Cancel", color = Color.White) }
    }
}
