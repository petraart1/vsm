import { useState, useEffect, useMemo } from "react";
import * as api from "../api.js";
import Button from "../components/ui/Button.jsx";
import Badge from "../components/ui/Badge.jsx";
import Icon from "../components/ui/Icon.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import ProgressBar from "../components/ui/ProgressBar.jsx";
import ContributionGraph from "../components/progress/ContributionGraph.jsx";
import CertificateCard from "../components/progress/CertificateCard.jsx";
import CertificateDialog from "../components/progress/CertificateDialog.jsx";
import { SplitText, CountUp } from "../components/motion/Motion.jsx";
import {
  readActivity, currentStreak, longestStreak, weekSummary, recentAverages,
  gradeFor, GRADES, buildQualifications, formatDate, pluralRu
} from "../progress.js";
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

const VERDICT_TONE = {
  "Хорошо справились": "green",
  "Есть над чем поработать": "amber",
  "Критическая ошибка безопасности": "red"
};

const STATUS_ORDER = ["certified", "in_training", "not_started"];

function formatNumber(n) {
  return new Intl.NumberFormat("ru-RU").format(Math.round(n));
}

/** Лестница разрядов: шесть ступеней, текущая заполнена пропорционально очкам. */
function GradeLadder({ grade }) {
  return (
    <div className={styles.ladder} aria-hidden="true">
      {GRADES.map((g, i) => {
        let fill = 0;
        if (i < grade.index) fill = 1;
        else if (i === grade.index) fill = grade.next ? grade.progress : 1;
        return (
          <span key={g.title} className={styles.rung} title={`${g.title}: от ${formatNumber(g.min)} очков`}>
            <span className={styles.rungFill} style={{ "--fill": fill, "--i": i }} />
          </span>
        );
      })}
    </div>
  );
}

function Stat({ label, value, unit, note, tone, delay }) {
  return (
    <div className={styles.stat}>
      <dt className={styles.statLabel}>{label}</dt>
      <dd className={styles.statValue} data-tone={tone}>
        {typeof value === "number" ? <CountUp value={value} delay={delay} /> : value}
        {unit && <span className={styles.statUnit}>{unit}</span>}
      </dd>
      {note && <dd className={styles.statNote}>{note}</dd>}
    </div>
  );
}

function ActivityFeed({ activity }) {
  const items = activity.slice(0, 6);
  if (items.length === 0) {
    return <p className={styles.noData}>Здесь появятся ваши прохождения с итогами по шкалам.</p>;
  }
  return (
    <ol className={styles.feed}>
      {items.map((e, i) => {
        const title = e.title || "Сценарий";
        const inner = (
          <>
            <span className={styles.feedMain}>
              <span className={styles.feedTitle}>{title}</span>
              <span className={styles.feedMeta}>
                {formatDate(e.at, { day: "numeric", month: "short" })}
                {e.verdict && <Badge tone={VERDICT_TONE[e.verdict] || "neutral"}>{e.verdict}</Badge>}
              </span>
            </span>
            <span className={styles.feedScores}>
              <span data-scale="safety" title="Рейтинг безопасности"><Icon name="shield" size={12} />{e.safety}</span>
              <span title="Лояльность пассажира"><Icon name="smile" size={12} />{e.loyalty}</span>
            </span>
          </>
        );
        return (
          <li key={`${e.progressId || e.scenarioId}-${i}`}>
            {e.progressId
              ? <a className={styles.feedItem} href={`#/debrief/${e.progressId}`}>{inner}</a>
              : <div className={styles.feedItem}>{inner}</div>}
          </li>
        );
      })}
    </ol>
  );
}

