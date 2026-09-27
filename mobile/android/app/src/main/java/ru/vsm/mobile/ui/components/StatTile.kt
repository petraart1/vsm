package ru.vsm.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

/** Одна стат-плитка (число+подпись+иконка) — профиль, «Сегодня». */
@Composable
fun StatTile(
    icon: Painter,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: VsmTone = VsmTone.Neutral,
) {
    SectionCard(modifier = modifier) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (tone == VsmTone.Neutral) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Строка стат-плиток, равномерно по ширине (5 карточек профиля/сводка «Сегодня»). */
@Composable
fun StatTileRow(tiles: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { tile ->
            Column(modifier = Modifier.weight(1f)) { tile() }
        }
    }
}
