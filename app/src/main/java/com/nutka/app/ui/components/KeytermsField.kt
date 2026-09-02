package com.nutka.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutka.app.ui.theme.NutkaColors

/**
 * Chip-style input for the "Keyterms" field from the Transcribe files
 * upload dialog — free-text terms that should bias transcription
 * (product names, jargon), added one at a time and shown as removable
 * chips.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KeytermsField(
    keyterms: List<String>,
    onKeytermsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier) {
        if (keyterms.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                keyterms.forEach { term ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(NutkaColors.accent100)
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(term, fontSize = 12.5.sp, color = NutkaColors.accent800)
                        IconButton(
                            onClick = { onKeytermsChange(keyterms - term) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Usuń", tint = NutkaColors.accent800, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Dodaj termin…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val trimmed = draft.trim()
                // Compare case-insensitively — "Nutka" i "nutka" to ten sam termin
                // dla silnika transkrypcji, więc nie dopuszczajmy duplikatów.
                if (trimmed.isNotEmpty() && keyterms.none { it.equals(trimmed, ignoreCase = true) }) {
                    onKeytermsChange(keyterms + trimmed)
                }
                draft = ""
            }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = NutkaColors.surface,
                focusedContainerColor = NutkaColors.surface,
                unfocusedBorderColor = NutkaColors.divider,
                focusedBorderColor = NutkaColors.accent
            )
        )
    }
}
