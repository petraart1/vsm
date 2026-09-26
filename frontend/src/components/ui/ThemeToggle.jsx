import { useEffect, useState } from "react";
import Icon from "./Icon.jsx";
import { getTheme, setTheme, subscribeAppearance } from "../../appearance.js";
import styles from "./Chrome.module.css";

/** Переключатель светлой/тёмной темы в шапке. */
export default function ThemeToggle() {
  const [, force] = useState(0);
  useEffect(() => subscribeAppearance(() => force((n) => n + 1)), []);
  const theme = getTheme();
  return (
    <button
      type="button"
      className={styles.roundBtn}
      onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
      aria-label={theme === "dark" ? "Включить светлую тему" : "Включить тёмную тему"}
    >
      <Icon name={theme === "dark" ? "sun" : "moon"} size={17} />
    </button>
  );
}
