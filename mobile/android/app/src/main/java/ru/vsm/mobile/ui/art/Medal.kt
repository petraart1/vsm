package ru.vsm.mobile.ui.art

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Объёмная медаль наград — статичный перенос лицевой стороны React-компонента `Medal.jsx`
 * (обод/фаска/лицо/блик по тем же градиентам и форме; жест переворота/инерция драга остаётся
 * веб-специфичным взаимодействием и здесь не переносится — на Android медаль статична).
 *
 * shape: circle|hexagon|octagon|shield, finish: metal|enamel|glass (заблокированная медаль —
 * earned=false, независимо от finish, рисуется в отдельной серой палитре "locked").
 */
enum class MedalShape { CIRCLE, HEXAGON, OCTAGON, SHIELD }
enum class MedalFinish { METAL, ENAMEL, GLASS }

private data class FinishColors(val rimA: Color, val rimB: Color, val bevelA: Color, val bevelB: Color, val faceA: Color, val faceB: Color, val ink: Color)

private val FINISHES = mapOf(
    MedalFinish.METAL to FinishColors(Color(0xFFFBFCFE), Color(0xFF6F7A8C), Color(0xFF7D889B), Color(0xFFE9EDF3), Color(0xFFE7EBF1), Color(0xFFAEB7C6), Color(0xFF57627A)),
    MedalFinish.ENAMEL to FinishColors(Color(0xFFFDFEFE), Color(0xFF707B8E), Color(0xFF6F7A8C), Color(0xFFEEF1F6), Color(0xFF2A6BD6), Color(0xFF0B3D91), Color.White),
    MedalFinish.GLASS to FinishColors(Color(0xFFE9F3FF), Color(0xFF5A86C4), Color(0xFF5A86C4), Color(0xFFE6F1FF), Color(0xFFD6E8FF), Color(0xFF78A8EC), Color(0xFF0B3D91))
)
private val LOCKED = FinishColors(Color(0xFFD9DCE2), Color(0xFF8A909B), Color(0xFF8A909B), Color(0xFFD5D9DF), Color(0xFFC7CBD2), Color(0xFFAAB0BA), Color(0xFF8A909B))

/** Геометрия формы медали для радиуса r (0..1 от полного радиуса ~92 из viewBox 200×200). */
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

@Composable
fun Medal(
    shape: MedalShape = MedalShape.CIRCLE,
    finish: MedalFinish = MedalFinish.METAL,
    glyph: String? = null,
    text: String? = null,
    earned: Boolean = true,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier
) {
    val f = if (earned) FINISHES.getValue(finish) else LOCKED
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.size(size)) {
        val s = min(this.size.width, this.size.height) / 200f
        scale(s, Offset.Zero) {
            drawShapeLayer(shape, 92f, Brush.linearGradient(listOf(f.rimA, f.rimB)))
            drawShapeLayer(shape, 92f * 0.88f, Brush.linearGradient(listOf(f.bevelA, f.bevelB)))
            drawShapeLayer(shape, 92f * 0.84f, Brush.radialGradient(listOf(f.faceA, f.faceB), center = Offset(76f, 60f), radius = 78f))
            drawShapeLayer(shape, 92f * 0.74f, null, outline = Color.White.copy(alpha = 0.35f))

            if (glyph != null) {
                val paths = MEDAL_GLYPHS[glyph]
                paths?.forEach { d ->
                    translate(58f, 62f) { scale(3.5f, Offset.Zero) { drawPath(svgPath(d), Color.Black.copy(alpha = 0.22f), style = Stroke(1.5f)) } }
                }
                paths?.forEach { d ->
                    translate(58f, 58f) { scale(3.5f, Offset.Zero) { drawPath(svgPath(d), f.ink, style = Stroke(1.5f)) } }
                }
            } else if (text != null) {
                val fontSize = if (text.length > 2) 46f else 64f
                val ty = if (text.length > 2) 113f else 119f
                val result = measurer.measure(text, TextStyle(fontSize = TextUnit(fontSize, TextUnitType.Sp), color = f.ink))
                drawText(result, topLeft = Offset(100f - result.size.width / 2f, ty - result.size.height * 0.75f))
            }
        }
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.scale(s: Float, pivot: Offset, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    scale(scaleX = s, scaleY = s, pivot = pivot) { block() }
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
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            if (brush != null) drawPath(path, brush)
            outline?.let { drawPath(path, it, style = Stroke(1.2f)) }
        }
    }
}

/** Форма «щит» — перенос функции SHAPES.shield(k) из Medal.jsx (кубические кривые вокруг центра 100,100). */
private fun shieldPath(k: Float): androidx.compose.ui.graphics.Path {
    fun p(x: Float, y: Float) = Offset(100f + (x - 100f) * k, 100f + (y - 100f) * k)
    val a = p(100f, 8f); val b = p(130f, 22f); val c = p(160f, 24f); val d = p(182f, 22f)
    val e = p(184f, 100f); val g = p(170f, 158f); val h = p(100f, 194f)
    val i = p(30f, 158f); val j = p(16f, 100f); val kk = p(18f, 22f); val l = p(40f, 24f); val m = p(70f, 22f)
    return androidx.compose.ui.graphics.Path().apply {
        moveTo(a.x, a.y)
        cubicTo(b.x, b.y, c.x, c.y, d.x, d.y)
        cubicTo(e.x, e.y, g.x, g.y, h.x, h.y)
        cubicTo(i.x, i.y, j.x, j.y, kk.x, kk.y)
        cubicTo(l.x, l.y, m.x, m.y, a.x, a.y)
        close()
    }
}

/** Геометрия для тиснения — те же path'ы, что в Icon.jsx ICON_PATHS (используется как гравировка). */
private val MEDAL_GLYPHS: Map<String, List<String>> = mapOf(
    "medal" to listOf("M12,14.5 m-6,0 a6,6 0,1,0 12,0 a6,6 0,1,0 -12,0", "M8.5,9.6 6,2.5h4l2,5", "M15.5,9.6 18,2.5h-4l-2,5"),
    "shield" to listOf("M12,22s8,-4 8,-10V5l-8,-3 -8,3v7c0,6 8,10 8,10z"),
    "check" to listOf("M20,6 9,17 4,12"),
    "flag" to listOf("M4,22V4", "M4,15s1,-1 4,-1 5,2 8,2 4,-1 4,-1V3s-1,1 -4,1 -5,-2 -8,-2 -4,1 -4,1"),
    "sparkle" to listOf("M12,3v3M12,18v3M3,12h3M18,12h3M5.6,5.6 7.7,7.7M16.3,16.3 18.4,18.4M5.6,18.4 7.7,16.3M16.3,7.7 18.4,5.6"),
    "bolt" to listOf("M13,2 4,14h7l-1,8 9,-12h-7z")
)