/** Компетенции: успешность по блокам, соблюдение 4 шагов ролевой модели, рекомендации. */
function CompetenciesSection({ competencies, blockLabelOf }) {
  if (!competencies || competencies.totalPlaythroughs === 0) {
    return (
      <p className={styles.noData}>
        {competencies ? "Разбор по компетенциям появится после нескольких прохождений." : "Сервер не вернул аналитику компетенций."}
      </p>
    );
  }

  return (
    <div className={styles.competencyGrid}>
      <div>
        <h3 className={styles.subTitle}>Успешность по блокам</h3>
        <div className={styles.bars}>
          {competencies.blockStats.map((b) => (
            <ProgressBar
              key={b.block}
              label={blockLabelOf(b.block)}
              value={b.successRate * 100}
              valueLabel={`${Math.round(b.successRate * 100)}%`}
              tone={b.weak ? "warning" : "default"}
            />
          ))}
        </div>
      </div>
      <div>
        <h3 className={styles.subTitle}>Ролевая модель ответа</h3>
        <div className={styles.bars}>
          {competencies.roleStepCompliance.map((r) => (
            <ProgressBar key={r.step} label={r.stepLabel} value={r.complianceRate * 100} valueLabel={`${Math.round(r.complianceRate * 100)}%`} tone="safety" />
          ))}
        </div>
        {competencies.weakCompetencies.length > 0 && (
          <>
            <h3 className={styles.subTitle}>Требуют внимания</h3>
            <div className={styles.tags}>
              {competencies.weakCompetencies.map((code) => <Badge key={code} tone="amber">{blockLabelOf(code)}</Badge>)}
            </div>
          </>
        )}
        {competencies.recommendations.length > 0 && (
          <>
            <h3 className={styles.subTitle}>Рекомендуем пройти</h3>
            <ul className={styles.recommendList}>
              {competencies.recommendations.map((r) => (
                <li key={r.scenarioId}>
                  <a href={`#/scenarios/${r.scenarioId}/play`}>{r.title}</a>
                  <span className={styles.muted}>, {RECOMMENDATION_REASON_LABELS[r.reason] || r.reason}</span>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}

/**
 * Профиль проводника: разряд, недельная сводка, журнал тренировок (календарь активности),
 * квалификации по учебным модулям, последние прохождения и компетенции.
 */
export default function Profile() {
  const [s, setS] = useState({ phase: "loading" });
  const [opened, setOpened] = useState(null);
  const activity = useMemo(() => readActivity(), [s.phase]);

  function load() {
    setS({ phase: "loading" });
    Promise.all([
      api.getProfile(),
      api.getCompetencyAnalytics().catch(() => null),
      api.listScenarios().catch(() => null)
    ]).then(
      ([data, competencies, scenarios]) => setS({ phase: "ready", data, competencies, scenarios }),
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, []);

  if (s.phase === "loading") {
    return (
      <div className={styles.page}>
        <Skeleton height="96px" />
        <Skeleton height="96px" />
        <Skeleton height="220px" />
      </div>
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Сервер тренажёра не вернул профиль. Проверьте, что backend запущен, и повторите." onRetry={load} />;
  }

  const p = s.data;
  const grade = gradeFor(p.totalScore);
  const week = weekSummary(activity);
  const streak = currentStreak(activity);
  const bestStreak = longestStreak(activity);
  const averages = recentAverages(activity);
  const qualifications = s.scenarios ? buildQualifications(s.scenarios) : [];
  const certifiedCount = qualifications.filter((q) => q.status === "certified").length;
  const shownQualifications = qualifications
    .slice()
    .sort((a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status))
    .slice(0, 4);

  const blockLabelMap = {};
  qualifications.forEach((q) => { blockLabelMap[q.block] = q.blockLabel; });
  (p.blockProgress || []).forEach((bp) => { blockLabelMap[bp.block] = bp.blockLabel || bp.block; });
  const blockLabelOf = (code) => blockLabelMap[code] || code;

  const weekDelta = week.current - week.previous;
  const sampleNote = averages
    ? `Среднее за ${averages.sample} ${pluralRu(averages.sample, "прохождение", "прохождения", "прохождений")}`
    : "Пока нет данных";

  return (
    <div className={styles.page}>
      <section className={styles.identity} aria-labelledby="profile-name">
        <div className={`${styles.avatarWrap} rv`} style={{ "--i": 0, "--rv-base": "0ms" }}>
          <Avatar initials={initialsFor(p.displayName)} size={72} tone="solid" />
        </div>
        <div className={styles.who}>
          <SplitText as="h1" text={p.displayName} className={styles.name} id="profile-name" />
          <p className={`${styles.gradeLine} rv`} style={{ "--i": 1 }}>
            <strong>{grade.current.title}.</strong> {grade.current.note}.
          </p>
        </div>
        <div className={`${styles.gradeBox} rv`} style={{ "--i": 2 }}>
          <p className={styles.score}>
            <CountUp className={styles.scoreValue} value={p.totalScore} format={formatNumber} duration={1400} delay={300} />
            <span>очков компетенций</span>
          </p>
          <GradeLadder grade={grade} />
          <p className={styles.gradeNext}>
            {grade.next
              ? <>До разряда «{grade.next.title}» осталось {formatNumber(grade.pointsToNext)}</>
              : "Высший разряд программы"}
          </p>
        </div>
      </section>

      <dl className={`${styles.stats} rv`} style={{ "--i": 3 }}>
        <Stat
          label="Эта неделя"
          value={week.current}
          unit={pluralRu(week.current, "сценарий", "сценария", "сценариев")}
          delay={450}
          note={week.previous || week.current
            ? `${weekDelta >= 0 ? "+" : "−"}${Math.abs(weekDelta)} к прошлой неделе`
            : "Начните неделю с одного сценария"}
        />
        <Stat
          label="Серия"
          value={streak}
          unit={pluralRu(streak, "день", "дня", "дней")}
          delay={500}
          note={bestStreak > 0 ? `Рекорд: ${bestStreak} ${pluralRu(bestStreak, "день", "дня", "дней")}` : "Тренируйтесь каждый день"}
        />
        <Stat label="Безопасность" value={averages ? Math.round(averages.safety) : "—"} tone="safety" delay={550} note={sampleNote} />
        <Stat label="Лояльность" value={averages ? Math.round(averages.loyalty) : "—"} delay={600} note={sampleNote} />
        <Stat
          label="Рейтинг"
          value={p.leaderboardRank ? p.leaderboardRank : "—"}
          unit={p.leaderboardRank ? "место" : ""}
          delay={650}
          note={<a href="#/leaderboard">Открыть рейтинг</a>}
        />
      </dl>

      <section className={`${styles.journal} rv`} style={{ "--i": 4 }} aria-labelledby="journal-title">
        <div className={styles.sectionHead}>
          <h2 className={styles.sectionTitle} id="journal-title">Журнал тренировок</h2>
          <span className={styles.sectionMeta}>{p.scenariosCompleted} из {p.totalScenariosAvailable} ситуаций освоено</span>
        </div>
        <ContributionGraph activity={activity} />
        {activity.length === 0 && (
          <div className={styles.emptyRow}>
            <p>Каждое пройденное упражнение отмечается в журнале. Начните с любой ситуации из каталога.</p>
            <Button as="a" href="#/scenarios">Открыть сценарии</Button>
          </div>
        )}
      </section>

      <div className={`${styles.split} rv`} style={{ "--i": 5 }}>
        <section aria-labelledby="qual-title">
          <div className={styles.sectionHead}>
            <h2 className={styles.sectionTitle} id="qual-title">Квалификации</h2>
            <a className={styles.sectionLink} href="#/achievements">
              {certifiedCount} из {qualifications.length || 10} присвоено
            </a>
          </div>
          {qualifications.length > 0 ? (
            <div className={styles.qualList}>
              {shownQualifications.map((q) => (
                <CertificateCard key={q.block} qualification={q} compact onOpen={(item) => setOpened({ kind: "module", q: item })} />
              ))}
            </div>
          ) : (
            <p className={styles.noData}>Сервер не вернул каталог модулей.</p>
          )}
        </section>

        <section aria-labelledby="feed-title">
          <div className={styles.sectionHead}>
            <h2 className={styles.sectionTitle} id="feed-title">Последние прохождения</h2>
          </div>
          <ActivityFeed activity={activity} />
        </section>
      </div>

      <section className={`${styles.competencies} rv`} style={{ "--i": 6 }} aria-labelledby="comp-title">
        <div className={styles.sectionHead}>
          <h2 className={styles.sectionTitle} id="comp-title">Компетенции</h2>
        </div>
        <CompetenciesSection competencies={s.competencies} blockLabelOf={blockLabelOf} />
      </section>

      <CertificateDialog
        item={opened}
        onClose={() => setOpened(null)}
        playerId={p.playerId}
        displayName={p.displayName}
      />
    </div>
  );
}
