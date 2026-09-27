package ru.vsm.mobile.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity

/**
 * Перенос `components/shift/MedRoom.jsx` — помещение заступа на смену: медпункт (предрейсовый
 * осмотр) или нарядная (инструктаж у начальника поезда). Плоская иллюстрация в стиле вагона,
 * сцена 800×700 условных единиц, вписывается по ширине экрана (см. карточку роли — здесь это
 * проще, чем «cover от нижней кромки» на сайте, поскольку сцена не обрезается вьюпортом).
 *
 * @param place "medpoint" | "briefing"
 * @param npc "medic" | "chief" — кто разговаривает с проводником
 * @param readout показание прибора (алкотестер/планшет): null — прибор молчит
 */
private const val W = 800f
private const val H = 700f
private const val PERSON = 320f
private const val FEET = 684f

data class MedReadout(val icon: String, val text: String, val bad: Boolean = false) // icon: thermometer|alert|stethoscope

@Composable
fun MedRoom(
    place: String = "medpoint",
    npc: String = "medic",
    heroTalking: Boolean = false,
    npcTalking: Boolean = false,
    readout: MedReadout? = null,
    modifier: Modifier = Modifier
) {
    val briefing = place == "briefing"
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = if (widthPx > 0f) widthPx / W else 1f
        fun px(v: Float) = with(density) { v.toDp() }

        Box(Modifier.fillMaxWidth().size(px(W * scale), px(H * scale))) {
            Canvas(Modifier.matchParentSize()) {
                withTransformScale(scale) {
                    drawRoomShell(briefing)
                    if (briefing) drawBriefing(readout) else drawMedpoint(readout)
                }
            }

            val personW = PERSON * (80f / 180f) * scale
            val personH = PERSON * scale
            Box(
                Modifier.offset(x = px(200f * scale - personW / 2), y = px((FEET - PERSON) * scale)).size(px(personW), px(personH))
            ) {
                WalkingPerson(outfit = "conductor", facing = "right", talking = heroTalking, modifier = Modifier.matchParentSize())
            }
            Box(
                Modifier.offset(x = px(600f * scale - personW / 2), y = px((FEET - PERSON) * scale)).size(px(personW), px(personH))
            ) {
                WalkingPerson(
                    outfit = npc,
                    facing = "left",
                    hair = if (npc == "medic") 1 else 2,
                    skin = if (npc == "medic") 4 else 1,
                    talking = npcTalking,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Оболочка комнаты: потолок, стена, пол, часы, табличка
// ---------------------------------------------------------------------------

private val CEIL = Color(0xFFEDEFF3)
private val WALL = Color(0xFFF7F8FB)
private val WAINSCOT = Color(0xFFDCE2EC)
private val FLOOR = Color(0xFFC9CFDA)
private val FLOOR_LINE = Color(0xFFB6BECC)
private val TILE = Color(0xFFE7ECF3)
private val RAIL = Color(0xFFB9C2D2)
private val CABINET = Color(0xFFEFF2F7)
private val DESK = Color(0xFFDCC9A6)
private val NAVY = Color(0xFF0B3D91)

private fun DrawScope.drawRoomShell(briefing: Boolean) {
    drawRect(CEIL, Offset(-1200f, -800f), Size(3200f, 842f))
    drawRect(WALL, Offset(-1200f, 40f), Size(3200f, 548f))
    drawRect(RAIL, Offset(-1200f, 40f), Size(3200f, 8f))
    if (briefing) {
        drawRect(WAINSCOT, Offset(-1200f, 430f), Size(3200f, 155f))
    } else {
        drawRect(TILE, Offset(-1200f, 430f), Size(3200f, 155f))
        var tx = -1190f
        while (tx < 2000f) {
            drawLine(Color.White.copy(alpha = 0.5f), Offset(tx, 430f), Offset(tx, 585f), strokeWidth = 1f)
            tx += 28f
        }
    }
    drawRect(RAIL, Offset(-1200f, 426f), Size(3200f, 5f))
    drawRect(FLOOR, Offset(-1200f, 585f), Size(3200f, 500f))
    var fx = -1195f
    while (fx < 2000f) {
        drawLine(FLOOR_LINE, Offset(fx, 585f), Offset(fx, 1085f), strokeWidth = 1.4f)
        fx += 90f
    }
    drawRect(Color(0xFF9AA3B4), Offset(-1200f, 578f), Size(3200f, 8f))
    // Потолочный светильник
    drawRoundRect(Color(0xFFDDE2EA), Offset(300f, 40f), Size(200f, 10f), CornerRadius(3f))
    drawRoundRect(Color(0xFFFFF4D6), Offset(310f, 48f), Size(180f, 4f), CornerRadius(2f))
    // Табличка над дверью
    drawPlate(400f, 82f, 330f, if (briefing) "Нарядная · Ленинградский вокзал" else "Медпункт · Ленинградский вокзал")
}

private fun DrawScope.drawPlate(x: Float, y: Float, w: Float, text: String) {
    drawRoundRect(Color(0xFF1C1C1E).copy(alpha = 0.88f), Offset(x - w / 2, y), Size(w, 34f), CornerRadius(8f))
    drawContext.canvas.nativeCanvas.let { native ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 16f
            isAntiAlias = true
        }
        native.drawText(text, x, y + 23f, paint)
    }
}

private fun DrawScope.drawWindow(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(Color(0xFFCBD3E0), Offset(x - 8f, y - 8f), Size(w + 16f, h + 16f), CornerRadius(6f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF9EC3EE), Color(0xFFEAF1FB))), Offset(x, y), Size(w, h * 0.72f))
    drawRect(Color(0xFFB9C1CD), Offset(x, y + h * 0.72f), Size(w, h * 0.28f))
    drawRect(Color(0xFFE9EEF5), Offset(x - 8f, y + h * 0.62f), Size(w * 0.55f, h * 0.2f))
    for (i in 0 until 3) {
        drawRoundRect(Color(0xFFDCE3EE), Offset(x + i * w / 3f + 4f, y + h * 0.42f), Size(w / 3f - 8f, h * 0.18f), CornerRadius(4f))
    }
    drawRect(Color(0xFFCBD3E0), Offset(x + w / 2 - 3f, y), Size(6f, h))
}

// ---------------------------------------------------------------------------
// Медпункт: шкаф, кушетка, стол медработника с алкотестером/тонометром/термометром
// ---------------------------------------------------------------------------

private fun DrawScope.drawMedpoint(readout: MedReadout?) {
    // Шкаф со стеклянными дверцами
    drawRoundRect(CABINET, Offset(6f, 258f), Size(120f, 324f), CornerRadius(6f))
    drawRoundRect(Color(0xFFCFE0F4).copy(alpha = 0.5f), Offset(14f, 266f), Size(104f, 186f), CornerRadius(3f))
    drawRoundRect(Color(0xFFD8DEE8), Offset(14f, 460f), Size(50f, 116f), CornerRadius(3f))
    drawRoundRect(Color(0xFFD8DEE8), Offset(68f, 460f), Size(50f, 116f), CornerRadius(3f))

    // Таблица для проверки зрения
    drawRoundRect(Color(0xFFF7F7FA), Offset(166f, 158f), Size(96f, 150f), CornerRadius(4f))

    // Кушетка
    drawRoundRect(Color(0xFFE3E7EE), Offset(136f, 458f), Size(18f, 70f), CornerRadius(6f))
    drawRoundRect(Color(0xFFF2F4F8), Offset(140f, 500f), Size(170f, 26f), CornerRadius(8f))
    drawRoundRect(Color(0xFFEDF1F6), Offset(186f, 496f), Size(100f, 10f), CornerRadius(3f))
    drawRect(Color(0xFFB9C1CD), Offset(150f, 526f), Size(8f, 56f))
    drawRect(Color(0xFFB9C1CD), Offset(292f, 526f), Size(8f, 56f))

    drawWindow(326f, 160f, 160f, 196f)

    // Плакат «Предрейсовый осмотр»
    drawRoundRect(Color(0xFFFFFFFF), Offset(532f, 178f), Size(118f, 156f), CornerRadius(6f))
    drawRoundRect(NAVY, Offset(532f, 178f), Size(118f, 38f), CornerRadius(6f))

    // Раковина с зеркалом
    drawRoundRect(Color(0xFFCFE0F4).copy(alpha = 0.6f), Offset(690f, 286f), Size(92f, 110f), CornerRadius(10f))
    drawRoundRect(Color(0xFFEDF1F6), Offset(686f, 440f), Size(100f, 18f), CornerRadius(9f))
    drawRoundRect(Color(0xFFD8DEE8), Offset(702f, 458f), Size(68f, 124f), CornerRadius(4f))

    // Стол медработника
    drawRoundRect(DESK, Offset(302f, 486f), Size(218f, 14f), CornerRadius(4f))
    drawRect(Color(0xFFB79B6E), Offset(312f, 500f), Size(10f, 82f))
    drawRoundRect(Color(0xFFCBB388), Offset(440f, 500f), Size(72f, 82f), CornerRadius(3f))

    // Монитор
    drawRoundRect(Color(0xFF2A2D33), Offset(430f, 382f), Size(100f, 72f), CornerRadius(6f))
    drawRoundRect(Color(0xFF0B3D91).copy(alpha = 0.85f), Offset(436f, 388f), Size(88f, 58f), CornerRadius(3f))

    // Терминал предрейсового осмотра: алкотестер с дисплеем
    val bad = readout?.bad == true
    drawRoundRect(Color(0xFFE9EDF4), Offset(320f, 400f), Size(108f, 86f), CornerRadius(10f))
    drawRoundRect(if (bad) Color(0xFF3A1E1B) else Color(0xFF15201A), Offset(326f, 406f), Size(96f, 46f), CornerRadius(6f))
    val text = readout?.text ?: "0,00 мг/л"
    drawContext.canvas.nativeCanvas.let { native ->
        val paint = android.graphics.Paint().apply {
            color = (if (bad) Color(0xFFFF6B5F) else Color(0xFF7FE0A6)).let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 15f
            isAntiAlias = true
        }
        native.drawText(text, 374f, 437f, paint)
    }
    drawCircle(Color(0xFF9AA3B1), 5f, Offset(336f, 467f))
    drawCircle(Color(0xFFD6312F), 5f, Offset(352f, 467f))
    drawRoundRect(Color(0xFFCBD1DC), Offset(366f, 462f), Size(44f, 10f), CornerRadius(5f))

    // Тонометр
    drawRoundRect(Color(0xFF3B4252), Offset(436f, 472f), Size(30f, 14f), CornerRadius(7f))
    drawCircle(Color(0xFFE9EDF4), 9f, Offset(503f, 474f), style = Stroke(1.6f))

    // Термометр
    rotate(-8f, pivot = Offset(336f, 480f)) {
        drawRoundRect(Color(0xFFE9EDF4), Offset(316f, 477f), Size(42f, 7f), CornerRadius(3.5f))
        drawRoundRect(Color(0xFFD6312F), Offset(346f, 478f), Size(10f, 5f), CornerRadius(2f))
    }
}

// ---------------------------------------------------------------------------
// Нарядная: шкафчики, схема маршрута, табло, вешалка, стол
// ---------------------------------------------------------------------------

private fun DrawScope.drawBriefing(readout: MedReadout?) {
    for (i in 0 until 3) {
        drawRoundRect(Color(0xFFE1E6EE), Offset(6f + i * 42f, 250f), Size(40f, 330f), CornerRadius(4f))
        drawRoundRect(Color(0xFFCBD3E0), Offset(14f + i * 42f, 304f), Size(18f, 12f), CornerRadius(2f))
    }
    drawWindow(150f, 170f, 146f, 180f)

    // Схема маршрута
    drawRoundRect(Color(0xFFFFFFFF), Offset(316f, 150f), Size(200f, 190f), CornerRadius(8f))
    val stops = listOf(344f to 202f, 360f to 240f, 392f to 278f, 420f to 316f)
    for (i in 0 until stops.size - 1) {
        drawLine(NAVY.copy(alpha = 0.5f), Offset(stops[i].first, stops[i].second), Offset(stops[i + 1].first, stops[i + 1].second), strokeWidth = 2f)
    }
    stops.forEach { (cx, cy) -> drawCircle(NAVY, 6f, Offset(cx, cy)) }

    // Табло: номер поезда
    drawRoundRect(Color(0xFF14161A), Offset(534f, 170f), Size(152f, 130f), CornerRadius(8f))
    drawContext.canvas.nativeCanvas.let { native ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0xFF, 0xB0, 0x40)
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        native.drawText("№ 752", 550f, 224f, paint)
    }

    // Вешалка с пальто
    drawRect(Color(0xFF8A93A3), Offset(738f, 300f), Size(6f, 282f))
    drawPath(svgPath("M722,316C712,330 708,380 712,450H770C774,380 770,330 760,316Z"), Color(0xFF1C3170))

    // Стол с документами и планшетом
    drawRoundRect(DESK, Offset(300f, 486f), Size(220f, 14f), CornerRadius(4f))
    drawRect(Color(0xFFB79B6E), Offset(312f, 500f), Size(10f, 82f))
    drawRect(Color(0xFFB79B6E), Offset(498f, 500f), Size(10f, 82f))
    val bad = readout?.bad == true
    drawPath(svgPath("M396,486L404,420H474L470,486Z"), Color(0xFF2A2D33))
    drawPath(svgPath("M404,426H468L464,478H400Z"), if (bad) Color(0xFF3A1E1B) else Color(0xFF15201A))
    if (readout != null) {
        drawContext.canvas.nativeCanvas.let { native ->
            val paint = android.graphics.Paint().apply {
                color = (if (bad) Color(0xFFFF6B5F) else Color(0xFF7FE0A6)).let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 13f
                isAntiAlias = true
            }
            native.drawText(readout.text, 434f, 458f, paint)
        }
    }
}
