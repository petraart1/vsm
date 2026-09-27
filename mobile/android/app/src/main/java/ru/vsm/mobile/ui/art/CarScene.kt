package ru.vsm.mobile.ui.art

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberInfiniteTransition
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import ru.vsm.mobile.R

/**
 * Перенос `components/shift/CarScene.jsx` — вагон в разрезе («кукольный домик»): стена с окнами
 * (за ними бежит пейзаж), пассажиры в креслах, проводник идёт по проходу.
 *
 * В отличие от веб-версии сцена не скроллится камерой за узким вьюпортом — целиком вписывается
 * по ширине экрана (см. карточку роли), поэтому здесь нет краевых стрелок-вызовов «за пределами
 * экрана» (`.edge` на сайте): все пассажиры и хотспоты видны сразу.
 *
 * Управление: тап по полу — идти в точку; тап по пассажиру/хотспоту — подойти и, если рядом,
 * сразу выполнить действие; педали внизу — идти влево/вправо, пока зажаты.
 */
private const val H = 300f
private const val VESTIBULE = 150f
private const val SPEED = 140f // мировых px/с
private const val REACH = 58f

data class CarClass(
    val key: String,
    val title: String,
    val car: Int,
    val seats: Int,
    val spacing: Float,
    val seat: Color,
    val seatDark: Color,
    val headrest: Color,
    val occupancy: Float,
    val tables: Boolean = false,
    val lamps: Boolean = false
)

val CAR_CLASSES: Map<String, CarClass> = mapOf(
    "STANDARD" to CarClass("STANDARD", "Стандарт", 5, 12, 86f, Color(0xFF6F7F9C), Color(0xFF566582), Color(0xFFE7EBF2), 0.8f),
    "COMFORT" to CarClass("COMFORT", "Комфорт", 4, 10, 96f, Color(0xFF2F5CA8), Color(0xFF244A8A), Color(0xFFE7EBF2), 0.75f),
    "BUSINESS" to CarClass("BUSINESS", "Бизнес", 2, 8, 120f, Color(0xFF1C2D52), Color(0xFF142240), Color(0xFFD7DEEA), 0.7f, tables = true),
    "FIRST" to CarClass("FIRST", "Первый", 1, 6, 152f, Color(0xFFC6CFDD), Color(0xFF9FABBE), Color(0xFFF4F6FA), 0.67f, tables = true, lamps = true)
)

fun worldWidth(cls: CarClass): Float = VESTIBULE * 2 + cls.seats * cls.spacing
fun seatX(cls: CarClass, i: Int): Float = VESTIBULE + cls.spacing * (i + 0.5f)

data class Passenger(val seat: Int, val variant: Int, val kid: Boolean = false, val phone: Boolean = false)
data class Hotspot(val key: String, val x: Float, val icon: String, val title: String, val state: String = "idle") // idle|ok|fault
data class SignalCall(val key: String, val seat: Int = -1, val vestibuleEnd: Boolean = false, val urgent: Boolean = false, val remaining: Float, val total: Float)

