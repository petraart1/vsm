package ru.vsm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.vsm.mobile.R
import ru.vsm.mobile.ui.theme.VsmPalette

/** Один день недельной полосы серии входов. */
data class StreakDay(val label: String, val done: Boolean, val today: Boolean, val future: Boolean)

/** Серия входов: пламя+число, неделя кружками (done/today/future). */
@Composable
fun StreakRow(streakDays: Int, week: List<StreakDay>, modifier: Modifier = Modifier, compact: Boolean = false) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = null,
                tint = VsmPalette.warning,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "$streakDays ${pluralDays(streakDays)} подряд",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                week.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val dotColor = when {
                            day.done -> MaterialTheme.colorScheme.primary
                            day.today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(dotColor, CircleShape),
                        )
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun pluralDays(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "день"
        m10 in 2..4 && (m100 < 12 || m100 > 14) -> "дня"
        else -> "дней"
    }
}
