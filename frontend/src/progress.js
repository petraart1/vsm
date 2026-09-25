/**
 * Модель прогресса проводника на клиенте: журнал прохождений (для графика активности),
 * разряды по очкам компетенций и квалификации по учебным модулям (блокам ситуаций).
 *
 * Журнал хранится в localStorage и пополняется из api.getDebrief — backend пока не отдаёт
 * историю прохождений по дням, поэтому график активности локален для браузера.
 * Всё остальное (разряд, квалификации) вычисляется из данных backend: профиля и списка сценариев.
 */

const ACTIVITY_KEY = "vsm.activityLog.v1";
const LEGACY_PROGRESS_KEY = "vsm.completedScenarios.v2";
const ACTIVITY_LIMIT = 1000;

// =======================================================================
// Журнал прохождений
// =======================================================================

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch (e) {
    return fallback;
  }
}

function writeJson(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* приватный режим */ }
}

/**
 * Записывает завершённое прохождение. Идемпотентно по progressId: повторное открытие
 * разбора того же прохождения не создаёт дубликат в журнале.
 */
export function recordActivity(entry) {
  const log = readJson(ACTIVITY_KEY, []);
  if (entry.progressId && log.some((e) => e.progressId === entry.progressId)) return;
  log.unshift({ ...entry, at: entry.at || new Date().toISOString() });
  writeJson(ACTIVITY_KEY, log.slice(0, ACTIVITY_LIMIT));
}

/**
 * Журнал, отсортированный от новых к старым. Прохождения, записанные до появления журнала
 * (только в кэше «последний результат по сценарию»), подмешиваются, чтобы график
 * у существующих пользователей не был пустым.
 */
export function readActivity() {
  const log = readJson(ACTIVITY_KEY, []);
  const legacy = readJson(LEGACY_PROGRESS_KEY, {});
  const known = new Set(log.map((e) => `${e.scenarioId}|${e.at}`));
  Object.keys(legacy).forEach((scenarioId) => {
    const p = legacy[scenarioId];
    if (!p || !p.completedAt) return;
    const key = `${scenarioId}|${p.completedAt}`;
    const sameScenarioLogged = log.some((e) => String(e.scenarioId) === scenarioId);
    if (known.has(key) || sameScenarioLogged) return;
    log.push({
      progressId: null,
      scenarioId,
      title: null,
      blockLabel: null,
      loyalty: p.loyalty,
      safety: p.safety,
      verdict: p.verdict,
      at: p.completedAt
    });
  });
  return log.sort((a, b) => (a.at < b.at ? 1 : -1));
}

// =======================================================================
// Календарь: даты в локальной зоне пользователя
// =======================================================================

