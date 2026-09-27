import { Fragment, useEffect, useState } from "react";
import { refreshAccount } from "./api.js";
import DailyReward from "./components/engagement/DailyReward.jsx";
import { recordVisit } from "./engagement.js";
import { TopBar, TabBar, Sidebar } from "./components/ui/Chrome.jsx";
import Button from "./components/ui/Button.jsx";
import EmptyState from "./components/ui/EmptyState.jsx";
import Splash from "./components/brand/Splash.jsx";
import { useHashRoute } from "./router.js";
import Today from "./screens/Today.jsx";
import Shift from "./screens/Shift.jsx";
import ScenarioList from "./screens/ScenarioList.jsx";
import ScenarioPlay from "./screens/ScenarioPlay.jsx";
import Debrief from "./screens/Debrief.jsx";
import Profile from "./screens/Profile.jsx";
import Leaderboard from "./screens/Leaderboard.jsx";
import Achievements from "./screens/Achievements.jsx";
import Auth from "./screens/Auth.jsx";
import Settings from "./screens/Settings.jsx";
import Admin from "./screens/Admin.jsx";
import Exam from "./screens/Exam.jsx";
import { useAccount } from "./account.js";
import { prefersReducedMotion } from "./components/motion/Motion.jsx";
import { LogoMark } from "./components/brand/Logo.jsx";
import { logout } from "./api.js";
import { navigate } from "./router.js";

/**
 * Каркас приложения. Навигация — как в iOS-приложении: стеклянная шапка сверху и плавающий
 * таб-бар снизу. Прохождение сценария и сама смена — полноэкранные (без шапки и таб-бара).
 */
export default function App() {
  const route = useHashRoute();
  const account = useAccount();
  const wide = useWide();
  // Заход в приложение отмечается один раз за запуск; первая за день — шторка награды за вход.
  const [visit] = useState(() => ({ ...recordVisit(), splash: !readSplashSeen() }));
  useEffect(() => { refreshAccount(); }, []);
  const screen = route.screen;
  const isAuth = screen === "login" || screen === "register" || screen === "auth";
  const isAdmin = !!(account && account.role === "ADMIN");

  // Администратор только администрирует: игровые разделы ему не показываются, консоль — только на компьютере.
  useEffect(() => {
    if (isAdmin && !isAuth && screen !== "admin" && screen !== "settings") navigate("/admin");
  }, [isAdmin, isAuth, screen]);

  if (isAdmin && !isAuth) {
    if (!wide) return <AdminMobileStub account={account} />;
    if (screen === "settings") {
      return (
        <Fragment>
          <main key={route.path} className="app-main app-admin-settings">
            <a className="admin-back" href="#/admin">← Администрирование</a>
            <Settings route={route} />
          </main>
        </Fragment>
      );
    }
    return <main className="app-admin"><Admin route={route} /></main>;
  }

  const isPlay = screen === "scenarios" && route.segments[2] === "play";
  const isShift = screen === "shift";
  const isExam = screen === "exam";
  const immersive = isPlay || isShift || isAuth || isExam;
  const withSidebar = !immersive || isShift || isPlay || isExam;

  let body;
  if (isPlay) body = <ScenarioPlay route={route} />;
  else if (isShift) body = <Shift route={route} />;
  else if (isExam) body = <Exam route={route} />;
  else if (isAuth) body = <Auth route={route} />;
  else if (screen === "settings") body = <Settings route={route} />;
  else if (screen === "admin") body = <Admin route={route} />;
  else if (screen === "today") body = <Today route={route} />;
  else if (screen === "scenarios") body = <ScenarioList route={route} />;
  else if (screen === "debrief") body = <Debrief route={route} />;
  else if (screen === "profile") body = <Profile route={route} />;
  else if (screen === "leaderboard") body = <Leaderboard route={route} />;
  else if (screen === "achievements") body = <Achievements route={route} />;
  else {
    body = (
      <EmptyState
        title="Страница не найдена"
        message="Такого раздела в тренажёре нет."
        action={<Button as="a" href="#/today">На главную</Button>}
      />
    );
  }

  return (
    <Fragment>
      <Splash />
      {withSidebar && <Sidebar activeScreen={isPlay || isExam ? "scenarios" : screen} />}
      {!immersive && <TopBar activeScreen={screen} />}
      {/* key по пути: при переходе экран монтируется заново и проигрывает свой вход. */}
      <main key={route.path} className={immersive ? `app-play${withSidebar ? " app-play-nav" : ""}` : "app-main"}>{body}</main>
      {!immersive && <TabBar activeScreen={screen} />}
      {/* Награда за вход — только для авторизованных: серия привязана к учётной записи. */}
      {!immersive && account && <DailyReward visit={visit} delay={visit.splash && !prefersReducedMotion() ? 2900 : 700} />}
    </Fragment>
  );
}

/** Широкий экран (≥ 1024 px) — там, где доступна консоль администратора. */
function useWide() {
  const q = "(min-width: 1024px)";
  const [wide, setWide] = useState(() => (window.matchMedia ? window.matchMedia(q).matches : true));
  useEffect(() => {
    if (!window.matchMedia) return undefined;
    const m = window.matchMedia(q);
    const on = () => setWide(m.matches);
    if (m.addEventListener) m.addEventListener("change", on); else m.addListener(on);
    return () => { if (m.removeEventListener) m.removeEventListener("change", on); else m.removeListener(on); };
  }, []);
  return wide;
}

function AdminMobileStub({ account }) {
  return (
    <main className="admin-stub">
      <LogoMark size={44} />
      <h1>Администрирование — на компьютере</h1>
      <p>Консоль администратора рассчитана на большой экран. Откройте тренажёр на компьютере, чтобы управлять учётными записями, сценариями, событиями и наградами.</p>
      <p className="admin-stub-who">{account.displayName || account.login} · администратор</p>
      <Button variant="secondary" onClick={() => { logout(); navigate("/login"); }}>Выйти</Button>
    </main>
  );
}

function readSplashSeen() {
  try { return sessionStorage.getItem("reactlab.splashSeen.v1") === "1"; } catch (e) { return false; }
}
