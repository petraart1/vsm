package ru.vsm.mobile.ui.art

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Объёмная медаль наград — перенос React-компонента `Medal.jsx`: обод с фаской, эмалевое/
 * металлическое/стеклянное лицо, тиснёный глиф, бегущий блик, мягкая тень на подложке.
 * Жест переворота сайта (перетаскивание медали пальцем) заменён на нажатие: медаль крутится
 * по оси Y и показывает гравировку [backTitle]/[backNote] на обороте.
 *
 * shape: circle|hexagon|octagon|shield, finish: metal|enamel|glass. Заблокированная медаль
 * (earned=false) всегда рисуется в серой палитре "locked" независимо от finish.
 */
enum class MedalShape { CIRCLE, HEXAGON, OCTAGON, SHIELD }
enum class MedalFinish { METAL, ENAMEL, GLASS }

private data class FinishColors(
    val rimA: Color, val rimB: Color,
    val bevelA: Color, val bevelB: Color,
    val faceA: Color, val faceB: Color,
    val ink: Color, val light: Color,
)

private val FINISHES = mapOf(
    MedalFinish.METAL to FinishColors(
        Color(0xFFFBFCFE), Color(0xFF6F7A8C),
        Color(0xFF7D889B), Color(0xFFE9EDF3),
        Color(0xFFE7EBF1), Color(0xFFAEB7C6),
        Color(0xFF57627A), Color(0xFFFFFFFF),
    ),
    MedalFinish.ENAMEL to FinishColors(
        Color(0xFFFDFEFE), Color(0xFF707B8E),
        Color(0xFF6F7A8C), Color(0xFFEEF1F6),
        Color(0xFF2A6BD6), Color(0xFF0B3D91),
        Color.White, Color(0xFF9CC3FF),
    ),
    MedalFinish.GLASS to FinishColors(
        Color(0xFFE9F3FF), Color(0xFF5A86C4),
        Color(0xFF5A86C4), Color(0xFFE6F1FF),
        Color(0xFFD6E8FF), Color(0xFF78A8EC),
        Color(0xFF0B3D91), Color.White,
    ),
)
private val LOCKED = FinishColors(
    Color(0xFFD9DCE2), Color(0xFF8A909B),
    Color(0xFF8A909B), Color(0xFFD5D9DF),
    Color(0xFFC7CBD2), Color(0xFFAAB0BA),
    Color(0xFF8A909B), Color(0xFFECEEF1),
)

/** Геометрия формы медали для радиуса r (в координатах viewBox 200×200, как в SHAPES из Medal.jsx). */
private fun shapePoints(shape: MedalShape, r: Float): List<Offset> = when (shape) {
    MedalShape.CIRCLE -> emptyList() // рисуется как круг отдельно
    MedalShape.HEXAGON -> polygon(6, r, -90f)
    MedalShape.OCTAGON -> polygon(8, r, -67.5f)
    MedalShape.SHIELD -> emptyList() // рисуется отдельной кривой
}

private fun polygon(n: Int, r: Float, startDeg: Float): List<Offset> = (0 until n).map { i ->
    val a = (startDeg + (360f / n) * i) * (PI / 180f).toFloat()
    Offset(100f + r * cos(a), 100f + r * sin(a))
}

/** Форма «щит» — перенос функции SHAPES.shield(k) из Medal.jsx (кубические кривые вокруг центра 100,100). */
private fun shieldPath(k: Float): Path {
    fun p(x: Float, y: Float) = Offset(100f + (x - 100f) * k, 100f + (y - 100f) * k)
    val a = p(100f, 8f); val b = p(130f, 22f); val c = p(160f, 24f); val d = p(182f, 22f)
    val e = p(184f, 100f); val g = p(170f, 158f); val h = p(100f, 194f)
    val i = p(30f, 158f); val j = p(16f, 100f); val kk = p(18f, 22f); val l = p(40f, 24f); val m = p(70f, 22f)
    return Path().apply {
        moveTo(a.x, a.y)
        cubicTo(b.x, b.y, c.x, c.y, d.x, d.y)
        cubicTo(e.x, e.y, g.x, g.y, h.x, h.y)
        cubicTo(i.x, i.y, j.x, j.y, kk.x, kk.y)
        cubicTo(l.x, l.y, m.x, m.y, a.x, a.y)
        close()
    }
}

private fun shapeOutline(shape: MedalShape, r: Float): Path = when (shape) {
    MedalShape.CIRCLE -> Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(100f - r, 100f - r), Size(r * 2f, r * 2f))) }
    MedalShape.SHIELD -> shieldPath(r / 92f)
    else -> {
        val pts = shapePoints(shape, r)
        Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (idx in 1 until pts.size) lineTo(pts[idx].x, pts[idx].y)
            close()
        }
    }
}

