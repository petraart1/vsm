package ru.vsm.mobile.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

/**
 * Персонажи тренажёра — перенос React-компонента `components/characters/People.jsx`: плоские
 * иллюстрации без внешних ассетов (крупная голова, простое лицо), геометрия — те же SVG path'ы,
 * разобранные [svgPath]. Портирован вид спереди (стоя и сидя) — он используется в портретах
 * (PersonBust/PassengerBust) и на статичных экранах; версия в движении (профиль, ходьба) —
 * часть интерактивной сцены вагона (веб CarScene.jsx), которая в контракт экранов Android
 * (design/screens/*.md) не входит и здесь не портируется.
 *
 * Person — стоящий человек: проводник, начальник поезда, медработник, охранник, сотрудник
 * транспортной полиции или пассажир. SeatedPerson — пассажир в кресле.
 */

private val INK = Color(0xFF1D1F24)

/** Оттенки кожи (SKIN) и волос (HAIR) — те же значения, что в People.jsx. */
val SKIN_TONES = listOf(
    Color(0xFFF7D7C2), Color(0xFFEEC4A6), Color(0xFFDCAA88), Color(0xFFB27A58), Color(0xFFF9E2D2)
)
val HAIR_COLORS = listOf(
    Color(0xFF3A2A22), Color(0xFF5A3B2A), Color(0xFF241D1A), Color(0xFF8A6A4A), Color(0xFFB9B9BE), Color(0xFF6B4A33)
)

/** Внешность персонажа: одежда, аксессуары, причёска. Поля — прямой перенос полей JS-объектов ROLES/LOOKS. */
data class Outfit(
    val top: Color,
    val shade: Color,
    val shirt: Color? = null,
    val tie: Color? = null,
    val trim: Color? = null,
    val pants: Color,
    val pantsShade: Color,
    val shoes: Color,
    val sole: Color? = null,
    val hat: String? = null, // "rail" | "guard" | "police"
    val bag: Boolean = false,
    val badge: Boolean = false,
    val stripes: Boolean = false,
    val coat: Boolean = false,
    val stethoscope: Boolean = false,
    val vest: Color? = null,
    val radio: Boolean = false,
    val hood: Boolean = false,
    val hoodUp: Boolean = false,
    val backpack: Color? = null,
    val handbag: Color? = null,
    val briefcase: Boolean = false,
    val bottle: Boolean = false,
    val beanie: Color? = null,
    val cap: Color? = null,
    val capBrim: Color? = null,
    val trackStripes: Boolean = false,
    val headphones: Boolean = false,
    val glasses: Boolean = false,
    val innerTop: Color? = null,
    val hairStyle: String = "short", // "short" | "long" | "side" | "grey" | "messy"
    val skinTone: Int? = null
)

/** Рабочая одежда персонала — ROLES из People.jsx. */
val ROLES: Map<String, Outfit> = mapOf(
    "conductor" to Outfit(
        top = Color(0xFF1C3170), shade = Color(0xFF132457), shirt = Color(0xFFFFFFFF), tie = Color(0xFFD6312F),
        trim = Color(0xFFD6312F), pants = Color(0xFF1C3170), pantsShade = Color(0xFF132457), shoes = Color(0xFF1B1C21),
        hat = "rail", bag = true, badge = true
    ),
    "chief" to Outfit(
        top = Color(0xFF1C3170), shade = Color(0xFF132457), shirt = Color(0xFFFFFFFF), tie = Color(0xFF0B3D91),
        trim = Color(0xFFE9EDF4), pants = Color(0xFF1C3170), pantsShade = Color(0xFF132457), shoes = Color(0xFF1B1C21),
        hat = "rail", stripes = true, badge = true
    ),
    "medic" to Outfit(
        top = Color(0xFFF7F8FB), shade = Color(0xFFDDE2EA), shirt = Color(0xFF2A4282), pants = Color(0xFF2A4282),
        pantsShade = Color(0xFF1F3368), shoes = Color(0xFFF4F5F8), sole = Color(0xFFC8CED8), coat = true,
        stethoscope = true, badge = true
    ),
    "guard" to Outfit(
        top = Color(0xFF26272C), shade = Color(0xFF18191C), shirt = Color(0xFF26272C), pants = Color(0xFF26272C),
        pantsShade = Color(0xFF18191C), shoes = Color(0xFF121214), hat = "guard", vest = Color(0xFF303137), radio = true
    ),
    "police" to Outfit(
        top = Color(0xFF34466E), shade = Color(0xFF26365A), shirt = Color(0xFFCFE0F4), tie = Color(0xFF26365A),
        pants = Color(0xFF26365A), pantsShade = Color(0xFF1C2944), shoes = Color(0xFF141416), hat = "police", badge = true
    )
)

