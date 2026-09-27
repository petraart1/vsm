package ru.vsm.mobile.ui.art

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

/**
 * Печать учебного центра — перенос React-компонента `Seal.jsx`: кольцевая надпись по дуге,
 * знак/подпись в центре, дуга прогресса для состояния "в обучении". Кольцевой текст рисуется
 * через `nativeCanvas.drawTextOnPath` (Android Canvas), т.к. Compose `DrawScope` не поддерживает
 * текст по кривой напрямую.
 */
enum class SealState { CERTIFIED, IN_TRAINING, NOT_STARTED }

@Composable
fun Seal(
    mark: String,
    caption: String? = "модуль",
    ring: String = "ReactLab  •  повышение квалификации  •  ",
    state: SealState = SealState.CERTIFIED,
    progress: Float = 0f,
    size: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    val ink = Color(0xFF0A64D8)
    val inkFg = Color(0xFFFFFFFF)
    val panelBg = Color(0xFFFFFFFF)
    val lineStrong = Color(0xFFC7C7CC)
    val line = Color(0xFFE3E3E8)
    val fg = Color(0xFF1C1C1E)
    val fg3 = Color(0xFF8E8E93)

    val outerFill: Color
    val outerStroke: Color?
    val innerStroke: Color
    val innerAlpha: Float
    val textColor: Color
    val ringAlpha: Float
    when (state) {
        SealState.CERTIFIED -> {
            outerFill = ink; outerStroke = null; innerStroke = inkFg; innerAlpha = 0.5f; textColor = inkFg; ringAlpha = 1f
        }
        SealState.IN_TRAINING -> {
            outerFill = panelBg; outerStroke = lineStrong; innerStroke = lineStrong; innerAlpha = 1f; textColor = fg; ringAlpha = 0.55f
        }
        SealState.NOT_STARTED -> {
            outerFill = Color.Transparent; outerStroke = lineStrong; innerStroke = line; innerAlpha = 1f; textColor = fg3; ringAlpha = 0.6f
        }
    }

    val measurer = rememberTextMeasurer()
    val ringVisible = size >= 80.dp

    Canvas(modifier = modifier.size(size)) {
        val s = kotlin.math.min(this.size.width, this.size.height) / 100f
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            // Внешний круг (заливка/обводка/пунктир по состоянию)
            drawCircle(outerFill, radius = 44f, center = Offset(50f, 50f))
            outerStroke?.let {
                val strokeStyle = if (state == SealState.NOT_STARTED) {
                    Stroke(width = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 3f)))
                } else Stroke(width = 1f)
                drawCircle(it, radius = 44f, center = Offset(50f, 50f), style = strokeStyle)
            }

            // Дуга прогресса (только "в обучении")
            if (state == SealState.IN_TRAINING) {
                rotate(-90f, pivot = Offset(50f, 50f)) {
                    val p = progress.coerceIn(0f, 1f).coerceAtLeast(0.02f)
                    drawArc(
                        color = ink,
                        startAngle = 0f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        topLeft = Offset(6f, 6f),
                        size = androidx.compose.ui.geometry.Size(88f, 88f),
                        style = Stroke(width = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }

            drawCircle(innerStroke, radius = 28f, center = Offset(50f, 50f), alpha = innerAlpha, style = Stroke(width = 0.8f))

            // Кольцевая надпись — только если знак достаточно крупный (как в оригинале, size >= 80).
            if (ringVisible) {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        isAntiAlias = true
                        color = textColor.copy(alpha = ringAlpha).toArgb()
                        textSize = 6.4f
                        letterSpacing = 0.6f / 6.4f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        textAlign = Paint.Align.LEFT
                    }
                    // Старт дуги ~9 часов (как в оригинале "m -36 0" перед дугой), обход по часовой.
                    val path = AndroidPath().apply { addArc(14f, 14f, 86f, 86f, 180f, 359.9f) }
                    canvas.nativeCanvas.drawTextOnPath(ring, path, 0f, 0f, paint)
                }
            }

            val markStyle = TextStyle(fontSize = TextUnit(22f, TextUnitType.Sp), fontWeight = FontWeight.ExtraBold, color = textColor, textAlign = TextAlign.Center)
            val markResult = measurer.measure(mark, markStyle)
            val markY = if (caption != null) 52f else 57f
            drawText(markResult, topLeft = Offset(50f - markResult.size.width / 2f, markY - markResult.size.height * 0.8f))

            if (caption != null) {
                val capStyle = TextStyle(fontSize = TextUnit(6f, TextUnitType.Sp), fontWeight = FontWeight.SemiBold, color = textColor.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                val capResult = measurer.measure(caption, capStyle)
                drawText(capResult, topLeft = Offset(50f - capResult.size.width / 2f, 64f - capResult.size.height * 0.8f))
            }
        }
    }
}
