package com.nutka.app.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutka.app.NutkaViewModel
import com.nutka.app.Screen
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

@Composable
fun NutkaApp() {
    val viewModel: NutkaViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val recordAudioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleRecord()
    }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
            containerColor = NutkaColors.bg,
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
                when (state.screen) {
                    Screen.RECORD -> RecordScreen(
                        isRecording = state.isRecording,
                        isPaused = state.isPaused,
                        elapsedLabel = formatDuration(state.elapsedSec),
                        bookmarkCount = state.bookmarkCount,
                        backgroundRecordingEnabled = state.settings.backgroundRecording,
                        onToggleRecord = { if (state.isRecording) viewModel.toggleRecord() else requestRecordToggle() },
                        onTogglePause = viewModel::togglePause,
                        onAddBookmark = viewModel::addBookmark,
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
                                                type = "audio/mp4"
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
                            onDelete = { viewModel.deleteRecording(recording.id) },
                            onStartEditSpeaker = viewModel::startEditSpeaker,
                            onNameDraftChange = viewModel::setNameDraft,
                            onCommitName = viewModel::commitName
                        )
                    }

                    Screen.IMPORT -> ImportScreen(
                        importing = state.importing,
                        onBack = viewModel::goRecord,
                        onPickFile = { filePickerLauncher.launch(arrayOf("audio/*")) }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        onConnectNotion = { token, dbId -> viewModel.setNotionToken(token); viewModel.setNotionDatabaseId(dbId) },
                        onDisconnectNotion = viewModel::disconnectNotion,
                        onToggleAutoNotion = viewModel::toggleAutoNotion,
                        onToggleBackgroundRecording = viewModel::toggleBackgroundRecording,
                        onSetPrimaryLanguage = viewModel::setPrimaryLanguage,
                        onToggleTagAudioEvents = viewModel::toggleTagAudioEvents,
                        onToggleIncludeSubtitles = viewModel::toggleIncludeSubtitles,
                        onToggleNoVerbatim = viewModel::toggleNoVerbatim,
                        onToggleAssignSpeakersFromLibrary = viewModel::toggleAssignSpeakersFromLibrary,
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

        state.toast?.let { message ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 84.dp)
            ) {
                Text(
                    message,
                    color = NutkaColors.bg,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NutkaColors.neutral900)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}
