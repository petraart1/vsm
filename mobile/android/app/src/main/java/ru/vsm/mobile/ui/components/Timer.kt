package ru.vsm.mobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.vsm.mobile.ui.theme.VsmPalette

/** Статус таймера решения — как на сайте (urgent ≤30%, critical ≤10%, expired — время вышло). */
enum class VsmTimerStatus { Normal, Urgent, Critical, Expired }

private fun statusOf(remaining: Int, total: Int): VsmTimerStatus {
    if (remaining <= 0) return VsmTimerStatus.Expired
    val ratio = remaining.toFloat() / total.coerceAtLeast(1)
    return when {
        ratio <= 0.1f -> VsmTimerStatus.Critical
        ratio <= 0.3f -> VsmTimerStatus.Urgent
        else -> VsmTimerStatus.Normal
    }
}

/**
 * Кольцевой таймер 48×48 (дуга по remaining/total) + число секунд по центру + подпись снизу.
 * Источник истины (пересчёт от `deadlineAt`) — на стороне экрана/ViewModel; сюда приходит готовое
 * `remainingSeconds`, компонент только рисует.
 */
@Composable
fun VsmTimer(remainingSeconds: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val status = statusOf(remainingSeconds, totalSeconds)
    val ringColor = if (status == VsmTimerStatus.Critical || status == VsmTimerStatus.Expired) {
        VsmPalette.danger
    } else {
        MaterialTheme.colorScheme.primary
    }
    val progress = (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                val strokeWidth = 3.dp.toPx()
                drawArc(
                    color = ringColor.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
                )
            }
            Text(
                text = remainingSeconds.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = if (remainingSeconds > 0) "секунд на решение" else "время вышло",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
