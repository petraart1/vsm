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

/** «Меньше движения» внутри приложения — независимо от системной настройки. */
export function applyMotion(s = getSettings()) {
  document.documentElement.toggleAttribute("data-reduce-motion", !!s.reduceMotion);
}
