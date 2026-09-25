import { useState, useEffect, useCallback } from "react";
import * as api from "../api.js";
import Card from "../components/ui/Card.jsx";
import Button from "../components/ui/Button.jsx";
import Badge from "../components/ui/Badge.jsx";
import ScaleBar from "../components/ui/ScaleBar.jsx";
import ScalesPanel from "../components/ui/ScalesPanel.jsx";
import DeltaBadges from "../components/ui/DeltaBadges.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import Toast from "../components/ui/Toast.jsx";
import { notifyNotificationsChanged } from "../notificationsBus.js";
import styles from "./Debrief.module.css";

function TimelineStep({ step, index, expanded, onToggle }) {
  const text = step.choiceText;
  const isLong = text.length > 90;
  const shown = expanded || !isLong ? text : text.slice(0, 90) + "…";

  return (
    <div className={styles.timelineStep}>
      <div className={styles.marker}>{index + 1}</div>
      <div
        className={styles.stepBody}
        onClick={isLong ? onToggle : undefined}
        role={isLong ? "button" : undefined}
        tabIndex={isLong ? 0 : undefined}
      >
        <p className={styles.stepNodeText}>{step.nodeText}</p>
        <p className={styles.stepChoiceText}>{shown}</p>
        {isLong && (
          <button type="button" className={styles.expandToggle} onClick={onToggle}>
            {expanded ? "Свернуть" : "Показать полностью"}
          </button>
        )}
        <DeltaBadges deltas={step.deltas} className={styles.stepDeltas} />
        {step.escalation && <Badge variant="escalation" className={styles.stepTag}>☎ Эскалация</Badge>}
        {step.scaleConflict && (
          <Badge variant="escalation" className={styles.stepTagSpaced}>Конфликт шкал — осознанный компромисс</Badge>
        )}
        {(step.roleStepsCompleted.length > 0 || step.roleStepsSkipped.length > 0) && (
          <div className={styles.roleStepTags}>
            {step.roleStepsCompleted.map((label) => (
              <span key={`c-${label}`} className={styles.roleStepTag}>{label}</span>
            ))}
            {step.roleStepsSkipped.map((label) => (
              <span key={`s-${label}`} className={`${styles.roleStepTag} ${styles.roleStepTagSkipped}`}>пропущено: {label}</span>
            ))}
          </div>
        )}
        {step.explanation && <p className={styles.stepExplanation}>{step.explanation}</p>}
        {step.hiddenCommunicationEffect && (
          <div className={styles.hiddenEffect}>
            <span className={styles.hiddenEffectIcon} aria-hidden="true">☎</span>
            <div>
              <span className={styles.hiddenEffectLabel}>За кадром</span>
              <span className={styles.hiddenEffectText}>
                Решение принято в переговорах, которые пассажир не слышит (например, по служебной рации) — на его отношении к Вам оно не сказалось, но повлияло на безопасность.
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
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
      <div className={styles.skeletonStack}>
        <Skeleton height="120px" />
        <Skeleton height="260px" />
      </div>
    );
  }

  if (s.phase === "unavailable") {
    return (
      <EmptyState
        message="Разбор недоступен."
        action={<Button as="a" variant="primary" href="#/profile">В профиль</Button>}
      />
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Не удалось загрузить разбор прохождения." onRetry={load} />;
  }

  const d = s.debrief;
  const a = d.accrual;

  return (
    <div className={styles.shell}>
      <Toast toasts={toasts} onDismiss={dismissToast} />
      <div className={styles.verdictBanner}>
        <h1 className={styles.verdictTitle}>{d.verdict}</h1>
        <p className={styles.verdictSubtitle}>{d.scenario.title} · {d.scenario.blockLabel}</p>
        {d.interrupted && <Badge variant="escalation">Прохождение не было завершено обычным образом</Badge>}
      </div>

      <ScalesPanel>
        <ScaleBar type="loyalty" value={d.finalScales.loyalty} />
        <ScaleBar type="safety" value={d.finalScales.safety} />
      </ScalesPanel>

      <Card as="section">
        <h2 className={styles.sectionTitle}>Пройденный путь</h2>
        <div className={styles.timeline}>
          {d.timeline.map((step, i) => (
            <TimelineStep
              key={i}
              index={i}
              step={step}
              expanded={!!expanded[i]}
              onToggle={() => setExpanded((prev) => ({ ...prev, [i]: !prev[i] }))}
            />
          ))}
        </div>
      </Card>

      <Card as="section">
        <h2 className={styles.sectionTitle}>Что можно было сделать иначе</h2>
        <p>{d.summary}</p>
        {d.keyMoment && (
          <div className={styles.keyMoment}>
            <p className={styles.keyMomentNodeText}>{d.keyMoment.nodeText}</p>
            <p><strong>Вы выбрали: </strong>{d.keyMoment.chosenChoiceText}</p>
            <DeltaBadges deltas={d.keyMoment.chosenDeltas} />
            <p className={styles.keyMomentBetter}><strong>Сильнее было бы: </strong>{d.keyMoment.betterChoiceText}</p>
            <DeltaBadges deltas={d.keyMoment.betterDeltas} />
            {d.keyMoment.betterExplanation && (
              <p className={styles.keyMomentBetterExplanation}>
                <strong>Лучше было бы: </strong>{d.keyMoment.betterExplanation}
              </p>
            )}
          </div>
        )}
        {d.normReferences && d.normReferences.length > 0 && (
          <div className={styles.normTags}>
            {d.normReferences.map((ref) => (
              <span key={ref} className={styles.normTag}>Норма: {ref}</span>
            ))}
          </div>
        )}
      </Card>

      <Card as="section">
        <h2 className={styles.sectionTitle}>Начисления</h2>
        {a.totalScore === null ? (
          <p className={styles.accrualSummary}>Начисления временно недоступны.</p>
        ) : (
          <p>Общий счёт: {a.totalScore} очков · пройдено сценариев: {a.scenariosCompleted}</p>
        )}
        {a.recentAchievements.length > 0 ? (
          <div className={styles.achievementRow}>
            {a.recentAchievements.map((ach) => (
              <Badge key={ach.code} as="a" variant="done" href={`#/achievements?highlight=${ach.code}`}>★ {ach.title}</Badge>
            ))}
          </div>
        ) : (
          <p className={styles.accrualSummary}>Новых ачивок пока нет.</p>
        )}
      </Card>

      <div className={styles.actions}>
        <Button as="a" variant="primary" href={`#/scenarios/${d.scenario.id}/play`}>Пройти ещё раз</Button>
        <Button as="a" variant="secondary" href="#/scenarios">К списку сценариев</Button>
        <Button as="a" variant="secondary" href="#/profile">В профиль</Button>
      </div>
    </div>
  );
}
