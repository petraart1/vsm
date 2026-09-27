import { useLayoutEffect, useRef, useState } from "react";
import { LogoMark } from "../brand/Logo.jsx";
import Icon from "./Icon.jsx";
import NotificationBell from "./NotificationBell.jsx";
import ThemeToggle from "./ThemeToggle.jsx";
import { readVisits, streakOf, weekStrip } from "../../engagement.js";
import VerifiedBadge from "./VerifiedBadge.jsx";
import { useAccount, initialsOf } from "../../account.js";
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
  const account = useAccount();
  return (
    <header className={styles.topbar}>
      <a className={styles.brand} href="#/today" aria-label="ReactLab, на главную">
        <LogoMark size={28} />
        <span className={styles.wordmark}><b>React</b>Lab</span>
      </a>
      <div className={styles.tools}>
        <ThemeToggle />
        <NotificationBell />
        <a className={`${styles.roundBtn} ${activeScreen === "settings" ? styles.roundBtnActive : ""}`} href="#/settings" aria-label="Настройки"><Icon name="palette" size={18} /></a>
        {account ? (
          <a
            className={`${styles.avatarBtn} ${activeScreen === "profile" || activeScreen === "settings" ? styles.roundBtnActive : ""}`}
            href="#/profile"
            aria-label="Профиль"
            aria-current={activeScreen === "profile" ? "page" : undefined}
          >
            {initialsOf(account.displayName || account.login)}
            {account.verified && <span className={styles.avatarCheck}><VerifiedBadge size={14} /></span>}
          </a>
        ) : (
          <a className={styles.loginBtn} href="#/login">Войти</a>
        )}
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

/**
 * Боковая навигация для широких экранов (≥ 1024 px) — как в веб-версиях приложений Apple:
 * логотип, разделы, внизу — серия входов и профиль. На телефоне и планшете скрыта (CSS).
 */
export function Sidebar({ activeScreen }) {
  const account = useAccount();
  const visits = readVisits();
  const streak = streakOf(visits);
  const week = weekStrip(visits);
  return (
    <aside className={styles.sidebar} aria-label="Навигация">
      <a className={styles.sideBrand} href="#/today" aria-label="ReactLab, на главную">
        <LogoMark size={32} />
        <span className={styles.wordmark}><b>React</b>Lab</span>
      </a>
      <p className={styles.sideCaption}>Тренажёр проводника ВСМ</p>
      <nav className={styles.sideNav}>
        {TABS.map((t) => {
          const active = t.screen === activeScreen;
          return (
            <a key={t.screen} href={`#${t.path}`} className={styles.sideItem} aria-current={active ? "page" : undefined}>
              <span className={styles.sideIcon}><Icon name={t.icon} size={18} strokeWidth={active ? 2.1 : 1.8} /></span>
              {t.label}
            </a>
          );
        })}
        <span className={styles.sideDivider} />
        <a href="#/profile" className={styles.sideItem} aria-current={activeScreen === "profile" ? "page" : undefined}>
          <span className={styles.sideIcon}><Icon name="user" size={18} /></span>
          Профиль и витрина
        </a>
        <a href="#/settings" className={styles.sideItem} aria-current={activeScreen === "settings" ? "page" : undefined}>
          <span className={styles.sideIcon}><Icon name="palette" size={18} /></span>
          Настройки
        </a>
        {account && account.role === "ADMIN" && (
          <a href="#/admin" className={styles.sideItem} aria-current={activeScreen === "admin" ? "page" : undefined}>
            <span className={styles.sideIcon}><Icon name="clipboard" size={18} /></span>
            Администрирование
          </a>
        )}
      </nav>

      <a className={styles.streakCard} href="#/profile" aria-label={`Серия входов: ${streak} дн.`}>
        <span className={styles.streakHead}>
          <span className={styles.streakFlame}><Icon name="flame" size={16} /></span>
          <span><b className="num">{streak}</b> {plural(streak)} подряд</span>
        </span>
        <span className={styles.streakWeek}>
          {week.map((d) => (
            <span key={d.key} className={styles.streakDay} data-done={d.done || undefined} data-today={d.today || undefined} data-future={d.future || undefined}>
              <i />{d.label}
            </span>
          ))}
        </span>
      </a>

      {account ? (
        <a
          className={styles.sideAccount}
          href="#/profile"
          aria-current={activeScreen === "profile" ? "page" : undefined}
        >
          <span className={styles.sideAvatar}>{initialsOf(account.displayName || account.login)}</span>
          <span className={styles.sideWho}>
            <b>{account.displayName || account.login} {account.verified && <VerifiedBadge size={14} />}</b>
            <span>{account.verified ? "Личность подтверждена" : "Не подтверждён"}</span>
          </span>
        </a>
      ) : (
        <a className={styles.sideLogin} href="#/login"><Icon name="user" size={16} />Войти</a>
      )}

      <div className={styles.sideTools}>
        <ThemeToggle />
        <NotificationBell placement="sidebar" />
      </div>
    </aside>
  );
}

function plural(n) {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return "день";
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "дня";
  return "дней";
}
