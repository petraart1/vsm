/**
 * Внешний вид приложения: стиль оформления (data-style на <html>) и светлая/тёмная тема
 * (data-theme). Выбор запоминается в браузере; до первого рендера его применяет скрипт в
 * index.html, чтобы не было вспышки другого стиля.
 */

export const STYLES = [
  {
    key: "mono",
    title: "Монохром",
    note: "Чёрное и белое, линии 1px",
    scheme: "both",
    background: "solid"
  },
  {
    key: "apple",
    title: "Apple",
    note: "Серый фон, матовые панели, синие кнопки",
    scheme: "both",
    background: "solid"
  },
  {
    key: "speed",
    title: "Скорость",
    note: "Графит и световые линии движения",
    scheme: "dark",
    background: "graphic"
  },
  {
    key: "express",
    title: "Экспресс",
    note: "Ночной поезд, стеклянные панели",
    scheme: "dark",
    background: "photo"
  },
  {
    key: "salon",
    title: "Салон",
    note: "Интерьер вагона, светлое стекло",
    scheme: "light",
    background: "photo"
  }
];

const STYLE_KEY = "reactlab.style";
const THEME_KEY = "reactlab.theme";
const listeners = new Set();

export function getStyle() {
  const attr = document.documentElement.getAttribute("data-style");
  return STYLES.some((s) => s.key === attr) ? attr : "mono";
}

export function styleMeta(key = getStyle()) {
  return STYLES.find((s) => s.key === key) || STYLES[0];
}

function notify() {
  listeners.forEach((fn) => fn());
}

export function subscribeAppearance(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Меняет стиль. Если браузер умеет View Transitions — старый вид плавно растворяется в новом. */
export function setStyle(key) {
  const apply = () => {
    document.documentElement.setAttribute("data-style", key);
    try { localStorage.setItem(STYLE_KEY, key); } catch (e) { /* приватный режим */ }
    notify();
  };
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (document.startViewTransition && !reduce) document.startViewTransition(apply);
  else apply();
}

export function getTheme() {
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "dark" || attr === "light") return attr;
  try {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  } catch (e) {
    return "light";
  }
}

export function setTheme(theme) {
  const apply = () => {
    document.documentElement.setAttribute("data-theme", theme);
    try { localStorage.setItem(THEME_KEY, theme); } catch (e) { /* приватный режим */ }
    notify();
  };
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (document.startViewTransition && !reduce) document.startViewTransition(apply);
  else apply();
}
