import { Fragment, useState } from "react";
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

/**
 * Каркас приложения. Навигация — как в iOS-приложении: стеклянная шапка сверху и плавающий
 * таб-бар снизу. Прохождение сценария и сама смена — полноэкранные (без шапки и таб-бара).
 */
export default function App() {
  const route = useHashRoute();
  // Заход в приложение отмечается один раз за запуск; первая за день — шторка награды за вход.
  const [visit] = useState(() => ({ ...recordVisit(), splash: !readSplashSeen() }));
  const screen = route.screen;
  const isPlay = screen === "scenarios" && route.segments[2] === "play";
  const isShift = screen === "shift";
  const immersive = isPlay || isShift;

  let body;
  if (isPlay) body = <ScenarioPlay route={route} />;
  else if (isShift) body = <Shift route={route} />;
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
      {!immersive && <Sidebar activeScreen={screen} />}
      {!immersive && <TopBar activeScreen={screen} />}
      {/* key по пути: при переходе экран монтируется заново и проигрывает свой вход. */}
      <main key={route.path} className={immersive ? "app-play" : "app-main"}>{body}</main>
      {!immersive && <TabBar activeScreen={screen} />}
      {!immersive && <DailyReward visit={visit} delay={visit.splash ? 2900 : 700} />}
    </Fragment>
  );
}

function readSplashSeen() {
  try { return sessionStorage.getItem("reactlab.splashSeen.v1") === "1"; } catch (e) { return false; }
}
