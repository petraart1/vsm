/**
 * Модель смены проводника: вагоны по классам, заступ (медкомиссия), приёмка вагона, генератор
 * инцидентов рейса и итоговая оценка.
 *
 * Диалоги с пассажирами — это сценарии backend (/api/scenarios, граф с шкалами и таймерами):
 * смена лишь решает, КАКОЙ сценарий, У КАКОГО пассажира и КОГДА случится, не объявляя его заранее.
 * Заступ и приёмка — короткий локальный контент; его можно перенести в seed-сценарии backend
 * тем же форматом без изменений экрана.
 */

export const CAR_CLASSES = {
  STANDARD: {
    key: "STANDARD", title: "Стандарт", car: 5, note: "Больше всего пассажиров, 2+2 в ряду",
    seats: 12, spacing: 86, seat: "#6f7f9c", seatDark: "#566582", headrest: "#e7ebf2", occupancy: 0.8
  },
  COMFORT: {
    key: "COMFORT", title: "Комфорт", car: 4, note: "Больше места, выше ожидания",
    seats: 10, spacing: 96, seat: "#2f5ca8", seatDark: "#244a8a", headrest: "#e7ebf2", occupancy: 0.75
  },
  BUSINESS: {
    key: "BUSINESS", title: "Бизнес", car: 2, note: "2+1, столики, пассажир ждёт внимания",
    seats: 8, spacing: 120, seat: "#1c2d52", seatDark: "#142240", headrest: "#d7deea", occupancy: 0.7, tables: true
  },
  FIRST: {
    key: "FIRST", title: "Первый", car: 1, note: "Кресла 1+1, сервис без права на ошибку",
    seats: 6, spacing: 152, seat: "#c6cfdd", seatDark: "#9fabbe", headrest: "#f4f6fa", occupancy: 0.67, tables: true, lamps: true
  }
};

export const CLASS_ORDER = ["STANDARD", "COMFORT", "BUSINESS", "FIRST"];

export const STATIONS = [
  { at: 0, name: "Москва" },
  { at: 0.36, name: "Тверь" },
  { at: 0.7, name: "Великий Новгород" },
  { at: 1, name: "Санкт-Петербург" }
];

export const VESTIBULE = 150;

export function worldWidth(cls) {
  return VESTIBULE * 2 + cls.seats * cls.spacing;
}

export function seatX(cls, i) {
  return VESTIBULE + cls.spacing * (i + 0.5);
}

// ---------------------------------------------------------------------------
// Детерминированный генератор случайных чисел: одна смена = один seed.
// ---------------------------------------------------------------------------

