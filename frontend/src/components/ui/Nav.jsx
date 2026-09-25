import styles from "./Nav.module.css";

const LINKS = [
  { screen: "scenarios", path: "/scenarios", label: "Сценарии" },
  { screen: "profile", path: "/profile", label: "Профиль" },
  { screen: "leaderboard", path: "/leaderboard", label: "Лидерборд" },
  { screen: "achievements", path: "/achievements", label: "Ачивки" }
];

/** props: activeScreen (текущий route.screen — подсвечивает активный пункт меню). */
export default function Nav({ activeScreen }) {
  return (
    <header className={styles.header}>
      <a className={styles.brand} href="#/scenarios">
        <span className={styles.brandMark}>ВСМ</span>
        <span>Тренажёр проводника</span>
      </a>
      <nav className={styles.nav} aria-label="Основная навигация">
        {LINKS.map((link) => {
          const isActive = link.screen === activeScreen;
          return (
            <a
              key={link.screen}
              href={`#${link.path}`}
              className={`${styles.link}${isActive ? ` ${styles.active}` : ""}`}
            >
              {link.label}
            </a>
          );
        })}
      </nav>
    </header>
  );
}