/** Пассажиры: «архетипы» — LOOKS из People.jsx. */
val LOOKS: List<Outfit> = listOf(
    Outfit(top = Color(0xFFECE1CF), shade = Color(0xFFD8CAB3), pants = Color(0xFF3F5F95), pantsShade = Color(0xFF324D7B), shoes = Color(0xFFF4F4F6), sole = Color(0xFFCFD3DA), hood = true, backpack = Color(0xFF2A2B30), hairStyle = "short"),
    Outfit(top = Color(0xFF1F2A4F), shade = Color(0xFF161F3C), shirt = Color(0xFFFFFFFF), tie = Color(0xFF1C3170), pants = Color(0xFF1F2A4F), pantsShade = Color(0xFF161F3C), shoes = Color(0xFF1B1C21), briefcase = true, hairStyle = "side"),
    Outfit(top = Color(0xFFC49A72), shade = Color(0xFFA98260), shirt = Color(0xFFF3EDE4), pants = Color(0xFFE3DCCF), pantsShade = Color(0xFFCFC6B6), shoes = Color(0xFFF4F4F6), sole = Color(0xFFCFD3DA), coat = true, handbag = Color(0xFF7A4E30), hairStyle = "long"),
    Outfit(top = Color(0xFF1F2C55), shade = Color(0xFF172142), pants = Color(0xFF3B3E46), pantsShade = Color(0xFF2E3037), shoes = Color(0xFF23252B), hood = true, headphones = true, backpack = Color(0xFF8A8F99), hairStyle = "short"),
    Outfit(top = Color(0xFFECE6D8), shade = Color(0xFFD6CEBA), pants = Color(0xFF4C5448), pantsShade = Color(0xFF3D4439), shoes = Color(0xFF2A2B30), hood = true, cap = Color(0xFF5D6B4A), backpack = Color(0xFF2A2B30), hairStyle = "short"),
    Outfit(top = Color(0xFFEEB3C4), shade = Color(0xFFDC9AAD), pants = Color(0xFF8FB0D6), pantsShade = Color(0xFF7B9CC3), shoes = Color(0xFFF4F4F6), sole = Color(0xFFCFD3DA), handbag = Color(0xFF6B4632), hairStyle = "long"),
    Outfit(top = Color(0xFF8E949E), shade = Color(0xFF777D88), shirt = Color(0xFFE9ECF1), pants = Color(0xFF4A505B), pantsShade = Color(0xFF3C414B), shoes = Color(0xFF2A2B30), glasses = true, hairStyle = "grey"),
    Outfit(top = Color(0xFF5D86C2), shade = Color(0xFF4B72AD), pants = Color(0xFF2E3A52), pantsShade = Color(0xFF252F44), shoes = Color(0xFFF4F4F6), sole = Color(0xFFCFD3DA), hood = true, hairStyle = "short")
)

/**
 * Пассажиры с признаками нарушений (variant 100+) — TROUBLE_LOOKS из People.jsx. 100–101 —
 * нетрезвые, 102–103 — агрессивные, 104–106 — под воздействием веществ.
 */
val TROUBLE_LOOKS: Map<Int, Outfit> = mapOf(
    100 to Outfit(top = Color(0xFF4D5A44), shade = Color(0xFF3D4836), innerTop = Color(0xFFC9CCD2), pants = Color(0xFF2A2D33), pantsShade = Color(0xFF202328), shoes = Color(0xFFE9EAEE), sole = Color(0xFFC8CCD4), hood = true, backpack = Color(0xFF2A2B30), hairStyle = "messy", bottle = true, skinTone = 0),
    101 to Outfit(top = Color(0xFF26272C), shade = Color(0xFF1C1D21), innerTop = Color(0xFFECE6DC), pants = Color(0xFF6F8FBF), pantsShade = Color(0xFF5E7DAB), shoes = Color(0xFFF1F1F3), sole = Color(0xFFC8CCD4), hairStyle = "long", bottle = true, skinTone = 4),
    102 to Outfit(top = Color(0xFF1F2023), shade = Color(0xFF161719), pants = Color(0xFF1F2023), pantsShade = Color(0xFF161719), shoes = Color(0xFF1A1B1E), hood = true, cap = Color(0xFF161719), capBrim = Color(0xFF0E0F10), trackStripes = true, hairStyle = "short", skinTone = 1),
    103 to Outfit(top = Color(0xFF1C1D20), shade = Color(0xFF141517), innerTop = Color(0xFFE8E4DC), pants = Color(0xFF23252A), pantsShade = Color(0xFF1B1C20), shoes = Color(0xFFEEEEEF), sole = Color(0xFFC8CCD4), hairStyle = "side", skinTone = 1),
    104 to Outfit(top = Color(0xFF2A2B31), shade = Color(0xFF1F2025), pants = Color(0xFF4A463D), pantsShade = Color(0xFF3C3931), shoes = Color(0xFFE9EAEE), sole = Color(0xFFC8CCD4), hoodUp = true, backpack = Color(0xFF26272B), hairStyle = "short", skinTone = 4),
    105 to Outfit(top = Color(0xFFD6CCBB), shade = Color(0xFFC2B7A4), pants = Color(0xFF33363C), pantsShade = Color(0xFF282A2F), shoes = Color(0xFFE0DDD6), sole = Color(0xFFC8CCD4), backpack = Color(0xFF1F2023), hairStyle = "messy", skinTone = 4),
    106 to Outfit(top = Color(0xFF2B2C31), shade = Color(0xFF202126), pants = Color(0xFF34363B), pantsShade = Color(0xFF2A2B30), shoes = Color(0xFF1A1B1E), beanie = Color(0xFF1C1D21), hood = true, handbag = Color(0xFF26272B), hairStyle = "short", skinTone = 1)
)

