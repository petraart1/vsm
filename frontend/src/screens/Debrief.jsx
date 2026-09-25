import { useState, useEffect, useCallback } from "react";
import * as api from "../api.js";
import Button from "../components/ui/Button.jsx";
import Badge from "../components/ui/Badge.jsx";
import Icon from "../components/ui/Icon.jsx";
import DeltaBadges from "../components/ui/DeltaBadges.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import Toast from "../components/ui/Toast.jsx";
import { SplitText, CountUp } from "../components/motion/Motion.jsx";
import { notifyNotificationsChanged } from "../notificationsBus.js";
import { toDistinction, BLOCK_ICON } from "../progress.js";
import styles from "./Debrief.module.css";

const VERDICT_TONE = {
  "Хорошо справились": "green",
  "Есть над чем поработать": "amber",
  "Критическая ошибка безопасности": "red"
};

function TimelineStep({ step, index, expanded, onToggle }) {
  const text = step.choiceText;
  const isLong = text.length > 140;
  const shown = expanded || !isLong ? text : text.slice(0, 140) + "…";

  return (
    <li className={styles.step} style={{ "--i": index }}>
      <span className={styles.marker} data-timeout={step.wasTimeout || undefined}>{index + 1}</span>
      <div className={styles.stepBody}>
        <p className={styles.stepNode}>{step.nodeText}</p>
        <p className={styles.stepChoice}>
          {shown}
          {isLong && (
            <button type="button" className={styles.more} onClick={onToggle}>
              {expanded ? "Свернуть" : "Показать полностью"}
            </button>
          )}
        </p>
        <div className={styles.stepMeta}>
          <DeltaBadges deltas={step.deltas} />
          {step.escalation && <Badge><Icon name="phone" size={11} />Эскалация</Badge>}
          {step.scaleConflict && <Badge tone="amber">Конфликт шкал</Badge>}
        </div>
        {(step.roleStepsCompleted.length > 0 || step.roleStepsSkipped.length > 0) && (
          <ul className={styles.roleSteps} aria-label="Шаги ролевой модели">
            {step.roleStepsCompleted.map((label) => (
              <li key={`c-${label}`} data-ok="true"><Icon name="check" size={12} strokeWidth={2.5} />{label}</li>
            ))}
            {step.roleStepsSkipped.map((label) => (
              <li key={`s-${label}`}><Icon name="x" size={12} strokeWidth={2.5} />{label}</li>
            ))}
          </ul>
        )}
        {step.explanation && <p className={styles.explanation}>{step.explanation}</p>}
        {step.hiddenCommunicationEffect && (
          <p className={styles.offstage}>
            <Icon name="radio" size={14} />
            Решение принято в служебных переговорах, которые пассажир не слышит: на лояльность не повлияло, на безопасность — да.
          </p>
        )}
      </div>
    </li>
  );
}

