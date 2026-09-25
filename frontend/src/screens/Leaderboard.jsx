import { useState, useEffect } from "react";
import * as api from "../api.js";
import Button from "../components/ui/Button.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import { CountUp } from "../components/motion/Motion.jsx";
import { gradeFor, pluralRu } from "../progress.js";
import styles from "./Leaderboard.module.css";

function initials(name) {
  const parts = String(name || "??").split(/[\s-]+/).filter(Boolean);
  return parts.length > 1 ? (parts[0][0] + parts[1][0]).toUpperCase() : String(name || "??").slice(0, 2).toUpperCase();
}

function Row({ entry, isMe, max, index }) {
  const share = max ? entry.totalScore / max : 0;
  return (
    <li className={`${styles.row} rv`} data-me={isMe || undefined} style={{ "--i": Math.min(index, 10) + 2 }}>
      <span className={styles.rank}>{entry.rank}</span>
      <span className={styles.person}>
        <Avatar initials={initials(entry.displayName)} size={32} tone={isMe ? "solid" : "soft"} />
        <span className={styles.name}>
          {entry.displayName}
          {isMe && <span className={styles.you}>вы</span>}
        </span>
      </span>
      <span className={styles.grade}>{gradeFor(entry.totalScore).current.title}</span>
      <span className={styles.count}>
        {entry.scenariosCompleted} {pluralRu(entry.scenariosCompleted, "сценарий", "сценария", "сценариев")}
      </span>
      <span className={styles.score}>
        <span className={styles.scoreBar} aria-hidden="true"><span style={{ width: `${share * 100}%` }} /></span>
        <CountUp value={entry.totalScore} delay={200 + index * 40} />
      </span>
    </li>
  );
}

/** Рейтинг проводников: топ-20 и закреплённая строка «вы», если игрок не в топе. */
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
        <PageHeader title="Рейтинг" />
        <div className={styles.skeletons}>{[1, 2, 3, 4, 5].map((i) => <Skeleton key={i} height="56px" />)}</div>
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Сервер тренажёра не вернул рейтинг." onRetry={load} />;
  }

  const d = s.data;
  const meInTop = d.me && d.top.some((t) => t.playerId === d.me.playerId);
  const max = d.top.length ? d.top[0].totalScore : 0;

  return (
    <div>
      <PageHeader
        title="Рейтинг"
        description="Проводники по сумме очков компетенций. Очки начисляются за каждое завершённое прохождение: больше за решения без ошибок безопасности."
      />

      {d.top.length === 0 ? (
        <EmptyState
          title="Рейтинг пока пуст"
          message="Пройдите первый сценарий — и вы откроете таблицу."
          action={<Button as="a" href="#/scenarios">Открыть сценарии</Button>}
        />
      ) : (
        <div className={styles.table}>
          <div className={styles.head} aria-hidden="true">
            <span>Место</span>
            <span>Проводник</span>
            <span className={styles.grade}>Разряд</span>
            <span className={styles.count}>Пройдено</span>
            <span className={styles.headScore}>Очки</span>
          </div>
          <ol className={styles.list}>
            {d.top.map((entry, i) => (
              <Row key={entry.playerId} entry={entry} index={i} max={max} isMe={d.me && entry.playerId === d.me.playerId} />
            ))}
          </ol>
          {d.me && !meInTop && (
            <ol className={`${styles.list} ${styles.meList}`} start={d.me.rank}>
              <Row entry={d.me} index={d.top.length} max={max} isMe />
            </ol>
          )}
        </div>
      )}
    </div>
  );
}