fun lookOf(variant: Int): Outfit = TROUBLE_LOOKS[variant] ?: LOOKS[abs(variant) % LOOKS.size]

fun outfitOf(role: String, variant: Int): Outfit =
    if (role == "passenger") lookOf(variant) else ROLES[role] ?: ROLES.getValue("conductor")

// ---------------------------------------------------------------------------
// Стоящий персонаж (вид спереди)
// ---------------------------------------------------------------------------

/**
 * Стоящий персонаж, вид спереди (перенос `Person`/`Front` из People.jsx; ходьба/профиль — см.
 * KDoc файла). outfit: conductor|chief|medic|guard|police|passenger, variant — внешность
 * пассажира, size — высота в dp (ширина ~ size*80/180).
 */
@Composable
fun Person(
    outfit: String = "conductor",
    variant: Int = 0,
    skin: Int = 0,
    hair: Int = 0,
    hairStyle: String? = null,
    mood: String? = null,
    modifier: Modifier = Modifier
) {
    val o = outfitOf(outfit, variant)
    val skinC = SKIN_TONES[(o.skinTone ?: skin) % SKIN_TONES.size]
    val hairC = if (o.hairStyle == "grey") HAIR_COLORS[4] else HAIR_COLORS[hair % HAIR_COLORS.size]
    val style = hairStyle ?: o.hairStyle
    val w = 80f
    val h = 180f
    Canvas(modifier = modifier) {
        val s = min(this.size.width / w, this.size.height / h)
        withTransformScale(s) {
            drawPath(svgPath("M40,175 m-20,0 a20,3.4 0,1,0 40,0 a20,3.4 0,1,0 -40,0"), Color.Black.copy(alpha = 0.12f))
            drawFront(o, skinC, hairC, style, mood)
        }
    }
}

/** Пассажир в кресле, вид спереди (перенос `SeatedPerson` из People.jsx). */
@Composable
fun SeatedPerson(
    variant: Int = 0,
    kid: Boolean = false,
    mood: String = "calm",
    modifier: Modifier = Modifier
) {
    val o = lookOf(variant)
    val skinC = SKIN_TONES[(o.skinTone ?: (variant * 3 + 1)) % SKIN_TONES.size]
    val hairC = if (o.hairStyle == "grey") HAIR_COLORS[4] else HAIR_COLORS[(variant * 5 + 2) % 4]
    val scale = if (kid) 0.72f else 1f
    val w = 70f
    val h = 100f
    Canvas(modifier = modifier) {
        val s = min(this.size.width / w, this.size.height / h) * scale
        withTransformScale(s) {
            drawSeated(o, skinC, hairC, mood)
        }
    }
}

/** Портрет пассажира для диалога — крупный план (перенос `PassengerBust`). */
@Composable
fun PassengerBust(variant: Int = 0, size: androidx.compose.ui.unit.Dp = 48.dp, mood: String = "calm") {
    SeatedPerson(variant = variant, mood = mood, modifier = Modifier.size(size))
}

/** Портрет стоящего персонажа для диалога (перенос `PersonBust`). */
@Composable
fun PersonBust(outfit: String = "conductor", size: androidx.compose.ui.unit.Dp = 48.dp) {
    Person(outfit = outfit, hair = if (outfit == "medic") 1 else 0, modifier = Modifier.size(size))
}

// ---------------------------------------------------------------------------
// Геометрия (androidx.compose.ui.graphics.drawscope) — координаты как в исходном viewBox.
// ---------------------------------------------------------------------------

private inline fun DrawScope.withTransformScale(
    s: Float,
    block: DrawScope.() -> Unit
) {
    scale(scaleX = s, scaleY = s, pivot = Offset.Zero) { block() }
}

