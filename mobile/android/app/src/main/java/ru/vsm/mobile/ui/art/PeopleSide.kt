package ru.vsm.mobile.ui.art

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Перенос профиля персонажа в движении из `components/characters/People.jsx` (функции `Side`,
 * `Leg`, `Arm`, `SideHair`, `SideHat`) — «суставная» ходьба (бедро → колено → стопа, руки в
 * противофазе, корпус приседает на двойной опоре). Используется только сценой вагона
 * (`CarScene.kt`): проводник и визитёры, идущие по проходу; вид спереди для стоящего/сидящего
 * персонажа — в `People.kt`.
 *
 * [WalkingPerson] — прямой аналог `Person` из People.jsx с проп `walking`/`talking`/`facing`:
 * при `walking=true` рисуется профиль с циклом шага (620 мс, как в CSS-анимации сайта), иначе —
 * вид спереди из [drawFront] (опционально с покачиванием рта — `talking`, как на экране медосмотра).
 */
@Composable
fun WalkingPerson(
    outfit: String = "conductor",
    variant: Int = 0,
    skin: Int = 0,
    hair: Int = 0,
    hairStyle: String? = null,
    walking: Boolean = false,
    talking: Boolean = false,
    facing: String = "right",
    mood: String? = null,
    modifier: Modifier = Modifier
) {
    val o = outfitOf(outfit, variant)
    val skinC = SKIN_TONES[(o.skinTone ?: skin) % SKIN_TONES.size]
    val hairC = if (o.hairStyle == "grey") HAIR_COLORS[4] else HAIR_COLORS[hair % HAIR_COLORS.size]
    val style = hairStyle ?: o.hairStyle

    val walkT = rememberInfiniteTransition(label = "walk")
    val phase by walkT.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(tween(620, easing = LinearEasing), RepeatMode.Restart),
        label = "walkPhase"
    )
    val mouthT = rememberInfiniteTransition(label = "talk")
    val mouthScale by mouthT.animateFloat(
        initialValue = 0.6f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(260, easing = LinearEasing), RepeatMode.Reverse),
        label = "mouthScale"
    )

    val w = 80f
    val h = 180f
    Canvas(modifier = modifier) {
        val s = kotlin.math.min(this.size.width / w, this.size.height / h)
        withTransformScale(s) {
            val mirror = facing == "left"
            if (mirror) {
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(w / 2, h / 2)) {
                    drawWalkingBody(o, skinC, hairC, style, mood, walking, phase, if (talking) mouthScale else 1f)
                }
            } else {
                drawWalkingBody(o, skinC, hairC, style, mood, walking, phase, if (talking) mouthScale else 1f)
            }
        }
    }
}

private fun DrawScope.drawWalkingBody(
    o: Outfit,
    skinC: Color,
    hairC: Color,
    hairStyle: String,
    mood: String?,
    walking: Boolean,
    phase: Float,
    mouthScaleY: Float
) {
    // Тень под ногами — уже у идущего персонажа (rx 17 вместо 20, как в People.jsx).
    drawPath(svgPath("M40,175 m-17,0 a17,3.4 0,1,0 34,0 a17,3.4 0,1,0 -34,0"), Color.Black.copy(alpha = 0.12f))
    if (walking) drawSide(o, skinC, hairC, hairStyle, phase) else drawFront(o, skinC, hairC, hairStyle, mood, mouthScaleY)
}

// ---------------------------------------------------------------------------
// Ходьба в профиль (смотрит вправо, как Side() в People.jsx)
// ---------------------------------------------------------------------------

/** Линейная интерполяция по ключевым кадрам CSS-анимации (`points` — доли 0..1 цикла, `values` — градусы/px). */
private fun keyframe(t: Float, points: FloatArray, values: FloatArray): Float {
    val tt = ((t % 1f) + 1f) % 1f
    for (i in 0 until points.size - 1) {
        if (tt >= points[i] && tt <= points[i + 1]) {
            val span = points[i + 1] - points[i]
            val f = if (span <= 0f) 0f else (tt - points[i]) / span
            return values[i] + (values[i + 1] - values[i]) * f
        }
    }
    return values.last()
}

private val THIGH_PTS = floatArrayOf(0f, .12f, .25f, .38f, .5f, .62f, .75f, .88f, 1f)
private val THIGH_VAL = floatArrayOf(-23f, -16f, -6f, 8f, 16f, 9f, -8f, -21f, -23f)
private val SHIN_VAL = floatArrayOf(4f, 16f, 7f, 4f, 24f, 60f, 44f, 10f, 4f)
private val FOOT_VAL = floatArrayOf(-16f, 0f, 2f, -2f, 24f, 14f, -6f, -12f, -16f)
private val HALF_PTS = floatArrayOf(0f, .5f, 1f)
private val ARM_VAL = floatArrayOf(18f, -22f, 18f)
private val FORE_VAL = floatArrayOf(-6f, -30f, -6f)
private val BOB_VAL = floatArrayOf(0.6f, -2.2f, 0.6f)
private val HEAD_BOB_VAL = floatArrayOf(0.8f, -0.8f, 0.8f)

