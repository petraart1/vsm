import { useLayoutEffect, useRef, useState } from "react";
import Logo from "../brand/Logo.jsx";
import NotificationBell from "./NotificationBell.jsx";
import ThemeToggle from "./ThemeToggle.jsx";
import StyleSwitcher from "./StyleSwitcher.jsx";
import styles from "./Nav.module.css";

const LINKS = [
  { screen: "scenarios", path: "/scenarios", label: "Сценарии" },
  { screen: "profile", path: "/profile", label: "Профиль" },
  { screen: "achievements", path: "/achievements", label: "Квалификации" },
  { screen: "leaderboard", path: "/leaderboard", label: "Рейтинг" }
];

/**
 * Шапка: логотип и рабочее пространство, ниже — вкладки. Активную вкладку подчёркивает
 * индикатор, который переезжает к новой вкладке при переходе; при наведении под вкладкой
 * появляется подложка.
 */
export default function Nav({ activeScreen }) {
  const tabsRef = useRef(null);
  const [indicator, setIndicator] = useState(null);

  useLayoutEffect(() => {
    function measure() {
      const root = tabsRef.current;
      if (!root) return;
      const active = root.querySelector('[aria-current="page"]');
      if (!active) { setIndicator(null); return; }
      setIndicator({ left: active.offsetLeft + 12, width: active.offsetWidth - 24 });
    }
    measure();
    window.addEventListener("resize", measure);
    document.fonts && document.fonts.ready.then(measure);
    return () => window.removeEventListener("resize", measure);
  }, [activeScreen]);

  return (
    <header className={styles.header}>
      <div className={styles.top}>
        <div className={styles.brand}>
          <Logo />
          <span className={styles.slash} aria-hidden="true">/</span>
          <span className={styles.space}>Проводник ВСМ-400</span>
        </div>
        <div className={styles.tools}>
          <StyleSwitcher />
          <ThemeToggle />
          <NotificationBell />
        </div>
      </div>
      <nav className={styles.tabs} ref={tabsRef} aria-label="Основная навигация">
        {LINKS.map((link) => {
          const isActive = link.screen === activeScreen;
          return (
            <a
              key={link.screen}
              href={`#${link.path}`}
              className={styles.tab}
              aria-current={isActive ? "page" : undefined}
            >
              {link.label}
            </a>
          );
        })}
        {indicator && (
          <span
            className={styles.indicator}
            style={{ transform: `translateX(${indicator.left}px)`, width: indicator.width }}
            aria-hidden="true"
          />
        )}
      </nav>
    </header>
  );
}
