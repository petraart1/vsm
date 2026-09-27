/**
 * Пользовательские настройки тренажёра (на этом устройстве): скорость рейса по умолчанию,
 * озвучка, голосовые ответы, вибрация, окно награды за вход.
 */
const KEY = "reactlab.settings.v1";
const DEFAULTS = { tripSpeed: 1, voiceReplies: true, haptics: true, dailyReward: true, reduceMotion: false, simMedProblemRate: 0.25, simStressCount: 2, simTripSeconds: 140 };
const listeners = new Set();

export function getSettings() {
  try { return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || "{}") }; } catch (e) { return { ...DEFAULTS }; }
}

export function setSetting(name, value) {
  const next = { ...getSettings(), [name]: value };
  try { localStorage.setItem(KEY, JSON.stringify(next)); } catch (e) { /* приватный режим */ }
  applyMotion(next);
  listeners.forEach((fn) => fn(next));
  return next;
}

export function subscribeSettings(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/**
 * «Меньше движения». Единый источник для CSS и JS — атрибут data-motion на <html>:
 * "reduce", если включена настройка приложения или системная prefers-reduced-motion
 * (настройка по умолчанию выключена и тогда следует системе), иначе "full".
 */
let motionQuery = null;
export function applyMotion(s = getSettings()) {
  let system = false;
  try {
    if (!motionQuery && window.matchMedia) {
      motionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
      const onChange = () => applyMotion();
      if (motionQuery.addEventListener) motionQuery.addEventListener("change", onChange);
      else if (motionQuery.addListener) motionQuery.addListener(onChange);
    }
    system = !!(motionQuery && motionQuery.matches);
  } catch (e) { /* нет matchMedia */ }
  const reduce = !!s.reduceMotion || system;
  const root = document.documentElement;
  root.setAttribute("data-motion", reduce ? "reduce" : "full");
  // Старое имя атрибута — для стилей, которые ещё проверяют его.
  root.toggleAttribute("data-reduce-motion", reduce);
}
