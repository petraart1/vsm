package ru.vsm.mobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/**
 * Тайминги и кривые движения — портированы 1:1 из `frontend/src/styles/motion.css` /
 * `styles/theme.css` (`--dur-*`, `--ease-*`), чтобы переходы на Android ощущались как на сайте.
 */
object VsmMotion {
    const val DUR_FAST = 160
    const val DUR = 260
    const val DUR_SLOW = 520

    val easeOut: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val easeInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val easeSpring: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val easeBounce: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** Каскадная задержка появления карточек в списке — как `--i` в `.rv` (90ms шаг). */
    fun staggerDelay(index: Int, stepMs: Int = 45, baseMs: Int = 0, maxSteps: Int = 10): Int =
        baseMs + stepMs * index.coerceIn(0, maxSteps)
}

/**
 * Появление блока: fade + подъём на 14dp, с кадрированной задержкой по индексу — аналог `.rv` с
 * `--i` на сайте (карточки списков, стат-плитки, кольца). Проигрывается один раз при первой
 * композиции, использует один и тот же durationMillis/easing, что и веб (`--ease-out`, 900ms →
 * упрощено до `DUR_SLOW` для отзывчивости на мобильном).
 */
fun Modifier.appearIn(index: Int = 0, stepMs: Int = 45): Modifier = androidx.compose.ui.composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(VsmMotion.staggerDelay(index, stepMs).toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = VsmMotion.DUR_SLOW, easing = VsmMotion.easeOut),
        )
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28f
    }
}

/**
 * Лёгкое уменьшение при нажатии (0.94×) — как `.tab:active { transform: scale(0.94) }` /
 * `:active` на кнопках сайта. Вешается поверх `clickable`/`Button` модификатора.
 */
fun Modifier.pressScale(pressedScale: Float = 0.94f, interactionSource: MutableInteractionSource): Modifier =
    androidx.compose.ui.composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = tween(durationMillis = VsmMotion.DUR_FAST, easing = VsmMotion.easeOut),
            label = "pressScale",
        )
        this.graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin.Center
        }
    }

/** Готовый источник взаимодействия + модификатор нажатия — для мест, где не нужен свой remember. */
@Composable
fun rememberPressScale(pressedScale: Float = 0.94f): Pair<MutableInteractionSource, Modifier> {
    val interactionSource = remember { MutableInteractionSource() }
    return interactionSource to Modifier.pressScale(pressedScale, interactionSource)
}

/**
 * Анимированное целое число (очки, дни стрика, проценты) — плавный отсчёт между значениями,
 * как счётчики на сайте. Текст без собственного стиля по умолчанию наследует `LocalTextStyle`.
 */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    prefix: String = "",
    suffix: String = "",
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = VsmMotion.DUR_SLOW, easing = VsmMotion.easeOut),
        label = "animatedCounter",
    )
    Text(text = "$prefix${animated}$suffix", style = style, color = color, modifier = modifier)
}

/** Тот же счётчик, но с дробной величиной (например, рейтинг 4.8) — один знак после запятой. */
@Composable
fun AnimatedDecimalCounter(
    value: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    decimals: Int = 1,
    suffix: String = "",
) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = VsmMotion.DUR_SLOW, easing = VsmMotion.easeOut),
        label = "animatedDecimalCounter",
    )
    val factor = Math.pow(10.0, decimals.toDouble()).toFloat()
    val rounded = (animated * factor).roundToInt() / factor
    Text(text = "${"%.${decimals}f".format(rounded)}$suffix", style = style, color = color, modifier = modifier)
}

/** Плавная пружинная анимация значения 0f..1f (прогресс, шкалы) — мягкий перелёт, как `--ease-bounce`. */
@Composable
fun rememberSpringProgress(target: Float): Float {
    val animated by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "springProgress",
    )
    return animated
}
