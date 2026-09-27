/**
 * Светлая/тёмная тема (data-theme на <html>). По умолчанию — как в системе; выбор человека
 * запоминается в браузере и применяется скриптом в index.html до первого рендера.
 */

const THEME_KEY = "reactlab.theme";
const listeners = new Set();

export function subscribeAppearance(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
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
    listeners.forEach((fn) => fn());
  };
  const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (document.startViewTransition && !reduce) document.startViewTransition(apply);
  else apply();
}

/** Тема как в системе: снимаем явный выбор. */
export function setSystemTheme() {
  try { localStorage.removeItem(THEME_KEY); } catch (e) { /* приватный режим */ }
  document.documentElement.removeAttribute("data-theme");
  listeners.forEach((fn) => fn());
}

export function getThemeChoice() {
  try { return localStorage.getItem(THEME_KEY) || "system"; } catch (e) { return "system"; }
}
