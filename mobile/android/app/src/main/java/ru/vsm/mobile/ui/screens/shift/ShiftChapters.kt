package ru.vsm.mobile.ui.screens.shift

import android.content.Context
import ru.vsm.mobile.domain.model.CarClass

/**
 * Режимы смены:
 *  - «Сюжет» — последовательные главы карьеры проводника: от первого рейса стажёром до вагона
 *    первого класса. Глава открывается после того, как предыдущая пройдена с допуском.
 *  - «Свободная смена» — вагон и самочувствие выбирает игрок, остальное — по умолчаниям тренажёра.
 *  - «Случайный рейс» — всё решает случай: вагон, самочувствие, число и тип ситуаций, темп;
 *    перед рейсом — короткая история и инструкция.
 */

enum class ShiftMode { STORY, FREE, RANDOM }

data class ShiftBrief(val story: String, val tasks: List<String>, val goal: String, val paceLabel: String? = null)

/** План смены — всё, что влияет на ход рейса. */
data class ShiftPlan(
    val carClass: CarClass,
    val condition: MedCondition?, // null = "случайно" (решает [rollCondition])
    val medRate: Double = 0.25,
    val stressCount: Int = 2,
    val backendCount: Int = 2,
    val tripSeconds: Int = 140,
    val patience: Float = 1f,
    val notice: Boolean? = null,
    val forcedStressId: String? = null,
    val brief: ShiftBrief? = null,
)

data class Chapter(val id: String, val title: String, val subtitle: String, val plan: ShiftPlan)

val CHAPTERS: List<Chapter> = listOf(
    Chapter(
        "c1", "Первый рейс", "Стажировка под присмотром наставника",
        ShiftPlan(
            carClass = CarClass.STANDARD, condition = MedCondition.FIT, stressCount = 0, backendCount = 2,
            tripSeconds = 150, patience = 1.4f, notice = false,
            brief = ShiftBrief(
                "Сегодня ваш первый самостоятельный рейс после стажировки. Начальник поезда Олег Викторович обещал заглядывать в вагон, но отвечать пассажирам будете вы.",
                listOf("Пройдите медосмотр и инструктаж", "Примите вагон — найдите хотя бы половину неисправностей", "Подойдите ко всем пассажирам, которые позовут"),
                "Получить допуск к самостоятельной работе",
            ),
        ),
    ),
    Chapter(
        "c2", "Час пик", "Полный вагон и первый конфликт",
        ShiftPlan(
            carClass = CarClass.STANDARD, condition = null, medRate = 0.15, stressCount = 1, backendCount = 2,
            tripSeconds = 140, patience = 1.2f, notice = true,
            brief = ShiftBrief(
                "Пятница, вечерний рейс, вагон забит полностью. На перроне уже спорят из-за мест, а у вас в наряде — особое указание.",
                listOf("Выслушайте особое указание на инструктаже", "Держите темп: пассажиры ждут недолго", "В конфликте — только слова и доклад, никакой силы"),
                "Смена без пропущенных вызовов",
            ),
        ),
    ),
    Chapter(
        "c3", "Вагон «Комфорт»", "Выше ожидания — выше цена ошибки",
        ShiftPlan(
            carClass = CarClass.COMFORT, condition = null, medRate = 0.25, stressCount = 2, backendCount = 2,
            tripSeconds = 140, patience = 1f, notice = true,
            brief = ShiftBrief(
                "Вас перевели в «Комфорт». Пассажиры здесь спокойнее, но замечают каждую мелочь, а начальник поезда попросил проверить вагон особенно тщательно.",
                listOf("Честно ответьте на медосмотре", "Найдите все неисправности на приёмке", "Отработайте две сложные ситуации в пути"),
                "Допуск и ни одной критической ошибки",
            ),
        ),
    ),
    Chapter(
        "c4", "Ночной экспресс", "Срочные вызовы и работа с нарядом полиции",
        ShiftPlan(
            carClass = CarClass.COMFORT, condition = null, medRate = 0.3, stressCount = 2, backendCount = 2,
            tripSeconds = 130, patience = 0.9f, notice = true, forcedStressId = "drunk-rowdy",
            brief = ShiftBrief(
                "Поздний рейс после футбольного матча. В составе едет наряд транспортной полиции, охрана поезда на связи. Ночью силы на исходе — и у вас, и у пассажиров.",
                listOf("Помните: удалить пассажира из поезда может только полиция", "Докладывайте начальнику поезда сразу", "Защитите соседей по вагону"),
                "Все инциденты — по регламенту",
            ),
        ),
    ),
    Chapter(
        "c5", "Бизнес-класс", "Сервис без права на ошибку",
        ShiftPlan(
            carClass = CarClass.BUSINESS, condition = null, medRate = 0.3, stressCount = 2, backendCount = 3,
            tripSeconds = 130, patience = 0.85f, notice = true,
            brief = ShiftBrief(
                "Бизнес-класс: деловые пассажиры, столики, звонки на ходу. Любая заминка превращается в жалобу, а срочные ситуации никто не отменял.",
                listOf("Реагируйте быстро — пассажиры ждут недолго", "Сочетайте вежливость и безопасность", "Не забывайте про приёмку: столики и розетки"),
                "Допуск и рекомендация к переводу",
            ),
        ),
    ),
    Chapter(
        "c6", "Первый класс", "Финальная глава карьеры",
        ShiftPlan(
            carClass = CarClass.FIRST, condition = null, medRate = 0.35, stressCount = 3, backendCount = 2,
            tripSeconds = 130, patience = 0.8f, notice = true,
            brief = ShiftBrief(
                "Вам доверили вагон первого класса. Шесть кресел, каждый пассажир на виду. Сегодня в пути случится больше обычного — покажите всё, чему научились.",
                listOf("Три сложные ситуации и обычные вызовы", "Ни одного пропущенного пассажира", "Безупречный заступ и приёмка"),
                "Подтвердить квалификацию проводника первого класса",
            ),
        ),
    ),
)

