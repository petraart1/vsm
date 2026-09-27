package ru.vsm.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Обёртка для пары [ScaleBar] (лояльность + безопасность) — в шапке диалога и в разборе. */
@Composable
fun ScalesPanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        content = content,
    )
}

/** Пара плашек «±N безопасность / ±N лояльность» после выбора в диалоге. */
@Composable
fun DeltaBadges(safetyDelta: Int, loyaltyDelta: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeltaChip(value = safetyDelta, label = "безопасность")
        DeltaChip(value = loyaltyDelta, label = "лояльность")
    }
}

@Composable
private fun DeltaChip(value: Int, label: String) {
    val tone = when {
        value > 0 -> VsmTone.Green
        value < 0 -> VsmTone.Red
        else -> VsmTone.Neutral
    }
    val sign = if (value > 0) "+" else if (value < 0) "−" else "±"
    VsmBadge(text = "$sign${kotlin.math.abs(value)} $label", tone = tone)
}