@Composable
fun Medal(
    shape: MedalShape = MedalShape.CIRCLE,
    finish: MedalFinish = MedalFinish.METAL,
    glyph: String? = null,
    text: String? = null,
    earned: Boolean = true,
    size: Dp = 96.dp,
    flippable: Boolean = false,
    backTitle: String = "",
    backNote: String = "",
    modifier: Modifier = Modifier,
) {
    var flipped by remember(backTitle, backNote) { mutableStateOf(false) }
    val angle by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "medal-flip",
    )
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (flippable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = size / 2),
                    ) { flipped = !flipped }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Мягкая тень-подложка под медалью.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = size * 0.42f)
                .graphicsLayer { alpha = if (earned) 0.9f else 0.4f },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawOval(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF0F1E3C).copy(alpha = 0.28f), Color(0xFF0F1E3C).copy(alpha = 0f)),
                    ),
                    topLeft = Offset(this.size.width * 0.18f, this.size.height * 0.86f),
                    size = Size(this.size.width * 0.64f, this.size.height * 0.12f),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = angle
                    cameraDistance = 14f * density.density
                },
        ) {
            if (angle <= 90f) {
                MedalFace(shape = shape, finish = finish, glyph = glyph, text = text, earned = earned, back = false, backTitle = backTitle, backNote = backNote)
            } else {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                    MedalFace(shape = shape, finish = finish, glyph = glyph, text = text, earned = earned, back = true, backTitle = backTitle, backNote = backNote)
                }
            }
        }
    }
}

