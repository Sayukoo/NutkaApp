package com.nutka.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.data.SettingsState
import com.nutka.app.service.CallRecordingAccessibilityService
import com.nutka.app.ui.components.HintTooltip
import com.nutka.app.ui.components.KeytermsField
import com.nutka.app.ui.components.SettingIcon
import com.nutka.app.ui.components.SettingsActionRow
import com.nutka.app.ui.components.SettingsSectionLabel
import com.nutka.app.ui.components.SettingsToggleRow
import com.nutka.app.ui.components.Tag
import com.nutka.app.ui.components.TagStyle
import com.nutka.app.ui.theme.NutkaColors

private val LANGUAGES = listOf(
    "pl" to "Polski",
    "auto" to "Wykryj automatycznie",
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
    onSetCallRecording: (Boolean) -> Unit,
    onToggleAutoRecordCalls: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSetPrimaryLanguage: (String) -> Unit,
    onToggleTagAudioEvents: () -> Unit,
    onToggleIncludeSubtitles: () -> Unit,
    onToggleNoVerbatim: () -> Unit,
    onToggleAssignSpeakersFromLibrary: () -> Unit,
    onSetExpectedSpeakers: (Int) -> Unit,
    onSetKeyterms: (List<String>) -> Unit,
    onSetElevenLabsApiKey: (String) -> Unit,
    onOpenLog: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("Ustawienia", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Najedź lub przytrzymaj opcję, aby zobaczyć szczegóły",
                    fontSize = 12.sp,
                    color = NutkaColors.text.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // ---- Integracje ----------------------------------------------------------
        item { SettingsSectionLabel("INTEGRACJE") }
        item {
            NotionCard(
                connected = settings.notionConnected,
                databaseId = settings.notionDatabaseId,
                autoExportOn = settings.autoNotion,
                onConnect = onConnectNotion,
                onDisconnect = onDisconnectNotion
            )
        }
        item {
            SettingsToggleRow(
                title = "Automatyczny eksport do Notion",
                checked = settings.autoNotion,
                onCheckedChange = { onToggleAutoNotion() },
                icon = Icons.Default.Description,
                tooltip = "Gdy włączone, każda gotowa transkrypcja jest od razu wysyłana do połączonej bazy Notion — bez ręcznego klikania „Wyślij do Notion”. Wymaga połączenia z Notion powyżej."
            )
        }

        // ---- Nagrywanie ------------------------------------------------------------
        item { SettingsSectionLabel("NAGRYWANIE") }
        item {
            SettingsToggleRow(
                title = "Nagrywanie w tle",
                checked = settings.backgroundRecording,
                onCheckedChange = { onToggleBackgroundRecording() },
                icon = Icons.Default.GraphicEq,
                tooltip = "Kontynuuje nagrywanie przy zablokowanym ekranie i gdy przejdziesz do innej aplikacji. Nagranie jest chronione powiadomieniem na pierwszym planie, więc Android nie ubije go przy niskiej baterii."
            )
        }
        item { BatteryOptimizationRow() }

        // ---- Rozmowy telefoniczne --------------------------------------------------
        item { SettingsSectionLabel("ROZMOWY TELEFONICZNE") }
        item {
            CallRecordingCard(
                enabled = settings.callRecording,
                autoRecord = settings.autoRecordCalls,
                onSetEnabled = onSetCallRecording,
                onToggleAutoRecord = onToggleAutoRecordCalls,
                onShowMessage = onShowMessage
            )
        }

        // ---- Transkrypcja --------------------------------------------------------
        item { SettingsSectionLabel("TRANSKRYPCJA") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface)
            ) {
                LanguageRow(selected = settings.primaryLanguage, onSelect = onSetPrimaryLanguage)
                HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
                SettingsToggleRow(
                    title = "Oznaczaj zdarzenia dźwiękowe",
                    checked = settings.tagAudioEvents,
                    onCheckedChange = { onToggleTagAudioEvents() },
                    icon = Icons.Default.CenterFocusStrong,
                    tooltip = "Model dopisze do tekstu znaczniki dźwięków spoza mowy, np. [śmiech], [muzyka], [aplauz] — łatwiej potem czytać kontekst spotkania."
                )
                HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
                SettingsToggleRow(
                    title = "Dołącz napisy (SRT)",
                    checked = settings.includeSubtitles,
                    onCheckedChange = { onToggleIncludeSubtitles() },
                    icon = Icons.Default.Subtitles,
                    tooltip = "Obok transkrypcji powstanie plik .srt z znacznikami czasu, zsynchronizowany z audio. Możesz go potem wyeksportować z ekranu transkrypcji (menu eksportu)."
                )
                HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
                SettingsToggleRow(
                    title = "Bez zapisu dosłownego",
                    checked = settings.noVerbatim,
                    onCheckedChange = { onToggleNoVerbatim() },
                    icon = Icons.AutoMirrored.Filled.Article,
                    tooltip = "Czyści przekaz z wypełniaczy („yyy”, „no czyli”), poprawek i powtórzeń. Tekst jest bardziej zwięzły, ale wiernie oddaje styl mówców."
                )
                HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
                SettingsToggleRow(
                    title = "Przypisz mówców z biblioteki",
                    checked = settings.assignSpeakersFromLibrary,
                    onCheckedChange = { onToggleAssignSpeakersFromLibrary() },
                    icon = Icons.Default.Groups,
                    tooltip = "Rozpoznaje znane głosy z wcześniejszych nagrań i automatycznie podpisuje je tymi samymi nazwami. Przydatne przy cyklicznych spotkaniach z tymi samymi osobami."
                )
                HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
                SpeakerCountRow(selected = settings.expectedSpeakers, onSelect = onSetExpectedSpeakers)
                HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
                KeytermsRow(keyterms = settings.keyterms, onKeytermsChange = onSetKeyterms)
            }
        }

        // ---- ElevenLabs -----------------------------------------------------------
        item { SettingsSectionLabel("ELEVENLABS API") }
        item {
            ElevenLabsCard(
                apiKey = settings.elevenLabsApiKey,
                onApiKeyChange = onSetElevenLabsApiKey
            )
        }

        // ---- Diagnostyka ----------------------------------------------------------
        item { SettingsSectionLabel("DIAGNOSTYKA") }
        item {
            SettingsActionRow(
                title = "Dziennik zdarzeń",
                onClick = onOpenLog,
                icon = Icons.Default.Bookmarks,
                tooltip = "Szczegółowa historia nagrywania, wysyłki do ElevenLabs i eksportu do Notion — wraz ze znacznikami czasu i błędami. Można udostępnić lub wyczyścić.",
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = NutkaColors.text.copy(alpha = 0.35f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }

        item {
            HintTooltip(
                "ID bazy danych Notion, do której trafiają transkrypcje. Skonfigurujesz je przyciskiem „Połącz” na karcie Notion u góry."
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(13.dp), tint = NutkaColors.text.copy(alpha = 0.4f))
                    Text(
                        "  Baza danych Notion: ${settings.notionDatabaseId.ifBlank { "nieskonfigurowana" }}",
                        fontSize = 11.sp, color = NutkaColors.text.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

/**
 * The one setting that decides whether a long recording actually survives.
 *
 * A microphone foreground service plus a wake lock is everything the platform
 * itself asks for, but most OEM ROMs (Xiaomi, Samsung, Oppo, Huawei…) run an
 * extra power manager on top that will still freeze or kill a backgrounded app
 * after a while unless it is on the "unrestricted" list. Nothing in the app can
 * opt itself out — only the user can, from the system dialog this row opens.
 *
 * The state is re-read when the user comes back from that dialog, so the tag
 * flips to "Wyłączona" without needing to leave and re-enter Settings.
 */
@SuppressLint("BatteryLife")
@Composable
private fun BatteryOptimizationRow() {
    val context = LocalContext.current
    fun isExempt(): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    var exempt by remember { mutableStateOf(isExempt()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exempt = isExempt()
    }

    val requestExemption: () -> Unit = {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName)
        )
        val opened = runCatching { launcher.launch(direct) }.isSuccess
        if (!opened) {
            // Some ROMs hide the per-app dialog; fall back to the full list.
            runCatching { launcher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    SettingsActionRow(
        title = "Optymalizacja baterii",
        icon = Icons.Default.Bolt,
        tooltip = "Android i nakładki producentów potrafią uśpić aplikację w tle i przerwać długie nagranie. " +
            "Dodaj Nutkę do wyjątków, a nagrywanie będzie działać także przy wygaszonym ekranie i przez wiele godzin.",
        onClick = if (exempt) null else requestExemption,
        trailing = {
            Tag(
                text = if (exempt) "Wyłączona" else "Włącz wyjątek",
                style = if (exempt) TagStyle.ACCENT2 else TagStyle.OUTLINE
            )
        }
    )
}

// ---- Cards -------------------------------------------------------------------

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/**
 * Phone-call recording needs three things from the user, and this card walks
 * through them in order: the phone-state + microphone permissions (asked when
 * the switch is turned on), the accessibility service (a system screen only
 * the user can flip — Android's condition for hearing the mic mid-call), and
 * the speaker during the call itself, which nothing in the app can switch on.
 */
@Composable
private fun CallRecordingCard(
    enabled: Boolean,
    autoRecord: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onToggleAutoRecord: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    fun serviceOn(): Boolean = runCatching { CallRecordingAccessibilityService.isEnabled(context) }.getOrDefault(false)

    var serviceEnabled by remember { mutableStateOf(serviceOn()) }
    val accessibilityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        serviceEnabled = serviceOn()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = context.hasPermission(Manifest.permission.READ_PHONE_STATE) &&
            context.hasPermission(Manifest.permission.RECORD_AUDIO)
        if (granted) onSetEnabled(true)
        else onShowMessage("Bez dostępu do stanu telefonu i mikrofonu nie nagram rozmowy")
    }

    val enable: () -> Unit = {
        val missing = listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO)
            .filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) onSetEnabled(true) else permissionLauncher.launch(missing.toTypedArray())
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface)
    ) {
        SettingsToggleRow(
            title = "Nagrywanie rozmów",
            checked = enabled,
            onCheckedChange = { on -> if (on) enable() else onSetEnabled(false) },
            icon = Icons.Default.Call,
            tooltip = "Nagrywa rozmowy telefoniczne, w których bierzesz udział, i transkrybuje je przez ElevenLabs jak zwykłe nagranie. Rozmówca musi wiedzieć, że rozmowa jest nagrywana."
        )
        if (enabled) {
            HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
            SettingsActionRow(
                title = "Usługa dostępności",
                icon = Icons.Default.Accessibility,
                tooltip = "Android wpuszcza mikrofon w trakcie rozmowy tylko do usług dostępności — bez niej nagranie rozmowy jest ciszą. " +
                    "Usługa Nutki nie czyta ekranu; w ustawieniach wybierz „Nutka — nagrywanie rozmów” i włącz.",
                onClick = { runCatching { accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } },
                trailing = {
                    Tag(
                        text = if (serviceEnabled) "Włączona" else "Włącz",
                        style = if (serviceEnabled) TagStyle.ACCENT2 else TagStyle.OUTLINE
                    )
                }
            )
            HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
            SettingsToggleRow(
                title = "Nagrywaj każdą rozmowę",
                checked = autoRecord,
                onCheckedChange = { onToggleAutoRecord() },
                icon = Icons.Default.PhoneInTalk,
                tooltip = "Wyłączone: przy każdej rozmowie pojawia się powiadomienie „Nagraj” i nagrywasz tylko te, na które rozmówca się zgodził. " +
                    "Włączone: każda rozmowa nagrywa się sama, także z osobami, które o tym nie wiedzą."
            )
            HorizontalDivider(color = NutkaColors.divider, thickness = 1.dp)
            Text(
                buildString {
                    append("W trakcie rozmowy włącz głośnik. Android nie daje aplikacjom dźwięku ze słuchawki, ")
                    append("więc głos rozmówcy nagrywa się tylko przez mikrofon — z głośnika.")
                    if (!serviceEnabled) {
                        append("\n\nPrzełącznik Nutki w ustawieniach dostępności jest wyszarzony? ")
                        append("Ustawienia → Aplikacje → Nutka → ⋮ → „Zezwól na ustawienia z ograniczonym dostępem”, potem spróbuj ponownie.")
                    }
                },
                fontSize = 11.5.sp,
                color = NutkaColors.text.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun NotionCard(
    connected: Boolean,
    databaseId: String,
    autoExportOn: Boolean,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var dbInput by remember { mutableStateOf(databaseId) }

    HintTooltip(
        "Połącz Nutkę z bazą danych Notion, aby jedna dotknięcie wysyłało gotową transkrypcję do twojego workspace'u. Potrzebny jest token integracji (secret_…) oraz ID bazy danych."
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(NutkaColors.neutral900),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = NutkaColors.bg, modifier = Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Notion", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            connected && autoExportOn -> "Połączono · autoeksport włączony"
                            connected -> "Połączono · eksport ręczny"
                            else -> "Nie połączono"
                        },
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
                    SettingsTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = "Token integracji (secret_…)",
                        password = true,
                        tooltip = "W Notion: Ustawienia → Połączenia → utwórz integrację i skopiuj jej sekret zaczynający się od secret_. Pamiętaj też, by udostępnić integracji docelową bazę danych."
                    )
                    SettingsTextField(
                        value = dbInput,
                        onValueChange = { dbInput = it },
                        placeholder = "ID bazy danych",
                        tooltip = "Otwórz bazę w Notion i skopiuj fragment adresu URL między ostatnim „/” a znakiem „?” — to 32-znakowy identyfikator bazy."
                    )
                    Button(
                        onClick = { if (tokenInput.isNotBlank() && dbInput.isNotBlank()) { onConnect(tokenInput, dbInput); showForm = false } },
                        enabled = tokenInput.isNotBlank() && dbInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.accent, contentColor = NutkaColors.bg)
                    ) { Text("Zapisz połączenie", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun ElevenLabsCard(apiKey: String, onApiKeyChange: (String) -> Unit) {
    HintTooltip(
        "Puste pole = transkrypcja wyłącznie na urządzeniu, nic nie opuszcza telefonu. Klucz ElevenLabs (elevenlabs.io) odblokowuje transkrypcję importowanych plików oraz prawdziwe rozdzielenie mówców i napisy SRT."
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NutkaColors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(Icons.Default.Key)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Klucz API", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (apiKey.isBlank()) "Tryb lokalny — audio nie opuszcza telefonu"
                        else "Połączono z ElevenLabs",
                        fontSize = 11.5.sp,
                        color = if (apiKey.isBlank()) NutkaColors.accent2_700 else NutkaColors.accent700
                    )
                }
            }
            SettingsTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                placeholder = "Wklej klucz API (sk_…)",
                password = true,
                tooltip = "Znajdziesz go na elevenlabs.io w profilu → API Keys. Klucz przechowujemy tylko lokalnie na telefonie."
            )
        }
    }
}

// ---- Rows --------------------------------------------------------------------

@Composable
private fun SpeakerCountRow(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(0, 1, 2, 3, 4, 5, 6)
    HintTooltip(
        "Podpowiedź dla modelu, ile osób mówi w nagraniu. Pomaga, gdy ktoś mówi wyraźnie ciszej i automatyczne wykrywanie go pomija. „Auto” = model sam zgaduje."
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Liczba mówców", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { count ->
                    val isSelected = count == selected
                    val label = when (count) { 0 -> "Auto"; 6 -> "6+"; else -> count.toString() }
                    val shape = RoundedCornerShape(999.dp)
                    Box(
                        Modifier
                            .clip(shape)
                            .then(if (!isSelected) Modifier.border(1.dp, NutkaColors.divider, shape) else Modifier)
                            .background(if (isSelected) NutkaColors.accent else NutkaColors.bg)
                            .clickable { onSelect(count) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 12.5.sp,
                            color = if (isSelected) NutkaColors.bg else NutkaColors.text.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeytermsRow(keyterms: List<String>, onKeytermsChange: (List<String>) -> Unit) {
    HintTooltip(
        "Nazwy własne, nazwa firmy, żargon i imiona, które model często myli — dodaj je, a transkrypcja będzie ich używać poprawnie. Dodawaj po jednym terminie i zatwierdzaj klawiszem „gotowe”."
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Słowa kluczowe", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            KeytermsField(keyterms = keyterms, onKeytermsChange = onKeytermsChange)
        }
    }
}

@Composable
private fun LanguageRow(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = LANGUAGES.firstOrNull { it.first == selected }?.second ?: LANGUAGES[0].second

    HintTooltip(
        "Język, w którym najczęściej mówisz — poprawia rozpoznawanie słów. „Wykryj automatycznie” pozwala mieszać języki, ale bywa mniej celna przy krótkich wypowiedziach."
    ) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingIcon(Icons.Default.Language)
                Text(
                    "Język podstawowy",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                Text(selectedLabel, fontSize = 12.5.sp, color = NutkaColors.text.copy(alpha = 0.55f))
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = NutkaColors.text.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 4.dp).size(18.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LANGUAGES.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (code == selected) NutkaColors.accent700 else NutkaColors.text) },
                        onClick = { onSelect(code); expanded = false }
                    )
                }
            }
        }
    }
}

// ---- Inputs ------------------------------------------------------------------

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    tooltip: String? = null
) {
    var showPassword by remember { mutableStateOf(false) }

    val field: @Composable () -> Unit = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            singleLine = true,
            visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (password) {
                {
                    Text(
                        if (showPassword) "Ukryj" else "Pokaż",
                        fontSize = 11.5.sp,
                        color = NutkaColors.accent700,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showPassword = !showPassword }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else null,
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

    if (tooltip != null) HintTooltip(tooltip) { field() } else field()
}