export function rng(seed) {
  let s = seed % 2147483647;
  if (s <= 0) s += 2147483646;
  return () => {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

/** Пассажиры: какие места заняты и кто сидит. */
export function seatPassengers(cls, rand) {
  const list = [];
  for (let i = 0; i < cls.seats; i += 1) {
    if (rand() < cls.occupancy) {
      list.push({ seat: i, variant: Math.floor(rand() * 40), kid: rand() < 0.08, phone: rand() < 0.3 });
    }
  }
  return list;
}

// ---------------------------------------------------------------------------
// Заступ на смену: медкомиссия и допуск. Правильный ответ — честный и по регламенту.
// ---------------------------------------------------------------------------

export function medCheckScript(cls) {
  return [
    {
      id: "health",
      speaker: "medic",
      context: "Предрейсовый медосмотр. Утром дома термометр показал 37,4.",
      text: "Доброе утро. Как самочувствие, жалобы есть?",
      choices: [
        { id: "a", text: "Жалоб нет, всё хорошо.", safety: -12, loyalty: 0, reply: "Хорошо, давление в норме. Допускаю.", note: "Скрытая температура — риск для пассажиров и бригады. Медработник решает о допуске только по честным данным." },
        { id: "b", text: "Утром была температура 37,4. Сообщаю, решать вам.", safety: 10, loyalty: 0, reply: "Спасибо, что сказали. Сейчас 36,9 — допускаю, но возьмите маску и дайте знать, если станет хуже.", note: "Честно сообщить о симптомах — единственный правильный вариант: решение о допуске принимает медработник." },
        { id: "c", text: "Немного знобит, но бригаду подводить не хочу.", safety: -4, loyalty: 0, reply: "Давайте всё-таки измерим. 36,9 — допускаю, но следите за состоянием.", note: "Сказать о симптомах — правильно, но оценивать, «подводит» ли это бригаду, не вам." }
      ]
    },
    {
      id: "assignment",
      speaker: "chief",
      context: "Инструктаж у начальника поезда.",
      text: "Напомните, где вы сегодня работаете?",
      choices: CLASS_ORDER.slice(0, 3).map((k, i) => {
        const c = CAR_CLASSES[k];
        const right = k === cls.key;
        return { id: `k${i}`, text: `Вагон ${c.car}, класс «${c.title}».`, safety: right ? 4 : -6, loyalty: 0, reply: right ? "Верно. Удачного рейса." : `Нет — вагон ${cls.car}, «${cls.title}». Сверяйтесь с нарядом.`, note: "Проводник знает свой вагон и класс до выхода на перрон." };
      }).concat(cls.key === "FIRST" ? [{ id: "k3", text: `Вагон ${cls.car}, класс «${cls.title}».`, safety: 4, loyalty: 0, reply: "Верно. Удачного рейса.", note: "Проводник знает свой вагон и класс до выхода на перрон." }] : [])
    },
    {
      id: "uniform",
      speaker: "chief",
      context: "Проверка формы и документов. Именной бейдж остался в шкафчике.",
      text: "Форма в порядке? Бейдж на месте?",
      choices: [
        { id: "a", text: "Бейдж забыл в шкафчике — сейчас схожу.", safety: 2, loyalty: 4, reply: "Давайте быстро, до посадки десять минут.", note: "Пассажир должен видеть, как обращаться к проводнику: бейдж — часть формы." },
        { id: "b", text: "Всё в порядке.", safety: -2, loyalty: -6, reply: "Хорошо.", note: "Работа без бейджа — нарушение требований к внешнему виду; пассажиру сложнее обратиться и написать отзыв." }
      ]
    }
  ];
}

// ---------------------------------------------------------------------------
// Приёмка вагона: 6 точек осмотра, из них 3 — с неисправностью. Пропущенная неисправность
// «всплывает» в рейсе инцидентом (для розетки и столика есть готовые сценарии 16 и 27).
// ---------------------------------------------------------------------------

export const INSPECTION_POINTS = [
  { key: "extinguisher", title: "Огнетушитель", ok: "Огнетушитель на месте, пломба цела.", fault: "Крепление пустое: огнетушителя нет.", safety: 12, situationId: null },
  { key: "firstaid", title: "Аптечка", ok: "Аптечка укомплектована.", fault: "В аптечке нет бинтов и антисептика.", safety: 8, situationId: null },
  { key: "hammer", title: "Аварийный молоток", ok: "Молоток на месте у окна.", fault: "Аварийного молотка нет у окна.", safety: 10, situationId: null },
  { key: "callbtn", title: "Кнопка вызова", ok: "Кнопка вызова работает.", fault: "Кнопка вызова не загорается.", safety: 6, situationId: 16 },
  { key: "socket", title: "Розетка", ok: "Розетка работает.", fault: "Розетка у кресла не даёт питания.", safety: 4, situationId: 16 },
  { key: "table", title: "Столик", ok: "Столик чистый.", fault: "Столик после прошлого рейса не убран.", safety: 3, situationId: 27 }
];

export function planInspection(cls, rand) {
  const faults = new Set();
  while (faults.size < 3) faults.add(Math.floor(rand() * INSPECTION_POINTS.length));
  const W = worldWidth(cls);
  const midSeat = (i) => seatX(cls, Math.min(cls.seats - 1, i));
  const positions = {
    extinguisher: 62,
    firstaid: W - 70,
    hammer: midSeat(Math.floor(cls.seats * 0.5)) - cls.spacing * 0.35,
    callbtn: midSeat(Math.floor(cls.seats * 0.25)),
    socket: midSeat(Math.floor(cls.seats * 0.7)),
    table: midSeat(Math.floor(cls.seats * 0.85))
  };
  return INSPECTION_POINTS.map((p, i) => ({ ...p, x: positions[p.key], faulty: faults.has(i), checked: false }));
}

// ---------------------------------------------------------------------------
// Инциденты рейса
// ---------------------------------------------------------------------------

const URGENT_BLOCKS = new Set(["medical", "safety"]);

/**
 * Выбирает 3 сценария из каталога: один флагманский (глубокое ветвление) и два из других блоков.
 * Название сценария игроку не показывается — только сигнал у пассажира.
 */
export function planIncidents(situations, passengers, rand, extraSituationIds = []) {
  const pool = situations.filter((s) => s.id != null);
  if (!pool.length || !passengers.length) return [];
  const pick = (list) => list[Math.floor(rand() * list.length)];
  const chosen = [];
  const flagships = pool.filter((s) => s.flagship);
  if (flagships.length) chosen.push(pick(flagships));
  let guard = 0;
  while (chosen.length < 3 && guard < 200) {
    guard += 1;
    const s = pick(pool);
    if (!chosen.some((c) => c.id === s.id || c.block === s.block)) chosen.push(s);
  }
  extraSituationIds.forEach((ref) => {
    const s = pool.find((x) => String(x.situationRef ?? x.id) === String(ref) || String(x.id) === String(ref));
    if (s && !chosen.some((c) => c.id === s.id)) chosen.push(s);
  });

  const seats = passengers.slice().sort(() => rand() - 0.5);
  const spawnAt = [0.1, 0.34, 0.58, 0.8, 0.9];
  return chosen.map((s, i) => ({
    key: `inc-${i}`,
    scenarioId: s.id,
    title: s.title,
    block: s.block,
    blockLabel: s.blockLabel,
    urgent: URGENT_BLOCKS.has(s.block),
    seat: seats[i % seats.length].seat,
    spawnAt: spawnAt[i] ?? 0.9,
    fromInspection: i >= 3,
    status: "pending", // pending → active → done | missed
    remaining: URGENT_BLOCKS.has(s.block) ? 35 : 50,
    result: null
  }));
}

// ---------------------------------------------------------------------------
// Итог смены
// ---------------------------------------------------------------------------

export const MISSED_PENALTY = 15;
const CRITICAL = "Критическая ошибка безопасности";

export function summarize({ cls, med, inspection, incidents }) {
  const medSafety = med.reduce((a, m) => a + m.safety, 0);
  const medLoyalty = med.reduce((a, m) => a + m.loyalty, 0);
  const faults = inspection.filter((p) => p.faulty);
  const found = faults.filter((p) => p.checked);
  const inspectionSafety = found.reduce((a, p) => a + p.safety, 0) - (faults.length - found.length) * 6;
  const done = incidents.filter((i) => i.status === "done");
  const missed = incidents.filter((i) => i.status === "missed");
  const incSafety = done.reduce((a, i) => a + (i.result?.safety ?? 0), 0) - missed.length * MISSED_PENALTY;
  const incLoyalty = done.reduce((a, i) => a + (i.result?.loyalty ?? 0), 0) - missed.length * MISSED_PENALTY;
  const critical = done.some((i) => i.result?.verdict === CRITICAL);
  const honest = med.find((m) => m.step === "health")?.choice === "b";

  const safety = medSafety + inspectionSafety + incSafety;
  const loyalty = medLoyalty + incLoyalty;
  // Допуск: без критических ошибок и пропущенных вызовов, честный медосмотр, приёмка хотя бы наполовину.
  const admitted = !critical && missed.length === 0 && safety >= 0 && honest && found.length * 2 >= faults.length;
  const idx = CLASS_ORDER.indexOf(cls.key);
  const upgrade = admitted && honest && found.length === faults.length && done.every((i) => i.result?.verdict === "Хорошо справились") && idx < CLASS_ORDER.length - 1
    ? CAR_CLASSES[CLASS_ORDER[idx + 1]]
    : null;

  return {
    safety,
    loyalty,
    admitted,
    upgrade,
    critical,
    honest,
    med: { correct: med.filter((m) => m.best).length, total: med.length },
    inspection: { found: found.length, faults: faults.length, missed: faults.filter((p) => !p.checked) },
    incidents: { done: done.length, missed: missed.length, total: incidents.length }
  };
}

const SHIFTS_KEY = "reactlab.shifts.v1";

export function saveShift(entry) {
  try {
    const list = JSON.parse(localStorage.getItem(SHIFTS_KEY) || "[]");
    list.unshift({ ...entry, at: new Date().toISOString() });
    localStorage.setItem(SHIFTS_KEY, JSON.stringify(list.slice(0, 100)));
  } catch (e) { /* приватный режим */ }
}

export function readShifts() {
  try { return JSON.parse(localStorage.getItem(SHIFTS_KEY) || "[]"); } catch (e) { return []; }
}

/**
 * Три кольца смены (0..1):
 *  - «Регламент» — верные ответы на заступе + найденные на приёмке неисправности;
 *  - «Реакция» — на сколько вызовов пассажиров проводник успел подойти;
 *  - «Качество» — как отработаны диалоги (хорошо = 1, есть над чем поработать = 0.5, критично = 0).
 */
export function ringsFor(summary, incidents) {
  const procTotal = summary.med.total + summary.inspection.faults;
  const procedure = procTotal ? (summary.med.correct + summary.inspection.found) / procTotal : 0;
  const reaction = summary.incidents.total ? summary.incidents.done / summary.incidents.total : 0;
  const q = incidents.map((i) => {
    if (i.status !== "done") return 0;
    const v = i.result && i.result.verdict;
    if (v === "Хорошо справились") return 1;
    if (v === "Критическая ошибка безопасности") return 0;
    return 0.5;
  });
  const quality = q.length ? q.reduce((a, b) => a + b, 0) / q.length : 0;
  return { procedure, reaction, quality };
}
