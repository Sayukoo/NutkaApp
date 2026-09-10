package com.nutka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.DownloadedAudio
import com.nutka.app.ui.components.HintTooltip
import com.nutka.app.ui.components.SettingIcon
import com.nutka.app.ui.components.SettingsSectionLabel
import com.nutka.app.ui.components.SettingsToggleRow
import com.nutka.app.ui.theme.NutkaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Import screen, built around the Downloads folder rather than the system file
 * picker — that is where audio shared from other apps actually lands, so the
 * files are listed here directly and import is one tap. The picker stays at the
 * bottom for anything the media scanner didn't catalogue as audio.
 */
@Composable
fun ImportScreen(
    importing: Boolean,
    downloads: List<DownloadedAudio>,
    permissionGranted: Boolean,
    autoImport: Boolean,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onGrantPermission: () -> Unit,
    onRefresh: () -> Unit,
    onImportDownload: (DownloadedAudio) -> Unit,
    onToggleAutoImport: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HintTooltip("Wróć do nagrywania") {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(NutkaColors.surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", modifier = Modifier.size(20.dp)) }
            }
            Text("Importuj plik", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (permissionGranted) {
                HintTooltip("Sprawdź folder Pobrane jeszcze raz") {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(NutkaColors.surface)
                            .clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Odśwież", modifier = Modifier.size(18.dp)) }
                }
            }
        }

        if (importing) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NutkaColors.accent)
                Text("  Importowanie…", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.7f))
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SettingsSectionLabel("Z FOLDERU POBRANE") }

            if (!permissionGranted) {
                item { PermissionCard(onGrantPermission) }
            } else {
                item {
                    SettingsToggleRow(
                        title = "Importuj nowe automatycznie",
                        checked = autoImport,
                        onCheckedChange = { onToggleAutoImport() },
                        icon = Icons.Default.Download,
                        tooltip = "Każdy nowy plik audio, który pojawi się w Pobranych, zostanie sam wciągnięty " +
                            "i wysłany do transkrypcji, gdy tylko wrócisz do Nutki. Pliki, które są tam już teraz, " +
                            "zostają nietknięte — inaczej jedno kliknięcie wysłałoby całe archiwum do ElevenLabs."
                    )
                }
                if (downloads.isEmpty()) {
                    item { EmptyDownloads() }
                } else {
                    items(downloads, key = { it.id }) { file ->
                        DownloadRow(file = file, onClick = { onImportDownload(file) })
                    }
                }
            }

            item { SettingsSectionLabel("INNE ŹRÓDŁO") }
            item {
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NutkaColors.text),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("Wybierz plik ręcznie", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(Icons.Default.Download)
            Text(
                "Pokaż pliki z Pobranych",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
        }
        Text(
            "Nutka potrzebuje zgody na czytanie plików audio, żeby wyświetlić tu nagrania z folderu Pobrane.",
            fontSize = 12.5.sp,
            color = NutkaColors.text.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.padding(top = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.accent, contentColor = NutkaColors.bg),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
        ) { Text("Zezwól na dostęp", fontSize = 13.sp) }
    }
}

@Composable
private fun EmptyDownloads() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .border(1.dp, NutkaColors.divider, RoundedCornerShape(20.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Brak plików audio w Pobranych",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Text(
            "Zapisz nagranie w telefonie i wróć tutaj — albo wybierz plik ręcznie poniżej.",
            fontSize = 12.5.sp,
            color = NutkaColors.text.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DownloadRow(file: DownloadedAudio, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NutkaColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIcon(Icons.Default.AudioFile, tint = NutkaColors.accent2_700)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    if (file.durationSec > 0) append(formatDuration(file.durationSec)).append("  ·  ")
                    append(formatFileSize(file.sizeBytes))
                    append("  ·  ")
                    append(downloadDateFormat.format(Date(file.addedAtMillis)))
                },
                fontSize = 11.5.sp,
                color = NutkaColors.text.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.Default.FileUpload,
            contentDescription = "Transkrybuj",
            tint = NutkaColors.accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val downloadDateFormat = SimpleDateFormat("d.MM, HH:mm", Locale.forLanguageTag("pl"))

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(Locale.forLanguageTag("pl"), bytes / (1024.0 * 1024.0))
    else -> "${(bytes / 1024L).coerceAtLeast(1L)} kB"
}