@Composable
private fun MedalFace(
    shape: MedalShape,
    finish: MedalFinish,
    glyph: String?,
    text: String?,
    earned: Boolean,
    back: Boolean,
    backTitle: String,
    backNote: String,
) {
    val f = if (earned) FINISHES.getValue(finish) else LOCKED
    val measurer = rememberTextMeasurer()
    val clip = shapeOutline(shape, 92f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val s = min(this.size.width, this.size.height) / 200f
        scaleDraw(s) {
            drawShapeLayer(shape, 92f, Brush.linearGradient(listOf(f.rimA, f.rimB), start = Offset.Zero, end = Offset(200f, 200f)))
            drawShapeLayer(shape, 92f * 0.88f, Brush.linearGradient(listOf(f.bevelA, f.bevelB), start = Offset(0f, 0f), end = Offset(0f, 200f)))
            drawShapeLayer(shape, 92f * 0.84f, Brush.radialGradient(listOf(f.faceA, f.faceB), center = Offset(76f, 60f), radius = 156f))
            drawShapeLayer(shape, 92f * 0.74f, null, outline = f.light.copy(alpha = 0.35f))

            if (!back) {
                if (glyph != null) {
                    val paths = MEDAL_GLYPHS[glyph] ?: MEDAL_GLYPHS["medal"]
                    paths?.forEach { d ->
                        translate(58f, 62f) { scaleDraw(3.5f) { drawPath(svgPath(d), Color.Black.copy(alpha = 0.22f), style = Stroke(1.5f)) } }
                    }
                    paths?.forEach { d ->
                        translate(58f, 58f) { scaleDraw(3.5f) { drawPath(svgPath(d), f.ink, style = Stroke(1.5f)) } }
                    }
                } else if (text != null) {
                    val fontSize = if (text.length > 2) 46f else 64f
                    val ty = if (text.length > 2) 113f else 119f
                    val shadow = measurer.measure(text, TextStyle(fontSize = TextUnit(fontSize, TextUnitType.Sp), color = Color.Black.copy(alpha = 0.25f)))
                    drawText(shadow, topLeft = Offset(100f - shadow.size.width / 2f, ty - shadow.size.height * 0.75f - 1.5f))
                    val result = measurer.measure(text, TextStyle(fontSize = TextUnit(fontSize, TextUnitType.Sp), color = f.ink))
                    drawText(result, topLeft = Offset(100f - result.size.width / 2f, ty - result.size.height * 0.75f))
                }

                // Бегущий блик — диагональная светлая полоса поверх лица медали.
                clipPath(clip) {
                    rotate(20f, pivot = Offset(100f, 100f)) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0f)),
                                start = Offset(-60f, 0f),
                                end = Offset(10f, 0f),
                            ),
                            topLeft = Offset(-60f, -20f),
                            size = Size(70f, 260f),
                        )
                    }
                }
            } else {
                val titleStyle = TextStyle(fontSize = TextUnit(15f, TextUnitType.Sp), fontWeight = FontWeight.Bold, color = f.ink, textAlign = TextAlign.Center)
                val titleResult = measurer.measure(backTitle, titleStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = 168))
                drawText(titleResult, topLeft = Offset(100f - titleResult.size.width / 2f, 92f - titleResult.size.height * 0.8f))

                val noteStyle = TextStyle(fontSize = TextUnit(12f, TextUnitType.Sp), color = f.ink.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                val noteResult = measurer.measure(backNote, noteStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = 168))
                drawText(noteResult, topLeft = Offset(100f - noteResult.size.width / 2f, 116f - noteResult.size.height * 0.8f))

                val footStyle = TextStyle(fontSize = TextUnit(10f, TextUnitType.Sp), color = f.ink.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                val footResult = measurer.measure("Тренажёр ВСМ", footStyle)
                drawText(footResult, topLeft = Offset(100f - footResult.size.width / 2f, 146f - footResult.size.height * 0.8f))
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.scaleDraw(s: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    scale(scaleX = s, scaleY = s, pivot = Offset.Zero) { block() }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShapeLayer(shape: MedalShape, r: Float, brush: Brush?, outline: Color? = null) {
    when (shape) {
        MedalShape.CIRCLE -> {
            if (brush != null) drawCircle(brush, radius = r, center = Offset(100f, 100f))
            outline?.let { drawCircle(it, radius = r, center = Offset(100f, 100f), style = Stroke(1.2f)) }
        }
        MedalShape.SHIELD -> {
            val path = shieldPath(r / 92f)
            if (brush != null) drawPath(path, brush)
            outline?.let { drawPath(path, it, style = Stroke(1.2f)) }
        }
        else -> {
            val pts = shapePoints(shape, r)
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            if (brush != null) drawPath(path, brush)
            outline?.let { drawPath(path, it, style = Stroke(1.2f)) }
        }
    }
}

/** Геометрия для тиснения — те же path'ы, что в Icon.jsx ICON_PATHS (используется как гравировка). */
private val MEDAL_GLYPHS: Map<String, List<String>> = mapOf(
    "medal" to listOf("M12,14.5 m-6,0 a6,6 0,1,0 12,0 a6,6 0,1,0 -12,0", "M8.5,9.6 6,2.5h4l2,5", "M15.5,9.6 18,2.5h-4l-2,5"),
    "shield" to listOf("M12,22s8,-4 8,-10V5l-8,-3 -8,3v7c0,6 8,10 8,10z"),
    "check" to listOf("M20,6 9,17 4,12"),
    "flag" to listOf("M4,22V4", "M4,15s1,-1 4,-1 5,2 8,2 4,-1 4,-1V3s-1,1 -4,1 -5,-2 -8,-2 -4,1 -4,1"),
    "sparkle" to listOf("M12,3v3M12,18v3M3,12h3M18,12h3M5.6,5.6 7.7,7.7M16.3,16.3 18.4,18.4M5.6,18.4 7.7,16.3M16.3,7.7 18.4,5.6"),
    "star" to listOf("M12,3v3M12,18v3M3,12h3M18,12h3M5.6,5.6 7.7,7.7M16.3,16.3 18.4,18.4M5.6,18.4 7.7,16.3M16.3,7.7 18.4,5.6"),
    "bolt" to listOf("M13,2 4,14h7l-1,8 9,-12h-7z"),
    "train" to listOf(
        "M9,3 H15 A4,4 0 0 1 19,7 V13 A4,4 0 0 1 15,17 H9 A4,4 0 0 1 5,13 V7 A4,4 0 0 1 9,3 Z",
        "M5,11h14",
        "M9,20 7,22",
        "M15,20 17,22",
        "M8,17 7,20h10l-1,-3",
        "M9,14 m-0.6,0 a0.6,0.6 0,1,0 1.2,0 a0.6,0.6 0,1,0 -1.2,0",
        "M15,14 m-0.6,0 a0.6,0.6 0,1,0 1.2,0 a0.6,0.6 0,1,0 -1.2,0",
    ),
    "clipboard" to listOf("M8,2h8v4a1,1 0,0 1,-1,1H9a1,1 0,0 1,-1,-1z", "M16,4h2a2,2 0,0 1,2,2V20a2,2 0,0 1,-2,2H6a2,2 0,0 1,-2,-2V6a2,2 0,0 1,2,-2h2", "M9,14 11,16 15,12"),
)
