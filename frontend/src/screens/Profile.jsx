import { useState, useEffect } from "react";
import * as api from "../api.js";
import Card from "../components/ui/Card.jsx";
import Button from "../components/ui/Button.jsx";
import Badge from "../components/ui/Badge.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import styles from "./Profile.module.css";

function initialsFor(name) {
  if (!name) return "ПР";
  const parts = name.split(/[\s-]+/).filter(Boolean);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

/** Профиль проводника — карточка личности, сводные метрики по блокам, витрина ачивок.
 * См. design/screens/profile.md. Данные — ru.vsm.backend.gamification (ProfileResponse). */
export default function Profile() {
  const [s, setS] = useState({ phase: "loading" });

  function load() {
    setS({ phase: "loading" });
    api.getProfile().then(
      (data) => setS({ phase: "ready", data }),
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, []);

  if (s.phase === "loading") {
    return (
      <div>
        <PageHeader title="Профиль" />
        <Skeleton height="100px" className={styles.identity} />
        <Skeleton height="160px" className={styles.section} />
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Не удалось загрузить профиль." onRetry={load} />;
  }

  const p = s.data;
  const hasProgress = p.scenariosCompleted > 0;

  return (
    <div>
      <PageHeader title="Профиль" />
      <Card className={styles.identity}>
        <Avatar initials={initialsFor(p.displayName)} size={64} />
        <div>
          <h2 className={styles.name}>{p.displayName}</h2>
          <p className={styles.score}>Счёт компетенций: {p.totalScore}</p>
          <p className={styles.counts}>
            {p.scenariosCompleted} из {p.totalScenariosAvailable} сценариев · место в рейтинге: {p.leaderboardRank || "—"}
          </p>
        </div>
      </Card>

      {!hasProgress && (
        <Card className={styles.section}>
          <p>Пройдите первый сценарий, чтобы увидеть свой прогресс.</p>
          <Button as="a" variant="primary" className={styles.emptyAction} href="#/scenarios">К списку сценариев</Button>
        </Card>
      )}

      {hasProgress && p.blockProgress.length > 0 && (
        <Card as="section" className={styles.section}>
          <h2 className={styles.sectionTitle}>Прогресс по блокам</h2>
          <div className={styles.blockList}>
            {p.blockProgress.map((bp) => (
              <div key={bp.block}>
                <p>{(bp.blockLabel || bp.block)} — пройдено {bp.scenariosCompleted}</p>
                <p className={styles.blockMeta}>Лояльность: {bp.loyaltyPoints} · Безопасность: {bp.safetyPoints}</p>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card as="section" className={styles.section}>
        <div className={styles.sectionHead}>
          <h2 className={styles.sectionTitle}>Ачивки</h2>
          <a className={styles.sectionLink} href="#/achievements">Все ачивки</a>
        </div>
        {p.recentAchievements.length > 0 ? (
          <div className={styles.achievementRow}>
            {p.recentAchievements.map((a) => (
              <Badge key={a.code} variant="done">★ {a.title}</Badge>
            ))}
          </div>
        ) : (
          <p className={styles.noData}>Пока нет полученных ачивок.</p>
        )}
      </Card>

      <Button as="a" variant="secondary" className={styles.leaderboardLink} href="#/leaderboard">Открыть лидерборд</Button>
    </div>
  );
}
