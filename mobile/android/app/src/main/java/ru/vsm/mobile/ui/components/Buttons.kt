package ru.vsm.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Варианты кнопки — соответствуют `Button.jsx` на сайте (primary/secondary/ghost). */
enum class VsmButtonVariant { Primary, Secondary, Ghost }

/** Высота кнопки: 30/36/44dp — как `--btn-h-sm/md/lg` на сайте. */
enum class VsmButtonSize { Small, Medium, Large }

/**
 * Капсульная кнопка — базовый интерактивный элемент сайта, перенесённый 1:1 (форма/варианты/размеры).
 */
@Composable
fun VsmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: VsmButtonVariant = VsmButtonVariant.Primary,
    size: VsmButtonSize = VsmButtonSize.Medium,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val heightDp = when (size) {
        VsmButtonSize.Small -> 30.dp
        VsmButtonSize.Medium -> 36.dp
        VsmButtonSize.Large -> 44.dp
    }
    val horizontalPadding = when (size) {
        VsmButtonSize.Small -> 14.dp
        VsmButtonSize.Medium -> 18.dp
        VsmButtonSize.Large -> 24.dp
    }
    val containerColor: Color
    val contentColor: Color
    val border: BorderStroke?
    when (variant) {
        VsmButtonVariant.Primary -> {
            containerColor = MaterialTheme.colorScheme.primary
            contentColor = MaterialTheme.colorScheme.onPrimary
            border = null
        }
        VsmButtonVariant.Secondary -> {
            containerColor = MaterialTheme.colorScheme.surface
            contentColor = MaterialTheme.colorScheme.onSurface
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
        VsmButtonVariant.Ghost -> {
            containerColor = Color.Transparent
            contentColor = MaterialTheme.colorScheme.primary
            border = null
        }
    }
    val alpha = if (enabled) 1f else 0.45f
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .wrapContentWidth()
            .pressScale(interactionSource = interactionSource)
            .height(heightDp)
            .clip(CircleShape)
            .let { if (border != null) it.border(border, CircleShape) else it }
            .background(containerColor.copy(alpha = containerColor.alpha * alpha), CircleShape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) leadingIcon()
        Text(
            text = text,
            color = contentColor.copy(alpha = alpha),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

/**
 * Полноширинная строка варианта ответа в диалоге (Choice) — мин-высота 56dp, текст слева,
 * радиус карточки, тонкая обводка.
 */
@Composable
fun VsmChoiceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(pressedScale = 0.97f, interactionSource = interactionSource)
            .defaultMinSize(minHeight = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.large)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            textAlign = TextAlign.Start,
        )
    }
}
