import { Fragment } from "react";
import { TopBar, TabBar } from "./components/ui/Chrome.jsx";
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
      {!immersive && <TopBar activeScreen={screen} />}
      {/* key по пути: при переходе экран монтируется заново и проигрывает свой вход. */}
      <main key={route.path} className={immersive ? "app-play" : "app-main"}>{body}</main>
      {!immersive && <TabBar activeScreen={screen} />}
    </Fragment>
  );
}