private fun DrawScope.drawSide(o: Outfit, skinC: Color, hairC: Color, hairStyle: String, phase: Float) {
    val bodyBob = keyframe(phase % 0.5f * 2f, HALF_PTS, BOB_VAL) // 310ms полуцикл переноса опоры
    // .lean: наклон корпуса на 3° вперёд (как в CSS), вокруг стопы (40,172).
    rotate(3f, pivot = Offset(40f, 172f)) {
        translate(top = bodyBob) {
            drawSideArm(o, skinC, back = true, phase = phase + 0.5f)
            drawSideLeg(o, back = true, phase = phase + 0.5f)
            o.backpack?.let { drawRoundRect(it, Offset(20f, 60f), Size(13f, 36f), 5f) }
            if (o.hairStyle == "long") drawPath(svgPath("M26,30 Q22,58 30,74 L40,72 Q36,50 38,30 Z"), hairC)
            drawSideLeg(o, back = false, phase = phase)

            // Корпус
            if (o.coat) {
                drawPath(svgPath("M30,61 Q30,55 37,55 L46,55 Q53,56 53,64 L55,139 Q55,142 52,142 L29,142 Q26,142 27,139 Z"), o.top)
            } else {
                drawPath(svgPath("M31,61 Q31,55 37,55 L46,55 Q52,56 52,63 L51,104 Q51,107 48,107 L34,107 Q31,107 31,104 Z"), o.top)
            }
            drawPath(svgPath("M31,61 Q31,55 37,55 L38,55 L36,106 L34,106 Q31,106 31,103 Z"), o.shade.copy(alpha = 0.55f))
            if (!o.hood && o.shirt != null && o.shirt != o.top) drawPath(svgPath("M45,55 L52,56 L50,64 Z"), o.shirt)
            o.tie?.let { drawPath(svgPath("M49.6,58 L51.4,58 L51,74 L49.6,76 Z"), it) }
            if (o.hood) drawPath(svgPath("M33,55 Q31,50 38,49 Q44,50 44,55 Z"), o.shade)
            o.vest?.let { drawRoundRect(it, Offset(32f, 63f), Size(20f, 38f), 3f) }
            if (o.badge && o.vest == null && !o.coat) drawRoundRect(Color(0xFFEEF2F8), Offset(46f, 66f), Size(5f, 4f), 1f)
            if (o.stethoscope) drawPath(svgPath("M46,56 Q51,68 48,80"), Color(0xFF3B3F48), style = Stroke(width = 1.4f))
            if (o.bag || o.handbag != null) {
                val c = if (o.bag) Color(0xFF2D2E33) else o.handbag!!
                drawLine(c, Offset(46f, 56f), Offset(36f, 97f), strokeWidth = 2.2f)
                drawRoundRect(c, Offset(29f, 94f), Size(15f, 17f), 3f)
            }
            if (o.radio) {
                drawRect(Color(0xFF111111), Offset(44f, 56f), Size(4.5f, 8f))
                drawRect(Color(0xFF111111), Offset(46f, 49f), Size(1.3f, 8f))
            }

            drawSideArm(o, skinC, back = false, phase = phase)

            // Голова
            drawRoundRect(skinC, Offset(38f, 47f), Size(8.5f, 11f), 3f)
            val headBob = keyframe(phase % 0.5f * 2f, HALF_PTS, HEAD_BOB_VAL)
            rotate(headBob, pivot = Offset(43f, 50f)) {
                drawOvalCentered(skinC, Offset(43f, 32f), Size(35f, 38f))
                drawPath(svgPath("M60,35 Q62.8,37.5 60,40"), skinC)
                drawSideHair(hairStyle, hairC)
                drawCircle(skinC, 3.3f, Offset(37f, 36f))
                if (o.glasses) drawCircle(Color(0xFF3B3F48), 4f, Offset(53.2f, 34.5f), style = Stroke(width = 1f))
                drawOvalCentered(INK, Offset(53.2f, 34.5f), Size(3.6f, 5f))
                drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2.4f, Offset(54f, 41f))
                drawPath(svgPath("M55,45 Q57.5,46 59,44.6"), Color(0xFF8B4A3A), style = Stroke(width = 1.1f, cap = StrokeCap.Round))
                if (o.hoodUp) drawPath(svgPath("M24,38 Q20,9 44,8 Q64,9 63,30 Q56,16 44,16 Q30,16 28,40 Q27,50 24,38 Z"), o.top)
                o.beanie?.let { drawPath(svgPath("M25,27 Q24,8 43,8 Q60,8 61,25 L61,27 Z"), it) }
                drawSideHat(o.hat, o.cap, o.capBrim)
            }
        }
    }
}

