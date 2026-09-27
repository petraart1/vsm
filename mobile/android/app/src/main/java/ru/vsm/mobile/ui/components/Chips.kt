package ru.vsm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.vsm.mobile.ui.theme.VsmPalette

/** Тон плашки/бейджа — соответствует `Badge.jsx` (neutral/blue/green/red/amber/inverse). */
enum class VsmTone { Neutral, Blue, Green, Red, Amber, Inverse }

private data class ToneColors(val bg: Color, val border: Color, val fg: Color)

@Composable
private fun toneColors(tone: VsmTone): ToneColors {
    val cs = MaterialTheme.colorScheme
    return when (tone) {
        VsmTone.Neutral -> ToneColors(cs.surfaceVariant, cs.outline, cs.onSurfaceVariant)
        VsmTone.Blue -> ToneColors(cs.primaryContainer, cs.primary.copy(alpha = 0.35f), cs.primary)
        VsmTone.Green -> ToneColors(VsmPalette.success.copy(alpha = 0.12f), VsmPalette.success.copy(alpha = 0.4f), VsmPalette.success)
        VsmTone.Red -> ToneColors(VsmPalette.danger.copy(alpha = 0.12f), VsmPalette.danger.copy(alpha = 0.4f), VsmPalette.danger)
        VsmTone.Amber -> ToneColors(VsmPalette.amberSoft, VsmPalette.amber.copy(alpha = 0.35f), VsmPalette.amber)
        VsmTone.Inverse -> ToneColors(cs.onSurface, cs.onSurface, cs.surface)
    }
}

/** Плашка-тег: тонкая рамка + лёгкая заливка в цвет тона (высота 24dp, капсула). */
@Composable
fun VsmBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: VsmTone = VsmTone.Neutral,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = toneColors(tone)
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(CircleShape)
            .background(colors.bg, CircleShape)
            .border(0.75.dp, colors.border, CircleShape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) icon()
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = colors.fg, maxLines = 1)
    }
}

/** Сегментированный переключатель (фильтры «Все/Не пройдены/Пройдены», вкладки «Сюжет/Свободная»). */
@Composable
fun <T> VsmSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