private fun DrawScope.drawFront(o: Outfit, skinC: Color, hairC: Color, hairStyle: String, mood: String?) {
    val long = hairStyle == "long"
    if (long) drawPath(svgPath("M21,34 Q19,62 24,80 L56,80 Q61,62 59,34 Z"), hairC)
    o.backpack?.let { drawPath(svgPath("M21,60 h38 v40 a8,8 0 0 1 -8,8 h-22 a8,8 0 0 1 -8,-8 Z"), it) }

    // Ноги
    drawPath(svgPath("M27,100 h12.5 a5,5 0 0 1 5,5 v56 a5,5 0 0 1 -5,5 h-12.5 a5,5 0 0 1 -5,-5 v-56 a5,5 0 0 1 5,-5 Z"), o.pants)
    drawPath(svgPath("M40.5,100 h12.5 a5,5 0 0 1 5,5 v56 a5,5 0 0 1 -5,5 h-12.5 a5,5 0 0 1 -5,-5 v-56 a5,5 0 0 1 5,-5 Z"), o.pantsShade)
    o.trim?.let {
        drawRect(it, Offset(27f, 157f), Size(12.5f, 2.6f))
        drawRect(it, Offset(40.5f, 157f), Size(12.5f, 2.6f))
    }
    if (o.trackStripes) {
        drawRect(Color(0xFFE9EAEE), Offset(28f, 102f), Size(1.8f, 60f))
        drawRect(Color(0xFFE9EAEE), Offset(50.2f, 102f), Size(1.8f, 60f))
    }
    drawPath(svgPath("M24,170 Q24,163 30,163 L38,163 Q40,163 40,166 L40,172 L25,172 Q24,172 24,170 Z"), o.shoes)
    drawPath(svgPath("M56,170 Q56,163 50,163 L42,163 Q40,163 40,166 L40,172 L55,172 Q56,172 56,170 Z"), o.shoes)
    o.sole?.let {
        drawRoundRect(it, Offset(24f, 170f), Size(16f, 2.4f), 1.2f)
        drawRoundRect(it, Offset(40f, 170f), Size(16f, 2.4f), 1.2f)
    }

    // Корпус
    if (o.coat) {
        drawPath(svgPath("M21,64 Q21,55 30,55 L50,55 Q59,55 59,64 L62,139 Q62,142 59,142 L21,142 Q18,142 18,139 Z"), o.top)
        drawLine(o.shade, Offset(40f, 64f), Offset(40f, 142f), strokeWidth = 1.2f)
        drawPath(svgPath("M50,55 Q59,55 59,64 L62,139 Q62,142 59,142 L52,142 Z"), o.shade.copy(alpha = 0.7f))
    } else {
        drawPath(svgPath("M22,64 Q22,55 30,55 L50,55 Q58,55 58,64 L56,104 Q56,107 53,107 L27,107 Q24,107 24,104 Z"), o.top)
        drawPath(svgPath("M50,55 Q58,55 58,64 L56,104 Q56,107 53,107 L49,107 Z"), o.shade.copy(alpha = 0.75f))
    }
    o.innerTop?.let { inner ->
        drawPath(svgPath("M31,55 L49,55 L47,104 L33,104 Z"), inner)
        drawPath(svgPath("M31,55 L36,104 L27,107 L24,60 Z"), o.top)
        drawPath(svgPath("M49,55 L44,104 L53,107 L56,60 Z"), o.shade)
    }
    if (o.hood) {
        drawPath(svgPath("M28,55 Q40,66 52,55 Q50,51 40,51 Q30,51 28,55 Z"), if (o.innerTop != null && !o.hoodUp) Color(0xFFB8BCC4) else o.shade)
    } else if (o.innerTop == null) {
        drawPath(svgPath("M33,55 L40,71 L47,55 Z"), o.shirt ?: o.top)
    }
    o.tie?.let { drawPath(svgPath("M38.7,58 L41.3,58 L42.4,77 L40,81 L37.6,77 Z"), it) }
    if (!o.hood && !o.coat && o.shirt != null && o.shirt != o.top) {
        drawPath(svgPath("M33,55 L40,71 L35,73 L29,58 Z"), o.shade)
        drawPath(svgPath("M47,55 L40,71 L45,73 L51,58 Z"), o.shade)
    }
    if (o.coat) {
        drawPath(svgPath("M31,55 L40,72 L33,76 L26,60 Z"), o.shade)
        drawPath(svgPath("M49,55 L40,72 L47,76 L54,60 Z"), o.shade)
    }
    o.vest?.let { vest ->
        drawRoundRect(vest, Offset(25f, 62f), Size(30f, 40f), 4f)
        drawRoundRect(o.shade, Offset(28f, 80f), Size(10f, 9f), 1.5f)
        drawRoundRect(o.shade, Offset(42f, 80f), Size(10f, 9f), 1.5f)
        drawPath(svgPath("M45,66 l4,0 l0,4 l-2,2 l-2,-2 z"), Color(0xFF9AA0AA))
        drawRect(o.shade, Offset(25f, 99f), Size(30f, 4f))
    }
    if (o.badge && o.vest == null) drawRoundRect(Color(0xFFEEF2F8), Offset(44f, 66f), Size(7f, 4.5f), 1f)
    if (o.stripes) {
        o.trim?.let {
            drawRect(it, Offset(17f, 90f), Size(10f, 1.6f))
            drawRect(it, Offset(53f, 90f), Size(10f, 1.6f))
        }
    }
    if (o.stethoscope) {
        val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round)
        drawPath(svgPath("M33,56 Q30,72 36,82"), Color(0xFF3B3F48), style = stroke)
        drawPath(svgPath("M47,56 Q51,70 47,82"), Color(0xFF3B3F48), style = stroke)
        drawCircle(Color(0xFF9AA3B1), 2.8f, Offset(47f, 85f))
    }
    if (o.coat && o.badge) drawRoundRect(Color(0xFFDFEAF8), Offset(44f, 72f), Size(7f, 9f), 1f)
    if (o.headphones) drawPath(svgPath("M28,57 Q40,66 52,57"), Color(0xFF1B1C21), style = Stroke(width = 3f, cap = StrokeCap.Round))
    o.backpack?.let {
        drawRoundRect(it, Offset(25f, 56f), Size(3.6f, 36f), 1.8f)
        drawRoundRect(it, Offset(51.4f, 56f), Size(3.6f, 36f), 1.8f)
    }

    // Сумка через плечо
    if (o.bag || o.handbag != null) {
        val c = if (o.bag) Color(0xFF2D2E33) else o.handbag!!
        drawLine(c, Offset(27f, 57f), Offset(55f, 99f), strokeWidth = 2.2f)
        drawRoundRect(c, Offset(49f, 96f), Size(15f, 17f), 3f)
    }

    // Руки
    drawRoundRect(o.top, Offset(16.5f, 58f), Size(10f, 44f), 5f)
    o.trim?.let { if (!o.stripes) drawRect(it, Offset(16.5f, 96f), Size(10f, 2.4f)) }
    drawCircle(skinC, 4.8f, Offset(21.5f, 105f))

    drawRoundRect(o.shade, Offset(53.5f, 58f), Size(10f, 44f), 5f)
    o.trim?.let { if (!o.stripes) drawRect(it, Offset(53.5f, 96f), Size(10f, 2.4f)) }
    drawCircle(skinC, 4.8f, Offset(58.5f, 105f))
    if (o.briefcase) {
        drawRoundRect(Color(0xFF23252B), Offset(52f, 108f), Size(14f, 13f), 2f)
        drawRoundRect(Color.Transparent, Offset(56f, 105.5f), Size(6f, 3f), 1.2f, style = Stroke(width = 1.3f), border = Color(0xFF23252B))
    }
    if (o.bottle) {
        drawRoundRect(Color(0xFF3F6B3A), Offset(55.5f, 104f), Size(6f, 16f), 2f)
        drawRoundRect(Color(0xFF3F6B3A), Offset(57.2f, 98f), Size(2.6f, 7f), 1f)
        drawRect(Color(0xFFD9D3C2).copy(alpha = 0.7f), Offset(56f, 109f), Size(5f, 4f))
    }
    if (o.radio) {
        drawRect(Color(0xFF111111), Offset(50f, 56f), Size(5f, 9f))
        drawRect(Color(0xFF111111), Offset(52.5f, 49f), Size(1.4f, 8f))
    }

    // Голова
    drawRoundRect(skinC, Offset(36f, 48f), Size(8f, 10f), 3f)
    if (o.hoodUp) drawPath(svgPath("M16,40 Q14,8 40,7 Q66,8 64,40 Q63,56 52,58 L28,58 Q17,56 16,40 Z"), o.top)
    drawCircle(skinC, 3.6f, Offset(21.5f, 35f))
    drawCircle(skinC, 3.6f, Offset(58.5f, 35f))
    drawOvalCentered(skinC, Offset(40f, 32f), Size(36f, 38f))
    drawHair(hairStyle, hairC)
    drawOvalCentered(INK, Offset(33.5f, 35.5f), Size(3.8f, 5.2f))
    drawOvalCentered(INK, Offset(46.5f, 35.5f), Size(3.8f, 5.2f))
    if (o.glasses) {
        val stroke = Stroke(width = 1f)
        drawCircle(Color(0xFF3B3F48), 4.2f, Offset(33.5f, 35.5f), style = stroke)
        drawCircle(Color(0xFF3B3F48), 4.2f, Offset(46.5f, 35.5f), style = stroke)
        drawLine(Color(0xFF3B3F48), Offset(37.7f, 35.5f), Offset(42.3f, 35.5f), strokeWidth = 1f)
    }
    drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2.6f, Offset(29f, 41f))
    drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2.6f, Offset(51f, 41f))
    drawPath(svgPath("M37.6,43.4 Q40,45.2 42.4,43.4"), Color(0xFF8B4A3A), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
    drawMoodFace(mood, skinC)
    if (o.hoodUp) drawPath(svgPath("M19,30 Q20,10 40,10 Q60,10 61,30 Q58,16 40,15 Q22,16 19,30 Z"), o.shade)
    o.beanie?.let { drawPath(svgPath("M20,26 Q20,7 40,7 Q60,7 60,26 L60,28 L20,28 Z"), it) }
    drawHat(o.hat, o.cap, o.capBrim)
}