@Composable
fun CarScene(
    cls: CarClass,
    passengers: List<Passenger>,
    hotspots: List<Hotspot> = emptyList(),
    signals: List<SignalCall> = emptyList(),
    moods: Map<Int, String> = emptyMap(),
    moving: Boolean = false,
    stationName: String? = null,
    disabled: Boolean = false,
    focusSeat: Int? = null,
    modifier: Modifier = Modifier,
    onInteract: ((type: String, key: String) -> Unit)? = null
) {
    val W = worldWidth(cls)
    fun sigX(sg: SignalCall) = if (sg.vestibuleEnd) W - VESTIBULE * 0.62f else seatX(cls, sg.seat)

    var heroX by remember(cls.key) { mutableFloatStateOf(VESTIBULE * 0.55f) }
    var dir by remember { mutableFloatStateOf(0f) }
    var target by remember { mutableStateOf<Float?>(null) }
    var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var walking by remember { mutableStateOf(false) }
    var facing by remember { mutableStateOf("right") }

    fun interactables(): List<Triple<String, String, Float>> {
        val hs = hotspots.filter { it.state == "idle" }.map { Triple("hotspot", it.key, it.x) }
        val sg = signals.map { Triple("signal", it.key, sigX(it)) }
        return hs + sg
    }

    val near = interactables().filter { abs(it.third - heroX) < REACH }.minByOrNull { abs(it.third - heroX) }

    fun act(type: String, key: String, x: Float) {
        if (disabled) return
        if (abs(x - heroX) < REACH) {
            onInteract?.invoke(type, key)
        } else {
            val side = if (x > heroX) -26f else 26f
            target = (x + side).coerceIn(24f, W - 24f)
            pendingAction = type to key
        }
    }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
            last = now
            var v = 0f
            if (!disabled) {
                val d = dir
                val t = target
                if (d != 0f) {
                    v = d * SPEED
                    target = null
                    pendingAction = null
                } else if (t != null) {
                    val delta = t - heroX
                    if (abs(delta) < 2f) {
                        target = null
                        val a = pendingAction
                        pendingAction = null
                        a?.let { (type, key) -> onInteract?.invoke(type, key) }
                    } else {
                        v = sign(delta) * min(SPEED, abs(delta) / max(dt, 0.001f))
                    }
                }
            }
            heroX = (heroX + v * dt).coerceIn(24f, W - 24f)
            walking = abs(v) > 1f
            if (v > 1f) facing = "right" else if (v < -1f) facing = "left"
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = if (widthPx > 0f) widthPx / W else 1f
        fun px(v: Float) = with(density) { v.toDp() }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(W / H)
                .pointerInput(cls.key, disabled) {
                    detectTapGestures { offset ->
                        if (!disabled) {
                            target = (offset.x / scale).coerceIn(0f, W)
                            pendingAction = null
                        }
                    }
                }
        ) {
            Landscape(moving = moving, stationName = stationName, modifier = Modifier.matchParentSize())

            Canvas(Modifier.matchParentSize()) {
                withTransformScale(scale) { drawWagon(cls, W) }
            }

            passengers.forEach { p ->
                val w = 70f * scale
                val h = 100f * (if (p.kid) 0.72f else 1f) * scale
                Box(
                    Modifier.offset(
                        x = px(seatX(cls, p.seat) * scale - 35f * (if (p.kid) 0.72f else 1f) * scale),
                        y = px((if (p.kid) 170f else 142f) * scale)
                    ).size(px(w), px(h))
                ) {
                    SeatedPerson(
                        variant = p.variant,
                        kid = p.kid,
                        phone = p.phone && moods[p.seat] == null,
                        mood = moods[p.seat] ?: "calm",
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            hotspots.forEach { h ->
                HotspotButton(
                    h,
                    Modifier.offset(x = px((h.x - 20f) * scale), y = px(88f * scale))
                ) { act("hotspot", h.key, h.x) }
            }

            signals.forEach { s ->
                val x = sigX(s)
                SignalButton(
                    s,
                    Modifier.offset(x = px((x - 22f) * scale), y = px(86f * scale))
                ) { act("signal", s.key, x) }
            }

            val heroW = 150f * (80f / 180f) * scale
            val heroH = 150f * scale
            Box(
                Modifier.offset(x = px((heroX - 33f) * scale), y = px(138f * scale)).size(px(heroW), px(heroH))
            ) {
                WalkingPerson(outfit = "conductor", walking = walking, facing = facing, modifier = Modifier.matchParentSize())
            }
        }

        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WalkPad(left = true) { pressed -> if (!disabled) dir = if (pressed) -1f else 0f }
                if (near != null && !disabled) {
                    val (type, key, x) = near
                    Button(onClick = { act(type, key, x) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text(if (type == "hotspot") "Проверить" else "Подойти к пассажиру")
                    }
                } else {
                    Text(if (disabled) "" else "Коснитесь, куда идти", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                WalkPad(left = false) { pressed -> if (!disabled) dir = if (pressed) 1f else 0f }
            }
        }
    }
}

@Composable
private fun WalkPad(left: Boolean, onPress: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .pointerInput(left) {
                detectTapGestures(onPress = {
                    onPress(true)
                    tryAwaitRelease()
                    onPress(false)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(if (left) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right),
            contentDescription = if (left) "Идти влево" else "Идти вправо"
        )
    }
}

@Composable
private fun HotspotButton(h: Hotspot, modifier: Modifier, onClick: () -> Unit) {
    val bg = when (h.state) {
        "ok" -> MaterialTheme.colorScheme.primary
        "fault" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = if (h.state == "idle") MaterialTheme.colorScheme.primary else Color.White
    Box(
        modifier
            .size(40.dp)
            .background(bg, CircleShape)
            .pointerInput(h.key) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        val icon = when (h.state) {
            "ok" -> R.drawable.ic_check
            "fault" -> R.drawable.ic_alert
            else -> iconResOrNull(h.icon) ?: R.drawable.ic_alert
        }
        Icon(painter = painterResource(icon), contentDescription = h.title, tint = fg)
    }
}

@Composable
private fun SignalButton(s: SignalCall, modifier: Modifier, onClick: () -> Unit) {
    val ringT = rememberInfiniteTransition(label = "signal")
    val float by ringT.animateFloat(0f, -4f, InfiniteRepeatableSpec(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "float")
    val color = if (s.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val progress = (s.remaining / max(s.total, 0.001f)).coerceIn(0f, 1f)
    Box(
        modifier
            .offset(y = with(LocalDensity.current) { float.toDp() })
            .size(44.dp)
            .pointerInput(s.key) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 3.dp.toPx()
            drawCircle(color.copy(alpha = 0.14f), radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (s.urgent) {
            Text("!", color = color, style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(painter = painterResource(R.drawable.ic_hand), contentDescription = "Пассажир зовёт проводника", tint = color)
        }
    }
}

private fun iconResOrNull(name: String): Int? = when (name) {
    "check" -> R.drawable.ic_check
    "alert" -> R.drawable.ic_alert
    "hand" -> R.drawable.ic_hand
    else -> null
}

// ---------------------------------------------------------------------------
// Пейзаж за окнами: небо и бегущие холмы (упрощённый перенос трёхслойного параллакса сайта).
// ---------------------------------------------------------------------------

@Composable
private fun Landscape(moving: Boolean, stationName: String?, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "land")
    val shift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "shift"
    )
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF9EC3EE), Color(0xFFD6E6F7), Color(0xFFEEF4FB))))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val dx = if (moving) shift * w else 0f
            val hillY = size.height * 0.23f
            val hillH = size.height * 0.19f
            var x = -w + (dx % w)
            while (x < w * 2) {
                drawRoundRect(Color(0xFFC5D4E8), Offset(x, hillY), Size(w, hillH), CornerRadius(hillH / 2))
                x += w
            }
        }
        if (!moving && stationName != null) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    stationName,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFF0B3D91), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Салон (стена с окнами, потолок, пол, кресла) — Canvas в мировых координатах.
// ---------------------------------------------------------------------------

private fun DrawScope.drawWagon(cls: CarClass, W: Float) {
    val winW = min(cls.spacing - 22f, 78f)
    val wall = Path().apply {
        addRect(Rect(0f, 0f, W, H))
        for (i in 0 until cls.seats) {
            val cx = seatX(cls, i)
            addRoundRect(RoundRect(cx - winW / 2, 52f, cx + winW / 2, 124f, CornerRadius(14f)))
        }
        addRoundRect(RoundRect(VESTIBULE * 0.5f - 16f, 60f, VESTIBULE * 0.5f + 16f, 130f, CornerRadius(8f)))
        addRoundRect(RoundRect(W - VESTIBULE * 0.5f - 16f, 60f, W - VESTIBULE * 0.5f + 16f, 130f, CornerRadius(8f)))
        fillType = PathFillType.EvenOdd
    }
    drawPath(wall, Color(0xFFF4F6F9))
    drawRect(Color(0xFFFBFCFD), Offset(0f, 0f), Size(W, 20f))
    drawRect(Color.White.copy(alpha = 0.9f), Offset(0f, 18f), Size(W, 3f))
    drawRoundRect(Color(0xFFD5DBE4), Offset(VESTIBULE, 36f), Size(W - VESTIBULE * 2, 5f), CornerRadius(2.5f))
    drawRect(Color(0xFFE6EAF0), Offset(0f, 146f), Size(W, 96f))
    drawRect(Color(0xFFB9C1CD), Offset(0f, 240f), Size(W, 60f))
    drawRect(cls.seat.copy(alpha = 0.35f), Offset(VESTIBULE, 262f), Size(W - VESTIBULE * 2, 16f))

    for (i in 0 until cls.seats) {
        val cx = seatX(cls, i)
        drawRoundRect(Color(0xFFD5DBE4), Offset(cx - winW / 2 - 3f, 49f), Size(winW + 6f, 78f), CornerRadius(16f), style = Stroke(6f))
    }
    listOf(VESTIBULE, W - VESTIBULE).forEachIndexed { k, x ->
        drawRect(Color(0xFFDFE4EC), Offset(x - 5f, 20f), Size(10f, 222f))
        val doorX = if (k == 1) x + 18f else x - 60f
        drawRoundRect(Color(0xFFCFD6E0), Offset(doorX, 54f), Size(42f, 186f), CornerRadius(6f))
        val glassX = if (k == 1) x + 26f else x - 52f
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(glassX, 64f), Size(26f, 60f), CornerRadius(5f))
    }
    for (i in 0 until cls.seats) drawSeat(cls, seatX(cls, i))
    if (cls.tables) {
        for (i in 0 until cls.seats) {
            val x = seatX(cls, i) + cls.spacing / 2 - 16f
            drawRoundRect(Color(0xFFD9DEE7), Offset(x, 196f), Size(32f, 5f), CornerRadius(2.5f))
            drawRect(Color(0xFF9AA4B4), Offset(x + 14f, 201f), Size(4f, 38f))
            drawRoundRect(Color(0xFF9AA4B4), Offset(x + 7f, 238f), Size(18f, 4f), CornerRadius(2f))
        }
    }
    if (cls.lamps) {
        for (i in 0 until cls.seats) {
            val x = seatX(cls, i) + cls.spacing / 2 - 5f
            drawCircle(Color(0xFFFFF4D6), 5f, Offset(x, 58f))
        }
    }
}

private fun DrawScope.drawSeat(cls: CarClass, x: Float) {
    val w = if (cls.key == "FIRST") 66f else if (cls.key == "BUSINESS") 60f else 54f
    drawRoundRect(cls.seat, Offset(x - w / 2, 118f), Size(w, 104f), CornerRadius(14f))
    drawRoundRect(cls.headrest, Offset(x - w / 2 + 6f, 122f), Size(w - 12f, 18f), CornerRadius(8f))
    drawRoundRect(cls.seatDark, Offset(x - w / 2 - 4f, 190f), Size(10f, 36f), CornerRadius(5f))
    drawRoundRect(cls.seatDark, Offset(x + w / 2 - 6f, 190f), Size(10f, 36f), CornerRadius(5f))
    drawRoundRect(cls.seatDark, Offset(x - w / 2 + 2f, 206f), Size(w - 4f, 20f), CornerRadius(8f))
    drawRect(Color(0xFF8791A3), Offset(x - 3f, 226f), Size(6f, 14f))
}
