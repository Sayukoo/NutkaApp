package com.nutka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.ui.components.HintTooltip
import com.nutka.app.ui.theme.NutkaColors

@Composable
fun ImportScreen(importing: Boolean, onBack: () -> Unit, onPickFile: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
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
            Text("Importuj plik", style = MaterialTheme.typography.titleLarge)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(NutkaColors.accent2_100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = NutkaColors.accent2_700, modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 230.dp)) {
                    Text("Wybierz nagranie", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        "MP3, M4A, WAV lub inny plik audio z telefonu",
                        fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (importing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NutkaColors.accent)
                        Text("  Importowanie…", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.7f))
                    }
                } else {
                    Button(
                        onClick = onPickFile,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(containerColor = NutkaColors.accent, contentColor = NutkaColors.bg),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp, vertical = 12.dp)
                    ) { Text("Wybierz plik", fontSize = 14.sp) }
                }
            }
        }
    }
}
