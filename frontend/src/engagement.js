/**
 * Вовлечённость: ежедневная серия входов, награды за вход и витрина наград.
 *
 * Серия считается по календарным дням (локальное время): заход сегодня продолжает серию, если
 * вчера тоже был заход; пропуск дня обнуляет. Награды за серию — 3, 7, 14 и 30 дней: медали в
 * том же дизайн-коде, что и остальные награды (круг со «вспышкой» и числом дней).
 *
 * Хранение — localStorage (per-device). Витрина дополнительно отправляется на backend, если он
 * поддерживает /api/gamification/showcase, — тогда её видят коллеги в рейтинге.
 */

const VISITS_KEY = "reactlab.visits.v1";
const CLAIMED_KEY = "reactlab.loginRewards.v1";
const SHOWCASE_KEY = "reactlab.showcase.v1";

export const STREAK_MILESTONES = [
  { days: 3, title: "Три дня подряд", note: "Серия входов 3 дня" },
  { days: 7, title: "Неделя без пропусков", note: "Серия входов 7 дней" },
  { days: 14, title: "Две недели в строю", note: "Серия входов 14 дней" },
  { days: 30, title: "Месяц дисциплины", note: "Серия входов 30 дней" }
];

export function dayKey(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function read(key, fallback) {
  try { const v = JSON.parse(localStorage.getItem(key) || "null"); return v == null ? fallback : v; } catch (e) { return fallback; }
}
function write(key, v) {
  try { localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* приватный режим */ }
}

export function readVisits() {
  return read(VISITS_KEY, []);
}

function shift(key, days) {
  const [y, m, d] = key.split("-").map(Number);
  return dayKey(new Date(y, m - 1, d + days));
}

export function streakOf(visits, today = dayKey()) {
  const set = new Set(visits);
  let cur = set.has(today) ? today : shift(today, -1);
  let n = 0;
  while (set.has(cur)) { n += 1; cur = shift(cur, -1); }
  return n;
}

export function longestStreak(visits) {
  const sorted = [...new Set(visits)].sort();
  let best = 0;
  let run = 0;
  let prev = null;
  sorted.forEach((k) => {
    run = prev && shift(prev, 1) === k ? run + 1 : 1;
    best = Math.max(best, run);
    prev = k;
  });
  return best;
}

/** Последние 7 дней (пн…вс текущей недели) с отметкой захода. */
export function weekStrip(visits, today = new Date()) {
  const set = new Set(visits);
  const dow = (today.getDay() + 6) % 7; // 0 = понедельник
  const labels = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  return labels.map((label, i) => {
    const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - dow + i);
    const key = dayKey(d);
    return { label, key, done: set.has(key), today: i === dow, future: i > dow };
  });
}

/**
 * Отметить заход. Возвращает { firstToday, streak, reward } — reward есть, если сегодня
 * достигнута новая веха серии (её ещё не выдавали).
 */
let visitOnce = null;

export function recordVisit() {
  // Один раз за загрузку страницы (StrictMode вызывает инициализаторы дважды).
  if (visitOnce) return visitOnce;
  visitOnce = recordVisitNow();
  return visitOnce;
}

function recordVisitNow() {
  const visits = readVisits();
  const today = dayKey();
  const firstToday = !visits.includes(today);
  if (firstToday) write(VISITS_KEY, [...visits, today].slice(-400));
  const all = firstToday ? [...visits, today] : visits;
  const streak = streakOf(all, today);
  const claimed = read(CLAIMED_KEY, []);
  const milestone = STREAK_MILESTONES.filter((m) => streak >= m.days && !claimed.includes(m.days)).pop() || null;
  if (milestone) write(CLAIMED_KEY, [...claimed, ...STREAK_MILESTONES.filter((m) => m.days <= milestone.days && !claimed.includes(m.days)).map((m) => m.days)]);
  return { firstToday, streak, reward: milestone, visits: all };
}

export function claimedStreakRewards() {
  return read(CLAIMED_KEY, []);
}

/** Медаль серии для витрины и наград. */
export function streakMedal(days, finish = "enamel", earned = true) {
  return { shape: "circle", finish, text: String(days), earned, backTitle: "Серия входов", backNote: `${days} дней подряд` };
}

// ---------------------------------------------------------------------------
// Витрина: до 6 наград, которые видят коллеги
// ---------------------------------------------------------------------------

export const SHOWCASE_MAX = 6;

/** Элемент витрины — самодостаточный снимок, чтобы его можно было показать другому человеку. */
export function readShowcase() {
  return read(SHOWCASE_KEY, []);
}

export function writeShowcase(items) {
  write(SHOWCASE_KEY, items.slice(0, SHOWCASE_MAX));
}

export function toggleShowcase(item) {
  const list = readShowcase();
  const has = list.some((x) => x.id === item.id);
  const next = has ? list.filter((x) => x.id !== item.id) : [...list, item].slice(0, SHOWCASE_MAX);
  writeShowcase(next);
  return next;
}

// ---------------------------------------------------------------------------
// Окно награды за вход показывается один раз в сутки, даже если компонент монтируется
// повторно (переходы между экранами, возврат со смены, несколько вкладок).
// ---------------------------------------------------------------------------

const REWARD_SHOWN_KEY = "reactlab.rewardShown.v1";

export function rewardShownToday() {
  try { return localStorage.getItem(REWARD_SHOWN_KEY) === dayKey(); } catch (e) { return true; }
}

export function markRewardShown() {
  try { localStorage.setItem(REWARD_SHOWN_KEY, dayKey()); } catch (e) { /* приватный режим */ }
}

/** Мягкая формулировка места в рейтинге — без сравнения «кто ниже вас». */
export function rankPhrase(rank, total) {
  if (!rank || !total) return null;
  const top = Math.max(1, Math.round((rank / total) * 100));
  if (top <= 50) return { short: `Топ-${top}%`, long: `Вы в топ-${top}% рейтинга — место ${rank} из ${total}.` };
  return { short: `${rank} из ${total}`, long: `Место ${rank} из ${total}. Каждая смена приближает к лидерам.` };
}