/** props: route (сегменты ["debrief", progressId]) */
export default function Debrief({ route }) {
  const progressId = route.segments[1];
  const [s, setS] = useState({ phase: "loading" });
  const [expanded, setExpanded] = useState({});
  const [toasts, setToasts] = useState([]);

  const dismissToast = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  function load() {
    setS({ phase: "loading" });
    api.getDebrief(progressId).then(
      (res) => {
        if (res.error) {
          setS({ phase: "unavailable" });
          return;
        }
        setS({ phase: "ready", debrief: res });
        // Завершение сценария могло начислить уведомления (новая ачивка/личный рекорд/рост в
        // лидерборде) — обновляем колокольчик в шапке и, если есть новая ачивка, показываем тост
        // прямо здесь (непрочитанные ACHIEVEMENT_UNLOCKED почти наверняка от этого прохождения,
        // т.к. уведомление создаётся синхронно с начислением очков).
        notifyNotificationsChanged();
        api.getNotifications(undefined, true).then((list) => {
          const achievementToasts = (list || [])
            .filter((n) => n.type === "ACHIEVEMENT_UNLOCKED")
            .slice(0, 3)
            .map((n) => ({ id: n.id, title: n.title, body: n.body }));
          if (achievementToasts.length > 0) setToasts(achievementToasts);
        }).catch(() => { /* тост необязателен, разбор всё равно показан */ });
      },
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, [progressId]);

  if (s.phase === "loading") {
    return (
      <div className={styles.skeletons}>
        <Skeleton height="96px" />
        <Skeleton height="120px" />
        <Skeleton height="320px" />
      </div>
    );
  }

  if (s.phase === "unavailable") {
    return (
      <EmptyState
        title="Разбор недоступен"
        message="Прохождение не найдено или ещё не завершено."
        action={<Button as="a" href="#/profile">Открыть профиль</Button>}
      />
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Сервер тренажёра не вернул разбор прохождения." onRetry={load} />;
  }

  const d = s.debrief;
  const a = d.accrual;
  const tone = VERDICT_TONE[d.verdict] || "neutral";

  return (
    <div className={styles.shell}>
      <Toast toasts={toasts} onDismiss={dismissToast} />

      <header className={styles.hero}>
        <p className={`${styles.scenario} rv`} style={{ "--i": 0, "--rv-base": "0ms" }}>
          <Badge icon={BLOCK_ICON[d.scenario.block] || "help"}>{d.scenario.blockLabel}</Badge>
          <span>{d.scenario.title}</span>
        </p>
        <SplitText as="h1" text={d.verdict} className={styles.verdict} data-tone={tone} delay={150} />
        {d.interrupted && (
          <p className={`${styles.interrupted} rv`} style={{ "--i": 2 }}>
            <Icon name="alert" size={14} />Прохождение завершено автоматически, а не последним решением.
          </p>
        )}
      </header>

      <dl className={`${styles.scores} rv`} style={{ "--i": 3 }}>
        <div>
          <dt><Icon name="shield" size={14} />Рейтинг безопасности</dt>
          <dd data-scale="safety"><CountUp value={d.finalScales.safety} delay={500} /></dd>
        </div>
        <div>
          <dt><Icon name="smile" size={14} />Лояльность пассажира</dt>
          <dd><CountUp value={d.finalScales.loyalty} delay={600} /></dd>
        </div>
        <div>
          <dt>Очки компетенций</dt>
          <dd>{a.totalScore === null ? "—" : <CountUp value={a.totalScore} delay={700} />}</dd>
        </div>
        <div>
          <dt>Сценариев пройдено</dt>
          <dd>{a.scenariosCompleted === null ? "—" : <CountUp value={a.scenariosCompleted} delay={800} />}</dd>
        </div>
      </dl>

      <div className={styles.columns}>
        <section className={`${styles.path} rv`} style={{ "--i": 4 }} aria-labelledby="path-title">
          <h2 className={styles.sectionTitle} id="path-title">Ваши решения</h2>
          <ol className={styles.timeline}>
            {d.timeline.map((step, i) => (
              <TimelineStep
                key={i}
                index={i}
                step={step}
                expanded={!!expanded[i]}
                onToggle={() => setExpanded((prev) => ({ ...prev, [i]: !prev[i] }))}
              />
            ))}
          </ol>
        </section>

        <aside className={`${styles.side} rv`} style={{ "--i": 5 }}>
          <section aria-labelledby="lesson-title">
            <h2 className={styles.sectionTitle} id="lesson-title">Что усилить</h2>
            <p className={styles.summary}>{d.summary}</p>
          </section>

          {d.keyMoment && (
            <section className={styles.compare} aria-label="Ключевая развилка">
              <p className={styles.compareNode}>{d.keyMoment.nodeText}</p>
              <div className={styles.option}>
                <p className={styles.optionLabel}>Ваш ответ</p>
                <p className={styles.optionText}>{d.keyMoment.chosenChoiceText}</p>
                <DeltaBadges deltas={d.keyMoment.chosenDeltas} />
              </div>
              <div className={styles.option} data-better="true">
                <p className={styles.optionLabel}>Сильнее</p>
                <p className={styles.optionText}>{d.keyMoment.betterChoiceText}</p>
                <DeltaBadges deltas={d.keyMoment.betterDeltas} />
                {d.keyMoment.betterExplanation && <p className={styles.optionWhy}>{d.keyMoment.betterExplanation}</p>}
              </div>
            </section>
          )}

          {d.normReferences && d.normReferences.length > 0 && (
            <section aria-labelledby="norm-title">
              <h3 className={styles.smallTitle} id="norm-title">Нормы регламента</h3>
              <ul className={styles.norms}>
                {d.normReferences.map((ref) => <li key={ref}>{ref}</li>)}
              </ul>
            </section>
          )}

          {a.recentAchievements.length > 0 && (
            <section aria-labelledby="dist-title">
              <h3 className={styles.smallTitle} id="dist-title">Получены отличия</h3>
              <div className={styles.distinctions}>
                {a.recentAchievements.map((ach) => (
                  <Badge key={ach.code} as="a" tone="inverse" href={`#/achievements?highlight=${ach.code}`}>{toDistinction(ach).title}</Badge>
                ))}
              </div>
            </section>
          )}
        </aside>
      </div>

      <div className={`${styles.actions} rv`} style={{ "--i": 6 }}>
        <Button as="a" size="lg" href={`#/scenarios/${d.scenario.id}/play`}><Icon name="rotate" size={16} />Пройти ещё раз</Button>
        <Button as="a" size="lg" variant="secondary" href="#/scenarios">Другие сценарии</Button>
        <Button as="a" size="lg" variant="ghost" href="#/profile">Профиль</Button>
      </div>
    </div>
  );
}
