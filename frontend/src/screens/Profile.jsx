import { useState, useEffect } from "react";
import * as api from "../api.js";
import Card from "../components/ui/Card.jsx";
import Button from "../components/ui/Button.jsx";
import Badge from "../components/ui/Badge.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import PageHeader from "../components/ui/PageHeader.jsx";
import ProgressBar from "../components/ui/ProgressBar.jsx";
import styles from "./Profile.module.css";

function initialsFor(name) {
  if (!name) return "ПР";
  const parts = name.split(/[\s-]+/).filter(Boolean);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[1][0]).toUpperCase();
}

const RECOMMENDATION_REASON_LABELS = {
  NOT_PLAYED: "ещё не пройден",
  FAILED: "в прошлый раз не удалось",
  PARTIAL: "пройден частично"
};

/** Блок «Компетенции»: успешность по блокам ситуаций, соблюдение 4 шагов ролевой модели,
 * просевшие компетенции и рекомендованные сценарии. props: competencies (CompetencyAnalyticsResponse
 * либо null при ошибке загрузки), blockLabelOf(code) — подписи блоков из профиля (тот же каталог,
 * что и в «Прогресс по блокам» выше на странице, без повторного похода за каталогом). */
function CompetenciesSection({ competencies, blockLabelOf }) {
  if (!competencies) {
    return (
      <Card as="section" className={styles.section}>
        <h2 className={styles.sectionTitle}>Компетенции</h2>
        <p className={styles.noData}>Не удалось загрузить аналитику компетенций.</p>
      </Card>
    );
  }

  if (competencies.totalPlaythroughs === 0) {
    return (
      <Card as="section" className={styles.section}>
        <h2 className={styles.sectionTitle}>Компетенции</h2>
        <p className={styles.noData}>Пока недостаточно данных — пройдите несколько сценариев, чтобы увидеть разбор по компетенциям.</p>
      </Card>
    );
  }

  return (
    <Card as="section" className={styles.section}>
      <h2 className={styles.sectionTitle}>Компетенции</h2>

      {competencies.blockStats.length > 0 && (
        <div className={styles.competencyBlocks}>
          {competencies.blockStats.map((b) => (
            <div key={b.block} className={styles.competencyBlock}>
              <ProgressBar
                label={blockLabelOf(b.block)}
                value={b.successRate * 100}
                valueLabel={`${Math.round(b.successRate * 100)}%`}
                tone={b.weak ? "warning" : "default"}
              />
              <p className={styles.blockMeta}>
                Пройдено: {b.playthroughs} · лояльность {Math.round(b.avgLoyaltyScore)} · безопасность {Math.round(b.avgSafetyScore)}
              </p>
            </div>
          ))}
        </div>
      )}

      {competencies.roleStepCompliance.length > 0 && (
        <>
          <h3 className={styles.subTitle}>Ролевая модель ответа</h3>
          <div className={styles.roleSteps}>
            {competencies.roleStepCompliance.map((r) => (
              <ProgressBar
                key={r.step}
                label={r.stepLabel}
                value={r.complianceRate * 100}
                valueLabel={`${Math.round(r.complianceRate * 100)}%`}
              />
            ))}
          </div>
        </>
      )}

      {competencies.weakCompetencies.length > 0 && (
        <div className={styles.weakBlock}>
          <h3 className={styles.subTitle}>Просевшие компетенции</h3>
          <div className={styles.weakTags}>
            {competencies.weakCompetencies.map((code) => (
              <Badge key={code} variant="escalation">{blockLabelOf(code)}</Badge>
            ))}
          </div>
        </div>
      )}

      {competencies.recommendations.length > 0 && (
        <div className={styles.recommendBlock}>
          <h3 className={styles.subTitle}>Рекомендуем пройти</h3>
          <ul className={styles.recommendList}>
            {competencies.recommendations.map((r) => (
              <li key={r.scenarioId} className={styles.recommendItem}>
                <a href={`#/scenarios/${r.scenarioId}/play`}>{r.title}</a>
                <span className={styles.recommendReason}> — {RECOMMENDATION_REASON_LABELS[r.reason] || r.reason}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

/** Профиль проводника — карточка личности, сводные метрики по блокам, витрина ачивок.
 * См. design/screens/profile.md. Данные — ru.vsm.backend.gamification (ProfileResponse). */
export default function Profile() {
  const [s, setS] = useState({ phase: "loading" });

  function load() {
    setS({ phase: "loading" });
    Promise.all([
      api.getProfile(),
      // Компетенции — отдельный агрегат (см. api.js), не должен блокировать остальной профиль,
      // если аналитика недоступна: CompetenciesSection сама покажет "не удалось загрузить".
      api.getCompetencyAnalytics().catch(() => null)
    ]).then(
      ([data, competencies]) => setS({ phase: "ready", data, competencies }),
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
  const blockLabelMap = {};
  (p.blockProgress || []).forEach((bp) => { blockLabelMap[bp.block] = bp.blockLabel || bp.block; });
  const blockLabelOf = (code) => blockLabelMap[code] || code;

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

      {hasProgress && (
        <CompetenciesSection competencies={s.competencies} blockLabelOf={blockLabelOf} />
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
