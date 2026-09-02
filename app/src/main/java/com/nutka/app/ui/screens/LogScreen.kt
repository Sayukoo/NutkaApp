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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.ui.components.HintTooltip
import com.nutka.app.ui.theme.NutkaColors

@Composable
fun LogScreen(lines: List<String>, onBack: () -> Unit, onShare: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HintTooltip("Wróć do ustawień") {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(NutkaColors.surface).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz", modifier = Modifier.size(20.dp)) }
            }
            Text("Dziennik zdarzeń", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HintTooltip("Wyślij cały dziennik przez dowolną aplikację — przydatne przy zgłaszaniu problemu") {
                Icon(
                    Icons.Default.Share, contentDescription = "Udostępnij",
                    tint = NutkaColors.text.copy(alpha = 0.6f),
                    modifier = Modifier.size(34.dp, 34.dp).clickable(onClick = onShare).padding(7.dp)
                )
            }
            HintTooltip("Usuń wszystkie wpisy z dziennika") {
                Icon(
                    Icons.Default.Delete, contentDescription = "Wyczyść",
                    tint = NutkaColors.text.copy(alpha = 0.6f),
                    modifier = Modifier.size(34.dp, 34.dp).clickable(onClick = onClear).padding(7.dp)
                )
            }
        }

        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Brak zdarzeń", fontSize = 13.sp, color = NutkaColors.text.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(NutkaColors.surface)
                    .padding(12.dp)
            ) {
                items(lines.asReversed()) { line ->
                    Text(
                        line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = NutkaColors.text.copy(alpha = 0.85f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
