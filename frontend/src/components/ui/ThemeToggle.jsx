import { useEffect, useState } from "react";
import Icon from "./Icon.jsx";
import { getTheme, setTheme, styleMeta, subscribeAppearance } from "../../appearance.js";
import styles from "./NotificationBell.module.css";

/** Переключатель светлой/тёмной темы. Скрыт у стилей с фиксированной схемой (Экспресс, Салон, Скорость). */
export default function ThemeToggle() {
  const [, force] = useState(0);
  useEffect(() => subscribeAppearance(() => force((n) => n + 1)), []);

  if (styleMeta().scheme !== "both") return null;
  const theme = getTheme();

  return (
    <button
      type="button"
      className={styles.trigger}
      onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
      aria-label={theme === "dark" ? "Включить светлую тему" : "Включить тёмную тему"}
    >
      <Icon name={theme === "dark" ? "sun" : "moon"} size={15} />
    </button>
  );
}