private fun DrawScope.drawHair(style: String, color: Color) {
    val d = when (style) {
        "messy" -> "M21,33 Q17,9 40,9 Q63,9 59,33 Q58,22 52,20 L50,26 L46,19 L42,25 L38,18 L34,25 L30,19 Q24,22 23,34 Z"
        "long" -> "M21,36 Q19,11 40,11 Q61,11 59,36 Q58,24 50,20 Q46,27 34,26 Q27,26 23,34 Z"
        "side" -> "M22,31 Q20,11 41,11 Q60,12 58,30 Q55,21 45,20 Q37,20 31,24 Q26,27 24,33 Z"
        "grey" -> "M22,30 Q22,13 40,13 Q58,13 58,30 Q55,22 47,21 Q40,24 33,22 Q26,23 24,31 Z"
        else -> "M22,31 Q19,11 40,11 Q61,11 58,31 Q56,21 48,20 Q44,26 34,25 Q27,24 24,32 Z"
    }
    drawPath(svgPath(d), color)
}

private fun DrawScope.drawMoodFace(mood: String?, skinC: Color) {
    when (mood) {
        "drunk" -> {
            drawCircle(Color(0xFFE8615A).copy(alpha = 0.45f), 4.2f, Offset(29f, 41f))
            drawCircle(Color(0xFFE8615A).copy(alpha = 0.45f), 4.2f, Offset(51f, 41f))
            drawRect(skinC, Offset(30f, 31f), Size(7f, 3.4f))
            drawRect(skinC, Offset(43f, 31f), Size(7f, 3.4f))
            drawPath(svgPath("M36.5,43 Q40,46.5 43.8,42.6"), Color(0xFF8B4A3A), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
        }
        "angry" -> {
            val stroke = Stroke(width = 1.5f, cap = StrokeCap.Round)
            drawLine(INK, Offset(30f, 30.5f), Offset(36.5f, 33f), strokeWidth = 1.5f)
            drawLine(INK, Offset(50f, 30.5f), Offset(43.5f, 33f), strokeWidth = 1.5f)
            drawPath(svgPath("M37,45 Q40,42.8 43,45"), Color(0xFF8B4A3A), style = stroke)
        }
        "high", "unwell" -> {
            drawRect(skinC, Offset(30f, 31.5f), Size(7f, 3.2f))
            drawRect(skinC, Offset(43f, 31.5f), Size(7f, 3.2f))
            drawLine(Color(0xFF5A4038), Offset(30.5f, 34.6f), Offset(36.5f, 34.6f), strokeWidth = 0.8f)
            drawLine(Color(0xFF5A4038), Offset(43.5f, 34.6f), Offset(49.5f, 34.6f), strokeWidth = 0.8f)
        }
        else -> Unit
    }
}

private fun DrawScope.drawHat(kind: String?, cap: Color?, brim: Color?) {
    if (kind == "rail" || kind == "police") {
        drawPath(svgPath("M17,20 Q19,6 40,5.5 Q61,6 63,20 Q63,24 58,24.5 L22,24.5 Q17,24 17,20 Z"), if (kind == "police") Color(0xFF34466E) else Color(0xFF1C3170))
        drawRect(Color(0xFFD6312F), Offset(21.5f, 20.5f), Size(37f, 6f))
        drawPath(svgPath("M22.5,26.5 L57.5,26.5 Q52,32 40,32 Q28,32 22.5,26.5 Z"), Color(0xFF15161A))
        if (kind == "rail") {
            drawPath(svgPath("M40,12.5 l2,2.2 l5,-1.4 l-3.2,3.2 l-3.8,0.6 l-3.8,-0.6 l-3.2,-3.2 l5,1.4 z"), Color(0xFFF2F4F8))
        } else {
            drawCircle(Color(0xFFE6C55A), 2.6f, Offset(40f, 15f))
        }
        return
    }
    if (kind == "guard" || cap != null) {
        val c = cap ?: Color(0xFF1D1E22)
        drawPath(svgPath("M20.5,27 Q20.5,9 40,9 Q59.5,9 59.5,27 Z"), c)
        drawPath(svgPath("M20,27 L60,27 Q58,32.5 40,31.5 Q23,31 20,27 Z"), if (kind == "guard") Color(0xFF111214) else (brim ?: Color(0xFF4B573C)))
        if (kind == "guard") drawPath(svgPath("M37.5,14.5 h5 v4 l-2.5,2.5 l-2.5,-2.5 z"), Color(0xFFAEB4BF))
    }
}

// ---------------------------------------------------------------------------
// Сидящий пассажир
// ---------------------------------------------------------------------------

private fun DrawScope.drawSeated(o: Outfit, skinC: Color, hairC: Color, mood: String) {
    val long = o.hairStyle == "long"
    if (long) drawPath(svgPath("M20,22 Q18,44 22,56 L48,56 Q52,44 50,22 Z"), hairC)
    if (o.hoodUp) drawPath(svgPath("M17,26 Q16,2 35,2 Q54,2 53,26 Q53,38 45,40 L25,40 Q17,38 17,26 Z"), o.top)

    // Ноги к зрителю
    drawRoundRect(o.pants, Offset(18f, 71f), Size(15f, 12f), 5f)
    drawRoundRect(o.pantsShade, Offset(37f, 71f), Size(15f, 12f), 5f)
    drawRoundRect(o.pants, Offset(19.5f, 81f), Size(12f, 15f), 4.5f)
    drawRoundRect(o.pantsShade, Offset(38.5f, 81f), Size(12f, 15f), 4.5f)
    drawRoundRect(o.shoes, Offset(17f, 93.5f), Size(15f, 5.5f), 2.7f)
    drawRoundRect(o.shoes, Offset(38f, 93.5f), Size(15f, 5.5f), 2.7f)

    // Корпус
    drawPath(svgPath("M16,50 Q16,38 26,38 L44,38 Q54,38 54,50 L53,76 L17,76 Z"), o.top)
    drawPath(svgPath("M44,38 Q54,38 54,50 L53,76 L46,76 Z"), o.shade.copy(alpha = 0.7f))
    o.innerTop?.let { drawPath(svgPath("M28,38 L42,38 L41,76 L29,76 Z"), it) }
    if (o.hood) {
        drawPath(svgPath("M25,38 Q35,47 45,38 Q43,34 35,34 Q27,34 25,38 Z"), if (o.innerTop != null) Color(0xFFB8BCC4) else o.shade)
    } else if (o.innerTop == null) {
        drawPath(svgPath("M29.5,38 L35,50 L40.5,38 Z"), o.shirt ?: o.top)
    }
    if (o.trackStripes) {
        drawRect(Color(0xFFE9EAEE), Offset(20f, 72f), Size(1.4f, 24f))
        drawRect(Color(0xFFE9EAEE), Offset(48.6f, 72f), Size(1.4f, 24f))
    }
    o.tie?.let { drawPath(svgPath("M34,40 L36,40 L36.8,53 L35,56 L33.2,53 Z"), it) }
    if (o.headphones) drawPath(svgPath("M25,40 Q35,47 45,40"), Color(0xFF1B1C21), style = Stroke(width = 2.6f, cap = StrokeCap.Round))

    // Руки
    drawRoundRect(o.top, Offset(10f, 44f), Size(9f, 28f), 4.5f)
    drawRoundRect(o.shade, Offset(51f, 44f), Size(9f, 28f), 4.5f)
    drawCircle(skinC, 4f, Offset(16f, 72f))
    drawCircle(skinC, 4f, Offset(54f, 72f))
    o.backpack?.let { drawRoundRect(it, Offset(21f, 60f), Size(28f, 18f), 5f) }
    o.handbag?.let { drawRoundRect(it, Offset(42f, 62f), Size(14f, 12f), 3f) }
    if (o.briefcase) drawRoundRect(Color(0xFF23252B), Offset(22f, 64f), Size(26f, 12f), 2f)
    if (o.bottle) {
        drawRoundRect(Color(0xFF3F6B3A), Offset(50f, 60f), Size(6.5f, 15f), 2f)
        drawRoundRect(Color(0xFF3F6B3A), Offset(51.9f, 54f), Size(2.7f, 7f), 1f)
    }
    drawRoundRect(skinC, Offset(31.5f, 31f), Size(7f, 8f), 3f)

    // Голова
    drawCircle(skinC, 2.8f, Offset(21.5f, 23f))
    drawCircle(skinC, 2.8f, Offset(48.5f, 23f))
    drawOvalCentered(skinC, Offset(35f, 21f), Size(27f, 28f))
    drawSeatHair(o.hairStyle, hairC)
    if (o.hoodUp) drawPath(svgPath("M19,18 Q20,5 35,5 Q50,5 51,18 Q48,9 35,9 Q22,9 19,18 Z"), o.shade)
    o.beanie?.let { drawPath(svgPath("M21,16 Q21,3 35,3 Q49,3 49,16 L49,18 L21,18 Z"), it) }
    if (o.cap != null) {
        drawPath(svgPath("M21,17 Q21,5 35,5 Q49,5 49,17 Z"), o.cap)
        drawPath(svgPath("M21,17 L49,17 Q47,21 35,20.5 Q23,20 21,17 Z"), o.capBrim ?: Color(0xFF4B573C))
    }
    drawSeatedMood(mood, skinC)
    if (o.glasses) {
        val stroke = Stroke(width = 0.9f)
        drawCircle(Color(0xFF3B3F48), 3.3f, Offset(30.5f, 23.5f), style = stroke)
        drawCircle(Color(0xFF3B3F48), 3.3f, Offset(39.5f, 23.5f), style = stroke)
        drawLine(Color(0xFF3B3F48), Offset(33.8f, 23.5f), Offset(36.2f, 23.5f), strokeWidth = 0.9f)
    }
}

private fun DrawScope.drawSeatHair(style: String, color: Color) {
    val d = when (style) {
        "messy" -> "M21,23 Q18,4 35,4 Q52,4 49,23 Q48,14 44,12 L42,17 L39,11 L36,16 L33,10 L30,16 L27,12 Q23,15 22.5,24 Z"
        "long" -> "M21.5,24 Q20,6 35,6 Q50,6 48.5,24 Q47,15 41,13 Q37,18 29,17 Q24,18 23,25 Z"
        "side" -> "M22,21 Q21,6 36,6 Q49,7 48,20 Q46,13 39,13 Q33,13 28,16 Q24,18 23.5,23 Z"
        "grey" -> "M22,20 Q22,8 35,8 Q48,8 48,20 Q46,14 40,13 Q35,15 30,14 Q25,15 23.5,21 Z"
        else -> "M22,21.5 Q20,6 35,6 Q50,6 48,21.5 Q46,13 40,13 Q37,17 30,16.5 Q25,16 23.5,22 Z"
    }
    drawPath(svgPath(d), color)
}

private fun DrawScope.drawSeatedMood(mood: String, skinC: Color) {
    when (mood) {
        "drunk" -> {
            val stroke = Stroke(width = 1.3f, cap = StrokeCap.Round)
            drawPath(svgPath("M28.5,23.8 Q30.5,22.4 32.5,23.8"), INK, style = stroke)
            drawPath(svgPath("M37.5,23.8 Q39.5,22.4 41.5,23.8"), INK, style = stroke)
            drawPath(svgPath("M31.5,29 Q35,32 38.8,28.4"), Color(0xFF8B4A3A), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFE8615A).copy(alpha = 0.5f), 3.2f, Offset(27f, 27.5f))
            drawCircle(Color(0xFFE8615A).copy(alpha = 0.5f), 3.2f, Offset(43f, 27.5f))
            drawCircle(Color(0xFFE8615A).copy(alpha = 0.45f), 1.4f, Offset(35f, 25.5f))
        }
        "high" -> {
            drawOvalCentered(INK, Offset(30.5f, 24.2f), Size(3f, 2.4f))
            drawOvalCentered(INK, Offset(39.5f, 24.2f), Size(3f, 2.4f))
            drawLine(Color(0xFF5A4038), Offset(28.5f, 23.3f), Offset(32.5f, 23.3f), strokeWidth = 1f)
            drawLine(Color(0xFF5A4038), Offset(37.5f, 23.3f), Offset(41.5f, 23.3f), strokeWidth = 1f)
            drawLine(Color(0xFF8B4A3A), Offset(33f, 30.2f), Offset(37f, 30.2f), strokeWidth = 1.1f, cap = StrokeCap.Round)
        }
        "angry" -> {
            drawLine(INK, Offset(28f, 20f), Offset(32.5f, 22f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawLine(INK, Offset(42f, 20f), Offset(37.5f, 22f), strokeWidth = 1.3f, cap = StrokeCap.Round)
            drawOvalCentered(INK, Offset(30.5f, 24.5f), Size(2.8f, 3.6f))
            drawOvalCentered(INK, Offset(39.5f, 24.5f), Size(2.8f, 3.6f))
            drawPath(svgPath("M32,30.5 Q35,28.5 38,30.5"), Color(0xFF8B4A3A), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFE0664F).copy(alpha = 0.45f), 2.3f, Offset(27f, 28f))
            drawCircle(Color(0xFFE0664F).copy(alpha = 0.45f), 2.3f, Offset(43f, 28f))
        }
        "unwell" -> {
            drawOvalCentered(INK, Offset(30.5f, 23.5f), Size(2.8f, 4f))
            drawOvalCentered(INK, Offset(39.5f, 23.5f), Size(2.8f, 4f))
            drawPath(svgPath("M32.5,30 Q35,29 37.5,30"), Color(0xFF8B4A3A), style = Stroke(width = 1.1f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2f, Offset(27f, 27.5f))
            drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2f, Offset(43f, 27.5f))
        }
        else -> {
            drawOvalCentered(INK, Offset(30.5f, 23.5f), Size(2.8f, 4f))
            drawOvalCentered(INK, Offset(39.5f, 23.5f), Size(2.8f, 4f))
            drawPath(svgPath("M32.5,29 Q35,30.6 37.5,29"), Color(0xFF8B4A3A), style = Stroke(width = 1.1f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2f, Offset(27f, 27.5f))
            drawCircle(Color(0xFFF09A86).copy(alpha = 0.28f), 2f, Offset(43f, 27.5f))
        }
    }
}

// --- Небольшие обёртки над DrawScope: drawRect/drawRoundRect (x,y = левый верхний угол, как в
// оригинале) — переиспользуют стандартные функции DrawScope напрямую; drawOvalCentered —
// единственная с другой семантикой (x,y = центр, как cx/cy у SVG <ellipse>), поэтому у неё
// отдельное имя, чтобы не совпасть по сигнатуре с DrawScope.drawOval(color, topLeft, size). ---

private fun DrawScope.drawRoundRect(color: Color, topLeft: Offset, size: Size, radius: Float) =
    drawRoundRect(color = color, topLeft = topLeft, size = size, cornerRadius = CornerRadius(radius, radius))

private fun DrawScope.drawRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
    style: Stroke,
    border: Color
) = drawRoundRect(color = border, topLeft = topLeft, size = size, cornerRadius = CornerRadius(radius, radius), style = style)

private fun DrawScope.drawOvalCentered(color: Color, center: Offset, size: Size) =
    drawOval(color = color, topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2), size = size)
