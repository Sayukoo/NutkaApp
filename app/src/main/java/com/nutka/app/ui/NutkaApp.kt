package com.nutka.app.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutka.app.NutkaViewModel
import com.nutka.app.Screen
import com.nutka.app.data.DownloadsAudioScanner
import com.nutka.app.ui.components.BottomNav
import com.nutka.app.ui.screens.ExportKind
import com.nutka.app.ui.screens.ImportScreen
import com.nutka.app.ui.screens.LogScreen
import com.nutka.app.ui.screens.RecordScreen
import com.nutka.app.ui.screens.RecordingsListScreen
import com.nutka.app.ui.screens.SettingsScreen
import com.nutka.app.ui.screens.TranscriptScreen
import com.nutka.app.ui.screens.formatDuration
import com.nutka.app.ui.theme.NutkaColors

/** Same as [ActivityResultContracts.OpenDocument] but opens straight into Downloads, since that's where imported recordings usually are. */
private class OpenAudioDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = super.createIntent(context, input)
        intent.putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
        )
        return intent
    }
}

@Composable
fun NutkaApp() {
    val viewModel: NutkaViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Collected here but deliberately never read during composition — the
    // State objects are handed straight to the meter, which reads them in its
    // draw phase. That keeps a ~22 Hz microphone signal from recomposing the
    // whole app on every sample.
    val audioLevel = viewModel.audioLevel.collectAsState()
    val waveform = viewModel.waveform.collectAsState()

    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleRecord()
        else viewModel.showToast("Bez dostępu do mikrofonu nie mogę nagrywać")
    }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshDownloads()
        if (!granted) viewModel.showToast("Bez dostępu do plików nie pokażę folderu Pobrane")
    }
    val filePickerLauncher = rememberLauncherForActivityResult(OpenAudioDocument()) { uri ->
        uri?.let { viewModel.importAudio(it) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestRecordToggle() {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.toggleRecord() else recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(Modifier.fillMaxSize().background(NutkaColors.bg)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (state.screen == Screen.RECORD || state.screen == Screen.LIST || state.screen == Screen.SETTINGS) {
                    BottomNav(current = state.screen, onSelect = { screen ->
                        when (screen) {
                            Screen.RECORD -> viewModel.goRecord()
                            Screen.LIST -> viewModel.goList()
                            Screen.SETTINGS -> viewModel.goSettings()
                            else -> {}
                        }
                    })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        (fadeIn(tween(280)) + scaleIn(initialScale = 0.98f, animationSpec = tween(280)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.985f, animationSpec = tween(180)))
                    },
                    label = "screenTransition"
                ) {
                    when (it) {
                    Screen.RECORD -> RecordScreen(
                        isRecording = state.isRecording,
                        isPaused = state.isPaused,
                        audioLevel = audioLevel,
                        waveform = waveform,
                        elapsedLabel = formatDuration(state.elapsedSec),
                        bookmarkCount = state.bookmarkCount,
                        backgroundRecordingEnabled = state.settings.backgroundRecording,
                        onStartRecord = { requestRecordToggle() },
                        onStopRecord = { viewModel.toggleRecord() },
                        onCancelRecord = { viewModel.cancelRecording() },
                        onTogglePause = viewModel::togglePause,
                        onAddBookmark = viewModel::addBookmark,
                        // Opens the import screen rather than jumping straight
                        // into the system picker: the Downloads list there is
                        // the faster route to the files that actually arrive
                        // from other apps.
                        onImport = viewModel::goImport
                    )

                    Screen.LIST -> RecordingsListScreen(
                        recordings = state.recordings,
                        onOpen = viewModel::openRecording,
                        onDelete = viewModel::deleteRecording
                    )

                    Screen.TRANSCRIPT -> state.activeRecording?.let { recording ->
                        TranscriptScreen(
                            recording = recording,
                            editingSpeaker = state.editingSpeaker,
                            nameDraft = state.nameDraft,
                            autoNotion = state.settings.autoNotion,
                            keyterms = state.settings.keyterms,
                            uploadPercent = state.uploadProgress?.takeIf { it.first == recording.id }?.second,
                            initialSearchQuery = state.initialTranscriptSearch,
                            initialSegmentIndex = state.initialTranscriptSegmentIndex,
                            onConsumeInitialSearch = viewModel::consumeInitialSearch,
                            onBack = viewModel::goList,
                            onTitleChange = viewModel::setTitle,
                            onCopy = {
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("Transkrypcja", viewModel.transcriptText()))
                                viewModel.showToast("Skopiowano transkrypcję")
                            },
                            onShare = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.transcriptText())
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                                viewModel.showToast("Wyeksportowano jako plik tekstowy")
                            },
                            onExport = { kind ->
                                fun shareFile(file: java.io.File, chooserTitle: String) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
                                }
                                when (kind) {
                                    ExportKind.AUDIO -> {
                                        val audioFile = recording.filePath?.let { java.io.File(it) }
                                        if (audioFile != null && audioFile.exists()) {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", audioFile
                                            )
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                // Derived from the actual container: recordings are
                                                // AAC/ADTS (.aac) where the device supports it, and
                                                // imports keep their source extension.
                                                type = when (audioFile.extension.lowercase()) {
                                                    "aac" -> "audio/aac"
                                                    "mp3" -> "audio/mpeg"
                                                    "wav" -> "audio/wav"
                                                    "ogg" -> "audio/ogg"
                                                    "flac" -> "audio/flac"
                                                    else -> "audio/mp4"
                                                }
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Pobierz oryginalne audio"))
                                        } else {
                                            viewModel.showToast("Brak pliku audio dla tego nagrania")
                                        }
                                    }
                                    ExportKind.SUBTITLES -> {
                                        val audioPath = recording.filePath
                                        val srtFile = audioPath?.let { path ->
                                             val f = java.io.File(path)
                                             java.io.File(f.parentFile, f.nameWithoutExtension + ".srt")
                                        }
                                        if (srtFile != null && srtFile.exists()) {
                                            shareFile(srtFile, "Eksportuj napisy")
                                        } else {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, viewModel.transcriptText())
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Eksportuj transkrypcję"))
                                            viewModel.showToast("Brak napisów SRT — eksportuję tekst (włącz \"Dołącz napisy\" w Ustawieniach)")
                                        }
                                    }
                                }
                            },
                            onSendToNotion = { viewModel.sendToNotion() },
                            onRetry = { viewModel.retryTranscription(recording.id) },
                            onCancelTranscription = { viewModel.cancelTranscription(recording.id) },
                            onDelete = { viewModel.deleteRecording(recording.id) },
                            onStartEditSpeaker = viewModel::startEditSpeaker,
                            onNameDraftChange = viewModel::setNameDraft,
                            onCommitName = viewModel::commitName
                        )
                    }

                    Screen.IMPORT -> ImportScreen(
                        importing = state.importing,
                        downloads = state.downloads,
                        permissionGranted = state.downloadsPermissionGranted,
                        autoImport = state.settings.autoImportDownloads,
                        onBack = viewModel::goRecord,
                        onPickFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                        onGrantPermission = { storageLauncher.launch(DownloadsAudioScanner.requiredPermission) },
                        onRefresh = viewModel::refreshDownloads,
                        onImportDownload = viewModel::importFromDownloads,
                        onToggleAutoImport = viewModel::toggleAutoImportDownloads
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        onConnectNotion = { token, dbId -> viewModel.setNotionToken(token); viewModel.setNotionDatabaseId(dbId) },
                        onDisconnectNotion = viewModel::disconnectNotion,
                        onToggleAutoNotion = viewModel::toggleAutoNotion,
                        onToggleBackgroundRecording = viewModel::toggleBackgroundRecording,
                        onSetCallRecording = viewModel::setCallRecording,
                        onToggleAutoRecordCalls = viewModel::toggleAutoRecordCalls,
                        onShowMessage = viewModel::showToast,
                        onSetPrimaryLanguage = viewModel::setPrimaryLanguage,
                        onToggleTagAudioEvents = viewModel::toggleTagAudioEvents,
                        onToggleIncludeSubtitles = viewModel::toggleIncludeSubtitles,
                        onToggleNoVerbatim = viewModel::toggleNoVerbatim,
                        onToggleAssignSpeakersFromLibrary = viewModel::toggleAssignSpeakersFromLibrary,
                        onSetExpectedSpeakers = viewModel::setExpectedSpeakers,
                        onSetKeyterms = viewModel::setKeyterms,
                        onSetElevenLabsApiKey = viewModel::setElevenLabsApiKey,
                        onOpenLog = viewModel::goLog
                    )

                    Screen.LOG -> LogScreen(
                        lines = state.logLines,
                        onBack = viewModel::goSettings,
                        onShare = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.logLines.joinToString("\n"))
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Udostępnij dziennik"))
                        },
                        onClear = viewModel::clearLog
                    )
                    }
                }
            }
        }

        state.toast?.let { message ->
            // Entrance animation keyed per message
            val shown = remember(message) { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(message) {
                shown.animateTo(
                    1f,
                    androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 380f)
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 88.dp)
            ) {
                Text(
                    message,
                    color = NutkaColors.bg,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = shown.value
                            val s = 0.9f + 0.1f * shown.value
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(999.dp))
                        .background(NutkaColors.neutral900)
                        .border(1.dp, NutkaColors.neutral700, RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}
