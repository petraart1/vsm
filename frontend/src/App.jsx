import { Fragment } from "react";
import Nav from "./components/ui/Nav.jsx";
import Button from "./components/ui/Button.jsx";
import EmptyState from "./components/ui/EmptyState.jsx";
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

  // scenario-play не имеет общего хедера сайта (см. design/screens/scenario-play.md —
  // «модальный/полноэкранный режим в потоке»).
  const isFullscreenPlay = screen === "scenarios" && route.segments[2] === "play";

  let body;
  if (screen === "scenarios" && route.segments[2] === "play") {
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
        message="Страница не найдена."
        action={<Button as="a" variant="primary" href="#/scenarios">К списку сценариев</Button>}
      />
    );
  }

  return (
    <Fragment>
      {!isFullscreenPlay && <Nav activeScreen={screen} />}
      <main className="app-main">{body}</main>
    </Fragment>
  );
}
