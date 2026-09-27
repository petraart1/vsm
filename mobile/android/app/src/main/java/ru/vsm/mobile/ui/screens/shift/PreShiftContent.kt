package ru.vsm.mobile.ui.screens.shift

import ru.vsm.mobile.domain.model.CarClass

/** Кто задаёт вопрос на предрейсовом этапе. */
enum class PreShiftSpeaker { MEDIC, CHIEF }

/** Один вариант ответа чек-листа заступа на смену: влияет на локальные итоговые шкалы этапа. */
data class PreShiftChoice(
    val id: String,
    val text: String,
    val safetyDelta: Int,
    val loyaltyDelta: Int,
    val reply: String,
    val note: String,
    val best: Boolean = false,
)

/** Один шаг чек-листа (медосмотр / инструктаж / приёмка) — короткий локальный контент. */
data class PreShiftStep(
    val id: String,
    val speaker: PreShiftSpeaker,
    val context: String,
    val question: String,
    val choices: List<PreShiftChoice>,
)

/**
 * Заступ на смену перед рейсом: медосмотр -> сверка наряда -> готовность к посадке. Правильный
 * ответ везде — честный и по регламенту (см. обучающую обратную связь [PreShiftChoice.note]).
 */
fun buildPreShiftSteps(carClass: CarClass): List<PreShiftStep> {
    val carTitles: Map<CarClass, Pair<String, Int>> = mapOf(
        CarClass.STANDARD to ("Стандарт" to 5),
        CarClass.COMFORT to ("Комфорт" to 4),
        CarClass.BUSINESS to ("Бизнес" to 2),
        CarClass.FIRST to ("Первый" to 1),
    )
    val (ownTitle, ownCar) = carTitles.getValue(carClass)
    val others = carTitles.entries.filter { it.key != carClass }.shuffled().take(2)

    val assignmentChoices = buildList {
        add(
            PreShiftChoice(
                id = "own",
                text = "Вагон $ownCar, класс «$ownTitle».",
                safetyDelta = 4,
                loyaltyDelta = 0,
                reply = "Верно. Удачного рейса.",
                note = "Проводник знает свой вагон и класс до выхода на перрон.",
                best = true,
            )
        )
        others.forEach { (cls, titleCar) ->
            val (title, car) = titleCar
            add(
                PreShiftChoice(
                    id = cls.name,
                    text = "Вагон $car, класс «$title».",
                    safetyDelta = -6,
                    loyaltyDelta = 0,
                    reply = "Нет — вагон $ownCar, «$ownTitle». Сверяйтесь с нарядом.",
                    note = "Проводник знает свой вагон и класс до выхода на перрон.",
                )
            )
        }
    }.shuffled()

    return listOf(
        PreShiftStep(
            id = "health",
            speaker = PreShiftSpeaker.MEDIC,
            context = "Предрейсовый медосмотр. Утром дома термометр показал 37,4.",
            question = "Доброе утро. Как самочувствие, жалобы есть?",
            choices = listOf(
                PreShiftChoice(
                    id = "a", text = "Жалоб нет, всё хорошо.", safetyDelta = -12, loyaltyDelta = 0,
                    reply = "Хорошо, давление в норме. Допускаю.",
                    note = "Скрытая температура — риск для пассажиров и бригады. Решение о допуске медработник принимает только по честным данным.",
                ),
                PreShiftChoice(
                    id = "b", text = "Утром была температура 37,4. Сообщаю, решать вам.", safetyDelta = 10, loyaltyDelta = 0, best = true,
                    reply = "Спасибо, что сказали. Сейчас 36,9 — допускаю, возьмите маску.",
                    note = "Честно сообщить о симптомах — единственно правильный вариант.",
                ),
                PreShiftChoice(
                    id = "c", text = "Немного знобит, но бригаду подводить не хочу.", safetyDelta = -4, loyaltyDelta = 0,
                    reply = "Давайте всё-таки измерим. 36,9 — допускаю, но следите за состоянием.",
                    note = "Сказать о симптомах — правильно, но оценивать допуск должен медработник, не вы сами.",
                ),
            ),
        ),
        PreShiftStep(
            id = "assignment",
            speaker = PreShiftSpeaker.CHIEF,
            context = "Инструктаж у начальника поезда.",
            question = "Напомните, где вы сегодня работаете?",
            choices = assignmentChoices,
        ),
        PreShiftStep(
            id = "readiness",
            speaker = PreShiftSpeaker.CHIEF,
            context = "Проверка формы и документов. Именной бейдж остался в шкафчике.",
            question = "Форма в порядке? Бейдж на месте?",
            choices = listOf(
                PreShiftChoice(
                    id = "a", text = "Бейдж забыл в шкафчике — сейчас схожу.", safetyDelta = 2, loyaltyDelta = 4, best = true,
                    reply = "Давайте быстро, до посадки десять минут.",
                    note = "Пассажир должен видеть, как обращаться к проводнику: бейдж — часть формы.",
                ),
                PreShiftChoice(
                    id = "b", text = "Всё в порядке.", safetyDelta = -2, loyaltyDelta = -6,
                    reply = "Хорошо.",
                    note = "Работа без бейджа — нарушение требований к внешнему виду.",
                ),
            ),
        ),
    )
}
