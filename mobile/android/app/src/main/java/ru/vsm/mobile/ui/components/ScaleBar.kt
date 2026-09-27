package ru.vsm.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Одна из двух игровых шкал (лояльность пассажира / рейтинг безопасности), 0..100. */
@Composable
fun ScaleBar(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val clamped = value.coerceIn(0, 100)
    val progress by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(durationMillis = 220),
        label = "scaleBarProgress",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$label · $clamped",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}