private fun DrawScope.drawSideLeg(o: Outfit, back: Boolean, phase: Float) {
    val c = if (back) o.pantsShade else o.pants
    val thigh = keyframe(phase, THIGH_PTS, THIGH_VAL)
    rotate(thigh, pivot = Offset(40f, 102f)) {
        drawRoundRect(c, Offset(34.5f, 98f), Size(11f, 40f), 5.5f)
        val shin = keyframe(phase, THIGH_PTS, SHIN_VAL)
        rotate(shin, pivot = Offset(40f, 135f)) {
            drawRoundRect(c, Offset(35f, 132f), Size(10f, 34f), 5f)
            o.trim?.let { if (!o.stripes) drawRect(it, Offset(35f, 157f), Size(10f, 2.4f)) }
            val foot = keyframe(phase, THIGH_PTS, FOOT_VAL)
            rotate(foot, pivot = Offset(40f, 165f)) {
                val shoeC = if (back) darken(o.shoes) else o.shoes
                drawPath(svgPath("M34,164 Q34,171 38,171.5 L54,171.5 Q57.5,171.5 56.5,167.5 Q55.5,164.5 48,163.5 L45,160.5 L36,160.5 Z"), shoeC)
                o.sole?.let { drawRoundRect(it, Offset(34f, 170f), Size(23f, 2.2f), 1.1f) }
            }
        }
    }
}

private fun DrawScope.drawSideArm(o: Outfit, skinC: Color, back: Boolean, phase: Float) {
    val c = if (back) o.shade else o.top
    val arm = keyframe(phase, HALF_PTS, ARM_VAL)
    rotate(arm, pivot = Offset(41f, 61f)) {
        drawRoundRect(c, Offset(36.5f, 57f), Size(9f, 32f), 4.5f)
        val fore = keyframe(phase, HALF_PTS, FORE_VAL)
        rotate(fore, pivot = Offset(41f, 86f)) {
            drawRoundRect(c, Offset(37f, 84f), Size(8f, 23f), 4f)
            o.trim?.let { if (!o.stripes) drawRect(it, Offset(37f, 102f), Size(8f, 2.2f)) }
            drawCircle(skinC, 4.4f, Offset(41f, 109f))
            if (!back && o.briefcase) drawRoundRect(Color(0xFF23252B), Offset(35f, 112f), Size(15f, 13f), 2f)
            if (!back && o.bottle) {
                drawRoundRect(Color(0xFF3F6B3A), Offset(38f, 106f), Size(6f, 15f), 2f)
                drawRoundRect(Color(0xFF3F6B3A), Offset(39.7f, 100f), Size(2.6f, 7f), 1f)
            }
        }
    }
}

private fun DrawScope.drawSideHair(style: String, color: Color) {
    val d = when (style) {
        "messy" -> "M25,36 Q21,10 43,9 Q62,9 62,25 L57,21 L55,27 L51,20 L48,26 L44,20 Q37,26 34,38 Q30,42 26,40 Z"
        "long" -> "M25,36 Q23,11 43,11 Q60,11 61,25 Q55,20 48,21 Q45,26 40,25 Q35,30 34,40 Q30,44 26,42 Z"
        else -> "M25.5,35 Q23,11 43,11 Q60,11 61,25 Q55,20 48,21 Q45,26 40,25 Q36,29 34,36 Q30,41 26.5,40 Z"
    }
    drawPath(svgPath(d), color)
}

private fun DrawScope.drawSideHat(kind: String?, cap: Color?, brim: Color?) {
    if (kind == "rail" || kind == "police") {
        drawPath(svgPath("M24,21 Q25,7 44,6.5 Q61,7 62,20 L62,25 L24,25 Z"), if (kind == "police") Color(0xFF34466E) else Color(0xFF1C3170))
        drawRect(Color(0xFFD6312F), Offset(24.5f, 20.5f), Size(37.5f, 6f))
        drawPath(svgPath("M59,26.5 L70,27.5 Q67.5,31.5 59,30.5 Z"), Color(0xFF15161A))
        if (kind == "rail") {
            drawPath(svgPath("M55,13.5 l3,1 l-3,1.8 l-3,-1.8 z"), Color(0xFFF2F4F8))
        } else {
            drawCircle(Color(0xFFE6C55A), 2.2f, Offset(56f, 15f))
        }
        return
    }
    if (kind == "guard" || cap != null) {
        drawPath(svgPath("M25,27 Q25,9.5 43,9.5 Q60,9.5 61,25 Z"), cap ?: Color(0xFF1D1E22))
        drawPath(svgPath("M58,24 L71,26 Q69,30 58,28.5 Z"), if (kind == "guard") Color(0xFF111214) else (brim ?: Color(0xFF4B573C)))
    }
}

private fun darken(c: Color): Color = Color(
    red = (c.red * 0.8f).coerceIn(0f, 1f),
    green = (c.green * 0.8f).coerceIn(0f, 1f),
    blue = (c.blue * 0.8f).coerceIn(0f, 1f),
    alpha = c.alpha
)