export function dayKey(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function startOfDay(date) {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Понедельник недели, в которую попадает дата. */
function startOfWeek(date) {
  const d = startOfDay(date);
  const shift = (d.getDay() + 6) % 7; // 0 = понедельник
  d.setDate(d.getDate() - shift);
  return d;
}

export function countByDay(activity) {
  const map = {};
  activity.forEach((e) => {
    const key = dayKey(new Date(e.at));
    map[key] = (map[key] || 0) + 1;
  });
  return map;
}

/**
 * Сетка для графика активности: weeks колонок по 7 дней (понедельник сверху),
 * последняя колонка — текущая неделя. Дни после сегодняшнего помечены future.
 */
export function buildCalendar(activity, weeks = 26, today = new Date()) {
  const counts = countByDay(activity);
  const end = startOfDay(today);
  const first = startOfWeek(end);
  first.setDate(first.getDate() - (weeks - 1) * 7);

  const columns = [];
  const months = [];
  let lastMonth = -1;
  for (let w = 0; w < weeks; w += 1) {
    const days = [];
    for (let d = 0; d < 7; d += 1) {
      const date = new Date(first);
      date.setDate(first.getDate() + w * 7 + d);
      const key = dayKey(date);
      days.push({ key, date, count: counts[key] || 0, future: date > end });
    }
    const monthOfColumn = days[0].date.getMonth();
    if (monthOfColumn !== lastMonth) {
      // Подпись неполного месяца у левого края не должна наезжать на следующую.
      if (months.length && w - months[months.length - 1].column < 3) months.pop();
      months.push({ column: w, label: days[0].date.toLocaleDateString("ru-RU", { month: "short" }).replace(".", "") });
      lastMonth = monthOfColumn;
    }
    columns.push(days);
  }
  return { columns, months };
}

/** Уровень насыщенности клетки 0-4. Пороги фиксированные: 1 / 2 / 3-4 / 5+ сценариев в день. */
export function intensityLevel(count) {
  if (count <= 0) return 0;
  if (count === 1) return 1;
  if (count === 2) return 2;
  if (count <= 4) return 3;
  return 4;
}

/** Серия — сколько дней подряд были тренировки, считая от сегодня (или от вчера, если сегодня ещё не было). */
export function currentStreak(activity, today = new Date()) {
  const counts = countByDay(activity);
  const cursor = startOfDay(today);
  if (!counts[dayKey(cursor)]) cursor.setDate(cursor.getDate() - 1);
  let streak = 0;
  while (counts[dayKey(cursor)]) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

export function longestStreak(activity) {
  const days = Object.keys(countByDay(activity)).sort();
  let best = 0;
  let run = 0;
  let prev = null;
  days.forEach((key) => {
    const date = new Date(`${key}T00:00:00`);
    if (prev) {
      const next = new Date(prev);
      next.setDate(next.getDate() + 1);
      run = dayKey(next) === key ? run + 1 : 1;
    } else {
      run = 1;
    }
    best = Math.max(best, run);
    prev = date;
  });
  return best;
}

/** Сводка «эта неделя против прошлой» — как недельный отчёт в беговых приложениях. */
export function weekSummary(activity, today = new Date()) {
  const thisWeek = startOfWeek(today);
  const lastWeek = new Date(thisWeek);
  lastWeek.setDate(lastWeek.getDate() - 7);
  let current = 0;
  let previous = 0;
  const activeDays = new Set();
  activity.forEach((e) => {
    const at = new Date(e.at);
    if (at >= thisWeek) {
      current += 1;
      activeDays.add(dayKey(at));
    } else if (at >= lastWeek) {
      previous += 1;
    }
  });
  return { current, previous, activeDays: activeDays.size };
}

/** Средние шкалы по последним n прохождениям (null, если прохождений нет). */
export function recentAverages(activity, n = 10) {
  const recent = activity.filter((e) => typeof e.safety === "number").slice(0, n);
  if (recent.length === 0) return null;
  const sum = recent.reduce((acc, e) => ({ loyalty: acc.loyalty + e.loyalty, safety: acc.safety + e.safety }), { loyalty: 0, safety: 0 });
  return { loyalty: sum.loyalty / recent.length, safety: sum.safety / recent.length, sample: recent.length };
}

// =======================================================================
// Разряды проводника по очкам компетенций
// =======================================================================

/**
 * За одно прохождение backend начисляет ~20-160 очков (см. формулу в GamificationAccrualService),
 * поэтому пороги рассчитаны так, чтобы старший разряд требовал уверенного прохождения
 * почти всего каталога из 51 ситуации, а не случайных повторов.
 */
export const GRADES = [
  { min: 0, title: "Стажёр", note: "Знакомство с регламентом и ролевой моделью ответа" },
  { min: 300, title: "Проводник 3 класса", note: "Самостоятельная работа в штатных ситуациях" },
  { min: 1000, title: "Проводник 2 класса", note: "Уверенная работа с конфликтами и эскалацией" },
  { min: 2500, title: "Проводник 1 класса", note: "Действия в экстренных ситуациях без ошибок" },
  { min: 4500, title: "Старший проводник", note: "Координация бригады вагона" },
  { min: 7000, title: "Проводник-наставник", note: "Подготовка стажёров" }
];

export function gradeFor(totalScore) {
  let index = 0;
  GRADES.forEach((g, i) => { if (totalScore >= g.min) index = i; });
  const current = GRADES[index];
  const next = GRADES[index + 1] || null;
  const progress = next ? (totalScore - current.min) / (next.min - current.min) : 1;
  return { index, current, next, progress, pointsToNext: next ? next.min - totalScore : 0 };
}

// =======================================================================
// Квалификации по учебным модулям
// =======================================================================

/** Учебные модули = блоки ситуаций датасета, в формулировках программы повышения квалификации. */
export const MODULES = {
  boarding: { code: "01", title: "Посадка и контроль проездных документов", hours: 4 },
  baggage: { code: "02", title: "Перевозка багажа, ручной клади и животных", hours: 3 },
  safety: { code: "03", title: "Обеспечение общественного порядка на борту", hours: 6 },
  seating: { code: "04", title: "Размещение пассажиров и смена класса обслуживания", hours: 3 },
  catering: { code: "05", title: "Сервис питания и платные услуги", hours: 4 },
  medical: { code: "06", title: "Действия при медицинских и экстренных ситуациях", hours: 8 },
  lost_found: { code: "07", title: "Работа с находками, утерями и обращениями", hours: 3 },
  conflict: { code: "08", title: "Урегулирование конфликтных ситуаций в поезде", hours: 6 },
  comfort: { code: "09", title: "Комфорт пассажиров и бытовые обращения", hours: 4 },
  misc: { code: "10", title: "Нестандартные запросы пассажиров", hours: 3 }
};

const MODULE_ORDER = Object.keys(MODULES);

/** Иконка для каждого блока ситуаций (имена из components/ui/Icon.jsx). */
export const BLOCK_ICON = {
  boarding: "ticket",
  baggage: "luggage",
  safety: "shield",
  seating: "armchair",
  catering: "coffee",
  medical: "cross",
  lost_found: "package",
  conflict: "users",
  comfort: "thermometer",
  misc: "help"
};

/**
 * Требования для присвоения квалификации. Шкалы в сценариях считаются от 0 как сумма
 * эффектов решений, «высоким» backend считает итог от 25 (ачивки FLAWLESS_SAFETY /
 * PASSENGER_FAVORITE) — для допуска по модулю достаточно стабильно положительного среднего.
 */
export const QUALIFICATION_RULES = {
  minAvgSafety: 10,
  minAvgLoyalty: 5
};

/** Среднее с одним знаком после запятой: округление до целого показало бы «сейчас 10» при 9,5 < 10. */
function formatAvg(n) {
  return Number.isInteger(n) ? String(n) : n.toFixed(1).replace(".", ",");
}

const FAILURE_VERDICT = "Критическая ошибка безопасности";

/**
 * Квалификации по списку сценариев из api.listScenarios(). Для каждого модуля:
 * status — certified | in_training | not_started, requirements — чек-лист с выполнением.
 */
export function buildQualifications(scenarioList) {
  const byBlock = {};
  (scenarioList.situations || []).forEach((s) => {
    if (!byBlock[s.block]) byBlock[s.block] = { label: s.blockLabel, scenarios: [] };
    byBlock[s.block].scenarios.push(s);
  });

  const blocks = MODULE_ORDER.filter((b) => byBlock[b]).concat(Object.keys(byBlock).filter((b) => !MODULES[b]));

  return blocks.map((block) => {
    const meta = MODULES[block] || { code: "--", title: byBlock[block].label, hours: 2 };
    const scenarios = byBlock[block].scenarios;
    const done = scenarios.filter((s) => s.status === "completed" && s.lastResult);
    const avgSafety = done.length ? done.reduce((a, s) => a + s.lastResult.safety, 0) / done.length : null;
    const avgLoyalty = done.length ? done.reduce((a, s) => a + s.lastResult.loyalty, 0) / done.length : null;
    const failures = done.filter((s) => s.lastResult.verdict === FAILURE_VERDICT);

    const requirements = [
      {
        key: "coverage",
        label: `Пройдены все ситуации модуля`,
        detail: `${done.length} из ${scenarios.length}`,
        met: done.length === scenarios.length
      },
      {
        key: "no-critical",
        label: "Нет критических ошибок безопасности",
        detail: failures.length ? `ошибок: ${failures.length}` : done.length ? "ошибок нет" : "нет данных",
        met: done.length > 0 && failures.length === 0
      },
      {
        key: "safety",
        label: `Средний рейтинг безопасности не ниже ${QUALIFICATION_RULES.minAvgSafety}`,
        detail: avgSafety === null ? "нет данных" : `сейчас ${formatAvg(avgSafety)}`,
        met: avgSafety !== null && avgSafety >= QUALIFICATION_RULES.minAvgSafety
      },
      {
        key: "loyalty",
        label: `Средняя лояльность пассажиров не ниже ${QUALIFICATION_RULES.minAvgLoyalty}`,
        detail: avgLoyalty === null ? "нет данных" : `сейчас ${formatAvg(avgLoyalty)}`,
        met: avgLoyalty !== null && avgLoyalty >= QUALIFICATION_RULES.minAvgLoyalty
      }
    ];

    const certified = requirements.every((r) => r.met);
    const status = certified ? "certified" : done.length > 0 ? "in_training" : "not_started";
    const certifiedAt = certified
      ? done.map((s) => s.lastResult.completedAt).filter(Boolean).sort().pop() || null
      : null;

    return {
      block,
      code: meta.code,
      title: meta.title,
      hours: meta.hours,
      blockLabel: byBlock[block].label,
      status,
      certifiedAt,
      completed: done.length,
      total: scenarios.length,
      requirements,
      scenarios
    };
  });
}

/** Регистрационный номер свидетельства: модуль + короткий хвост playerId (без персональных данных). */
export function certificateNumber(moduleCode, playerId) {
  const tail = String(playerId || "0000").replace(/-/g, "").slice(-6).toUpperCase();
  return `ВСМ-УЦ-${moduleCode}/${tail}`;
}

export function formatDate(iso, opts) {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("ru-RU", opts || { day: "numeric", month: "long", year: "numeric" });
}

export function pluralRu(n, one, few, many) {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return one;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return few;
  return many;
}

// =======================================================================
// Служебные отличия (ачивки backend) в формулировках учебного центра
// =======================================================================

const DISTINCTIONS = {
  FIRST_SCENARIO: { title: "Вводный инструктаж", mark: "ВИ" },
  FLAWLESS_SAFETY: { title: "Отличие за безопасность", mark: "Б" },
  PASSENGER_FAVORITE: { title: "Отличие за сервис", mark: "С" },
  VERSATILE: { title: "Широкий профиль подготовки", mark: "ШП" },
  VETERAN: { title: "Десять учебных рейсов", mark: "10" }
};

/** AchievementDto -> отличие для витрины: формальное название, монограмма для печати. */
export function toDistinction(a) {
  const meta = DISTINCTIONS[a.code] || { title: a.title, mark: (a.title || "?").slice(0, 2).toUpperCase() };
  return { ...a, title: meta.title, mark: meta.mark };
}
