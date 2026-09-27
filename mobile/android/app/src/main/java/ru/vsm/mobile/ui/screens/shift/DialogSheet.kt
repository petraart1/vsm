package ru.vsm.mobile.ui.screens.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.vsm.mobile.ui.art.PassengerBust
import ru.vsm.mobile.ui.art.PersonBust
import ru.vsm.mobile.ui.components.ScaleBar
import ru.vsm.mobile.ui.components.VsmChoiceButton
import ru.vsm.mobile.ui.components.VsmTimer
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Диалог как переписка (реплики собеседника слева, ответы проводника справа синими пузырями,
 * служебная строка по центру — изменения шкал). Перенос `ChatDialog.jsx`, только рисует: логику
 * ведёт [ShiftViewModel] (заступ / стрессовые ситуации рейса — единый локальный движок диалога).
 */
@Composable
fun DialogSheet(
    state: DialogUiState,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.messages.size, state.busy) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp)) {
                    if (state.outfit != null) PersonBust(outfit = state.outfit, size = 44.dp)
                    else PassengerBust(variant = state.variant ?: 0, size = 44.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(state.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScaleBar(label = "Безопасность", value = state.safety, color = VsmPalette.safety, modifier = Modifier.weight(1f), compact = true)
                ScaleBar(label = "Лояльность", value = state.loyalty, color = VsmPalette.loyalty, modifier = Modifier.weight(1f), compact = true)
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.messages.forEach { m -> DialogBubble(m) }
                if (state.busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (state.timerRemaining != null && state.timerTotal != null && !state.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    VsmTimer(remainingSeconds = state.timerRemaining, totalSeconds = state.timerTotal)
                }
            }

            if (!state.busy && state.choices != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.choices.forEach { choice -> VsmChoiceButton(text = choice.text, onClick = { onChoose(choice.id) }) }
                }
            }

            if (state.final && footer != null) footer()
        }
    }
}

@Composable
private fun DialogBubble(m: DialogMessage) {
    when (m.from) {
        DialogFrom.NPC -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Column(
                Modifier
                    .fillMaxWidth(0.86f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(10.dp),
            ) {
                if (m.speakerLabel != null) {
                    Text(m.speakerLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (m.context != null) {
                    Text(m.context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.text != null) Text(m.text, style = MaterialTheme.typography.bodyMedium)
            }
        }

        DialogFrom.ME -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.text.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                    .padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        DialogFrom.NOTE -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val parts = buildList {
                m.safetyDelta?.let { add("безопасность ${formatDelta(it)}") }
                m.loyaltyDelta?.let { add("лояльность ${formatDelta(it)}") }
            }
            val text = m.text ?: parts.joinToString(" · ").ifBlank { null }
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (m.bad) VsmPalette.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun formatDelta(v: Int) = if (v >= 0) "+$v" else "$v"
