package ru.vsm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Заливка монограммы — соответствует `Avatar.jsx` (`tone='solid'|'soft'`). */
enum class VsmAvatarTone { Solid, Soft }

/** Круг-монограмма (инициалы). `verified` — галочка Госуслуг снизу-справа. */
@Composable
fun VsmAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tone: VsmAvatarTone = VsmAvatarTone.Soft,
    verified: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    if (tone == VsmAvatarTone.Solid) cs.secondary else cs.surfaceVariant,
                    CircleShape,
                )
                .let {
                    if (tone == VsmAvatarTone.Soft) it.border(1.dp, cs.outline, CircleShape) else it
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials.ifBlank { "?" },
                color = if (tone == VsmAvatarTone.Solid) cs.onSecondary else cs.onSurfaceVariant,
                fontSize = (size.value * 0.36f).sp,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (verified) {
            Box(
                modifier = Modifier
                    .size(size * 0.4f)
                    .align(Alignment.BottomEnd)
                    .background(cs.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                VerifiedBadge(size = size * 0.34f)
            }
        }
    }
}
