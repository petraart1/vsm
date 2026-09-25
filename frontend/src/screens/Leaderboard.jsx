import { useState, useEffect } from "react";
import * as api from "../api.js";
import Card from "../components/ui/Card.jsx";
import Button from "../components/ui/Button.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import styles from "./Leaderboard.module.css";

function Row({ entry, isMe }) {
  return (
    <Card className={`${styles.row}${isMe ? ` ${styles.rowMine}` : ""}`}>
      <strong className={styles.rank}>#{entry.rank}</strong>
      <Avatar initials={(entry.displayName || "??").slice(0, 2).toUpperCase()} size={40} />
      <div className={styles.rowBody}>
        <p>{entry.displayName}{isMe ? " (Вы)" : ""}</p>
        <p className={styles.rowMeta}>{entry.scenariosCompleted} сценариев пройдено</p>
      </div>
      <strong>{entry.totalScore}</strong>
    </Card>
  );
}

/** Лидерборд — топ игроков + закреплённая карточка "Ваше место". См. design/screens/leaderboard.md.
 * Данные — ru.vsm.backend.gamification (LeaderboardResponse). */
export default function Leaderboard() {
  const [s, setS] = useState({ phase: "loading" });

  function load() {
    setS({ phase: "loading" });
    api.getLeaderboard({ limit: 20 }).then(
      (data) => setS({ phase: "ready", data }),
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, []);

  if (s.phase === "loading") {
    return (
      <div>
        <PageHeader title="Лидерборд" />
        <Skeleton height="60px" className={styles.skeletonRow} />
        <Skeleton height="60px" className={styles.skeletonRow} />
        <Skeleton height="60px" className={styles.skeletonRow} />
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Не удалось загрузить лидерборд." onRetry={load} />;
  }

  const d = s.data;
  const meInTop = d.me && d.top.some((t) => t.playerId === d.me.playerId);

  return (
    <div>
      <PageHeader title="Лидерборд" />
      {d.top.length === 0 && (
        <EmptyState
          className={styles.noMe}
          message="Станьте первым в рейтинге."
          action={<Button as="a" variant="primary" href="#/scenarios">К списку сценариев</Button>}
        />
      )}
      {d.top.length > 0 && (
        <div className={styles.list}>
          {d.top.map((entry) => (
            <Row key={entry.playerId} entry={entry} isMe={d.me && entry.playerId === d.me.playerId} />
          ))}
        </div>
      )}
      {d.me && !meInTop && (
        <div className={styles.meLabel}>
          <p className={styles.meCaption}>Ваше место:</p>
          <Row entry={d.me} isMe />
        </div>
      )}
      {!d.me && (
        <Card className={styles.noMe}>
          <p>Пройдите первый сценарий, чтобы попасть в рейтинг.</p>
          <Button as="a" variant="primary" className={styles.emptyAction} href="#/scenarios">К списку сценариев</Button>
        </Card>
      )}
    </div>
  );
}
