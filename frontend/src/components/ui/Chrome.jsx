import { useLayoutEffect, useRef, useState } from "react";
import { LogoMark } from "../brand/Logo.jsx";
import Icon from "./Icon.jsx";
import NotificationBell from "./NotificationBell.jsx";
import ThemeToggle from "./ThemeToggle.jsx";
import styles from "./Chrome.module.css";

export const TABS = [
  { screen: "today", path: "/today", label: "Сегодня", icon: "today" },
  { screen: "shift", path: "/shift", label: "Смена", icon: "train" },
  { screen: "scenarios", path: "/scenarios", label: "Тренировки", icon: "list" },
  { screen: "achievements", path: "/achievements", label: "Награды", icon: "medal" },
  { screen: "leaderboard", path: "/leaderboard", label: "Рейтинг", icon: "podium" }
];

/** Верхняя панель: стекло, логотип слева, тема/уведомления/профиль справа. */
export function TopBar({ activeScreen }) {
  return (
    <header className={styles.topbar}>
      <a className={styles.brand} href="#/today" aria-label="ReactLab, на главную">
        <LogoMark size={28} />
        <span className={styles.wordmark}><b>React</b>Lab</span>
      </a>
      <div className={styles.tools}>
        <ThemeToggle />
        <NotificationBell />
        <a
          className={`${styles.roundBtn} ${activeScreen === "profile" ? styles.roundBtnActive : ""}`}
          href="#/profile"
          aria-label="Профиль"
          aria-current={activeScreen === "profile" ? "page" : undefined}
        >
          <Icon name="user" size={18} />
        </a>
      </div>
    </header>
  );
}

/**
 * Плавающий таб-бар в духе iOS: стеклянная капсула внизу экрана, иконка над подписью.
 * Подложка активной вкладки переезжает к новой вкладке — движение отвечает на действие.
 */
export function TabBar({ activeScreen }) {
  const barRef = useRef(null);
  const [pill, setPill] = useState(null);

  useLayoutEffect(() => {
    function measure() {
      const bar = barRef.current;
      if (!bar) return;
      const active = bar.querySelector('[aria-current="page"]');
      setPill(active ? { x: active.offsetLeft, w: active.offsetWidth } : null);
    }
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [activeScreen]);

  return (
    <nav className={styles.tabbar} aria-label="Разделы">
      <div className={styles.tabInner} ref={barRef}>
        {pill && <span className={styles.pill} style={{ transform: `translateX(${pill.x}px)`, width: pill.w }} aria-hidden="true" />}
        {TABS.map((t) => {
          const active = t.screen === activeScreen;
          return (
            <a key={t.screen} href={`#${t.path}`} className={styles.tab} aria-current={active ? "page" : undefined}>
              <Icon name={t.icon} size={22} strokeWidth={active ? 2.1 : 1.75} />
              <span>{t.label}</span>
            </a>
          );
        })}
      </div>
    </nav>
  );
}
