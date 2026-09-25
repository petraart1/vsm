import { useState, useEffect } from "react";
import * as api from "../api.js";
import Card from "../components/ui/Card.jsx";
import Badge from "../components/ui/Badge.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import styles from "./Achievements.module.css";

function AchievementCard({ achievement, highlighted }) {
  const cls = [
    styles.card,
    !achievement.earned ? styles.cardLocked : null,
    highlighted ? styles.cardHighlighted : null
  ].filter(Boolean).join(" ");
  return (
    <Card className={cls}>
      <div className={styles.cardHead}>
        <Badge variant={achievement.earned ? "done" : "escalation"}>{achievement.earned ? "★ Получено" : "Заблокировано"}</Badge>
        {achievement.earnedAt && <span className={styles.earnedAt}>{new Date(achievement.earnedAt).toLocaleDateString("ru-RU")}</span>}
      </div>
      <h3 className={styles.title}>{achievement.title}</h3>
      <p className={styles.description}>{achievement.description}</p>
    </Card>
  );
}

/** Полная витрина ачивок (полученных и заблокированных, с понятными условиями).
 * См. design/screens/achievements.md. Данные — ru.vsm.backend.gamification (AchievementDto[]). */
export default function Achievements({ route }) {
  const highlight = route.query && route.query.highlight;
  const [s, setS] = useState({ phase: "loading" });

  function load() {
    setS({ phase: "loading" });
    api.getAchievements().then(
      (data) => setS({ phase: "ready", data }),
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, []);

  if (s.phase === "loading") {
    return (
      <div>
        <PageHeader title="Ачивки" />
        <div className={styles.skeletonGrid}>
          {[1, 2, 3, 4, 5].map((i) => <Skeleton key={i} height="120px" />)}
        </div>
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Не удалось загрузить каталог ачивок." onRetry={load} />;
  }

  const list = s.data;
  const earnedCount = list.filter((a) => a.earned).length;

  return (
    <div>
      <PageHeader title="Ачивки" meta={`Получено ${earnedCount} из ${list.length}`} />
      <div className={styles.grid}>
        {list.map((a) => (
          <AchievementCard key={a.code} achievement={a} highlighted={highlight === a.code} />
        ))}
      </div>
    </div>
  );
}
