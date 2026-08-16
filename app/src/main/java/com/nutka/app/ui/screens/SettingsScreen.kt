package com.nutka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.SettingsState
import com.nutka.app.ui.components.KeytermsField
import com.nutka.app.ui.components.SettingsSectionLabel
import com.nutka.app.ui.components.SettingsToggleRow
import com.nutka.app.ui.theme.NutkaColors

private val LANGUAGES = listOf(
    "auto" to "Wykryj automatycznie",
    "pl" to "Polski",
    "en" to "English",
    "de" to "Deutsch",
    "fr" to "Français",
    "es" to "Español",
    "it" to "Italiano",
    "uk" to "Українська"
)

@Composable
fun SettingsScreen(
    settings: SettingsState,
    onConnectNotion: (token: String, databaseId: String) -> Unit,
    onDisconnectNotion: () -> Unit,
    onToggleAutoNotion: () -> Unit,
    onToggleBackgroundRecording: () -> Unit,
    onSetPrimaryLanguage: (String) -> Unit,
    onToggleTagAudioEvents: () -> Unit,
    onToggleIncludeSubtitles: () -> Unit,
    onToggleNoVerbatim: () -> Unit,
    onToggleAssignSpeakersFromLibrary: () -> Unit,
    onSetKeyterms: (List<String>) -> Unit,
    onSetElevenLabsApiKey: (String) -> Unit,
    onOpenLog: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Ustawienia", style = MaterialTheme.typography.headlineSmall) }

        item {
            NotionCard(
                connected = settings.notionConnected,
                databaseId = settings.notionDatabaseId,
                onConnect = onConnectNotion,
                onDisconnect = onDisconnectNotion
            )
        }

        item {
            SettingsToggleRow(
                title = "Automatyczny eksport",
                description = "Wysyłaj transkrypcję do Notion od razu po jej ukończeniu",
                checked = settings.autoNotion,
                onCheckedChange = { onToggleAutoNotion() }
            )
        }

        item {
            SettingsToggleRow(
                title = "Nagrywanie w tle",
                description = "Kontynuuj nagrywanie z zablokowanym ekranem i przy niskim poziomie baterii",
                checked = settings.backgroundRecording,
                onCheckedChange = { onToggleBackgroundRecording() }
            )
        }

        item { SettingsSectionLabel("Transkrypcja") }

        item {
            LanguageDropdown(selected = settings.primaryLanguage, onSelect = onSetPrimaryLanguage)
        }

        item {
            SettingsToggleRow(
                title = "Oznaczaj zdarzenia dźwiękowe",
                description = "Np. [śmiech], [muzyka] w tekście transkrypcji",
                checked = settings.tagAudioEvents,
                onCheckedChange = { onToggleTagAudioEvents() }
            )
        }
        item {
            SettingsToggleRow(
                title = "Dołącz napisy",
                description = "Wygeneruj plik napisów (SRT) obok transkrypcji",
                checked = settings.includeSubtitles,
                onCheckedChange = { onToggleIncludeSubtitles() }
            )
        }
        item {
            SettingsToggleRow(
                title = "Bez zapisu dosłownego",
                description = "Pomijaj wypełniacze typu \"yyy\", powtórzenia i poprawki w mowie",
                checked = settings.noVerbatim,
                onCheckedChange = { onToggleNoVerbatim() }
            )
        }
        item {
            SettingsToggleRow(
                title = "Przypisz mówców z biblioteki",
                description = "Rozpoznawaj znane głosy i podpisuj je automatycznie",
                checked = settings.assignSpeakersFromLibrary,
                onCheckedChange = { onToggleAssignSpeakersFromLibrary() }
            )
        }

        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp)
            ) {
                Text("Słowa kluczowe", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Nazwy własne i żargon, które model powinien rozpoznawać poprawnie",
                    fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 3.dp, bottom = 10.dp)
                )
                KeytermsField(keyterms = settings.keyterms, onKeytermsChange = onSetKeyterms)
            }
        }

        item { SettingsSectionLabel("ElevenLabs (opcjonalnie)") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Puste pole = transkrypcja wyłącznie na urządzeniu, nic nie opuszcza telefonu. Wklej swój klucz API ElevenLabs (elevenlabs.io), aby audio było transkrybowane po ich stronie — potrzebne też, żeby transkrybować importowane pliki i uzyskać prawdziwe rozdzielenie mówców.",
                    fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.6f)
                )
                SettingsTextField(value = settings.elevenLabsApiKey, onValueChange = onSetElevenLabsApiKey, placeholder = "Klucz API ElevenLabs", password = true)
            }
        }

        item { SettingsSectionLabel("Diagnostyka") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(NutkaColors.surface)
                    .clickable(onClick = onOpenLog)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dziennik zdarzeń", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Szczegółowy log nagrywania, transkrypcji i wysyłki do Notion",
                        fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = NutkaColors.text.copy(alpha = 0.4f))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = NutkaColors.text.copy(alpha = 0.4f))
                Text(
                    "  Baza danych Notion: ${settings.notionDatabaseId.ifBlank { "nieskonfigurowana" }}",
                    fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun NotionCard(
    connected: Boolean,
    databaseId: String,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var dbInput by remember { mutableStateOf(databaseId) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(NutkaColors.neutral900),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = NutkaColors.bg, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Notion", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (connected) "Połączono · Notatki głosowe" else "Nie połączono",
                    fontSize = 12.sp, color = NutkaColors.text.copy(alpha = 0.6f)
                )
            }
            if (connected) {
                OutlinedButton(onClick = onDisconnect, shape = MaterialTheme.shapes.extraLarge) {
                    Text("Rozłącz", fontSize = 12.5.sp)
                }
            } else {
                Button(
                    onClick = { showForm = !showForm },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.accent, contentColor = NutkaColors.bg)
                ) { Text("Połącz", fontSize = 12.5.sp) }
            }
        }
        if (!connected && showForm) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsTextField(value = tokenInput, onValueChange = { tokenInput = it }, placeholder = "Token integracji (secret_...)", password = true)
                SettingsTextField(value = dbInput, onValueChange = { dbInput = it }, placeholder = "ID bazy danych")
                Button(
                    onClick = { if (tokenInput.isNotBlank() && dbInput.isNotBlank()) { onConnect(tokenInput, dbInput); showForm = false } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.accent, contentColor = NutkaColors.bg)
                ) { Text("Zapisz połączenie", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun SettingsTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NutkaColors.bg,
            focusedContainerColor = NutkaColors.bg,
            unfocusedBorderColor = NutkaColors.divider,
            focusedBorderColor = NutkaColors.accent
        )
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = LANGUAGES.firstOrNull { it.first == selected }?.second ?: LANGUAGES[0].second

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp)
    ) {
        Text("Język podstawowy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = NutkaColors.bg,
                    focusedContainerColor = NutkaColors.bg,
                    unfocusedBorderColor = NutkaColors.divider,
                    focusedBorderColor = NutkaColors.accent
                )
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.exposedDropdownSize()
            ) {
                LANGUAGES.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(code); expanded = false })
                }
            }
        }
    }
}