data class ChapterState(val passed: Boolean = false, val best: Int = 0, val attempts: Int = 0)

private const val PREFS = "shift_story"

/** Прогресс карьеры (какие главы пройдены) — хранится на устройстве, переживает перезапуск. */
fun readStory(context: Context): Map<String, ChapterState> {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return CHAPTERS.associate { c ->
        c.id to ChapterState(
            passed = prefs.getBoolean("${c.id}.passed", false),
            best = prefs.getInt("${c.id}.best", 0),
            attempts = prefs.getInt("${c.id}.attempts", 0),
        )
    }
}

fun recordChapter(context: Context, id: String, passed: Boolean, score: Int) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val prevPassed = prefs.getBoolean("$id.passed", false)
    val prevBest = prefs.getInt("$id.best", 0)
    val attempts = prefs.getInt("$id.attempts", 0)
    prefs.edit()
        .putBoolean("$id.passed", prevPassed || passed)
        .putInt("$id.best", maxOf(prevBest, score))
        .putInt("$id.attempts", attempts + 1)
        .apply()
}

/** Открыта ли глава: первая — всегда, остальные — после допуска в предыдущей. */
fun chapterUnlocked(story: Map<String, ChapterState>, index: Int): Boolean =
    index == 0 || story[CHAPTERS[index - 1].id]?.passed == true

// ---------------------------------------------------------------------------
// Случайный рейс: план и история генерируются из seed
// ---------------------------------------------------------------------------

private val CHIEF_NAMES = listOf("Олег Викторович", "Марина Андреевна", "Сергей Павлович", "Елена Игоревна", "Андрей Николаевич")
private val WEATHER = listOf("метель и задержки на подходе к Твери", "жара за тридцать, кондиционеры на пределе", "ясное утро и полный вагон отпускников", "дождь, мокрые зонты и скользкий тамбур", "праздничные выходные — много семей с детьми")
private val TIMES = listOf("ранний утренний рейс", "дневной рейс", "вечерний рейс в пятницу", "поздний рейс после концерта")

private data class Pace(val label: String, val tripSeconds: Int, val patience: Float)
private val PACES = listOf(Pace("Спокойный темп", 160, 1.3f), Pace("Обычный темп", 140, 1f), Pace("Напряжённый темп", 120, 0.8f))

private val CAR_TITLES = mapOf(
    CarClass.STANDARD to ("Стандарт" to 5),
    CarClass.COMFORT to ("Комфорт" to 4),
    CarClass.BUSINESS to ("Бизнес" to 2),
    CarClass.FIRST to ("Первый" to 1),
)

fun randomPlan(rand: ShiftRng): ShiftPlan {
    val cls = rand.pick(CLASS_ORDER)
    val pace = rand.pick(PACES)
    val stressCount = rand.nextInt(4)
    val backendCount = maxOf(1, 3 - stressCount / 2 - rand.nextInt(2))
    val chief = rand.pick(CHIEF_NAMES)
    val weather = rand.pick(WEATHER)
    val whenText = rand.pick(TIMES)
    val (title, car) = CAR_TITLES.getValue(cls)
    val tasks = listOf(
        "Пройдите медосмотр — отвечайте честно, допуск решает медработник",
        "Примите вагон $car «$title»: найдите неисправности до посадки",
        if (stressCount == 0) "В пути — обычные вызовы пассажиров" else "В пути — $stressCount ${if (stressCount == 1) "сложная ситуация" else "сложные ситуации"} и обычные вызовы",
        "О любом нарушении порядка — доклад начальнику поезда",
    )
    return ShiftPlan(
        carClass = cls,
        condition = null,
        medRate = 0.1 + rand.next() * 0.3,
        stressCount = stressCount,
        backendCount = backendCount,
        tripSeconds = pace.tripSeconds,
        patience = pace.patience,
        notice = rand.next() < 0.8,
        brief = ShiftBrief(
            "${whenText.replaceFirstChar { it.uppercase() }}, $weather. Начальник поезда сегодня — $chief. Вам достался вагон $car, класс «$title». Что случится в пути, не знает никто.",
            tasks,
            "Отработать смену так, чтобы получить допуск",
            paceLabel = pace.label,
        ),
    )
}
