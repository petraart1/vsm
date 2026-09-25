import { Fragment } from "react";
import Nav from "./components/ui/Nav.jsx";
import Button from "./components/ui/Button.jsx";
import EmptyState from "./components/ui/EmptyState.jsx";
import Splash from "./components/brand/Splash.jsx";
import Backdrop from "./components/brand/Backdrop.jsx";
import { useHashRoute } from "./router.js";
import ScenarioList from "./screens/ScenarioList.jsx";
import ScenarioPlay from "./screens/ScenarioPlay.jsx";
import Debrief from "./screens/Debrief.jsx";
import Profile from "./screens/Profile.jsx";
import Leaderboard from "./screens/Leaderboard.jsx";
import Achievements from "./screens/Achievements.jsx";

export default function App() {
  const route = useHashRoute();
  const screen = route.screen;

  // Прохождение сценария — полноэкранный режим без общей шапки (design/screens/scenario-play.md).
  const isFullscreenPlay = screen === "scenarios" && route.segments[2] === "play";

  let body;
  if (isFullscreenPlay) {
    body = <ScenarioPlay route={route} />;
  } else if (screen === "scenarios") {
    body = <ScenarioList route={route} />;
  } else if (screen === "debrief") {
    body = <Debrief route={route} />;
  } else if (screen === "profile") {
    body = <Profile route={route} />;
  } else if (screen === "leaderboard") {
    body = <Leaderboard route={route} />;
  } else if (screen === "achievements") {
    body = <Achievements route={route} />;
  } else {
    body = (
      <EmptyState
        title="Страница не найдена"
        message="Такого адреса в тренажёре нет."
        action={<Button as="a" href="#/scenarios">Открыть сценарии</Button>}
      />
    );
  }

  return (
    <Fragment>
      <Backdrop />
      <Splash />
      {!isFullscreenPlay && <Nav activeScreen={screen} />}
      {/* key по пути: при переходе экран монтируется заново и проигрывает свой вход. */}
      <main key={route.path} className={isFullscreenPlay ? "app-play" : "app-main"}>{body}</main>
    </Fragment>
  );
}
