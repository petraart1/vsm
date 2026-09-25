import { useState, useEffect, useRef } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import { connectProgressChannel } from "../ws.js";
import Card from "../components/ui/Card.jsx";
import Button from "../components/ui/Button.jsx";
import Badge from "../components/ui/Badge.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Timer from "../components/ui/Timer.jsx";
import ScaleBar from "../components/ui/ScaleBar.jsx";
import ScalesPanel from "../components/ui/ScalesPanel.jsx";
import DeltaBadges from "../components/ui/DeltaBadges.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import styles from "./ScenarioPlay.module.css";

const AUTO_ADVANCE_MS = 1800;

/** props: route (сегменты ["scenarios", id, "play"]) */
export default function ScenarioPlay({ route }) {
  const scenarioId = route.segments[1];

  const [s, setS] = useState({ phase: "loading" });
  const [wsSecondsRemaining, setWsSecondsRemaining] = useState(null);
  const choosingRef = useRef(false);
  const autoAdvanceTimeoutRef = useRef(null);
  const handleWsMessageRef = useRef(null);

  function load() {
    choosingRef.current = false;
    setS({ phase: "loading" });
    api.startScenario(scenarioId).then(
      (res) => {
        if (res.error) {
          setS({ phase: "not_found" });
          return;
        }
        setS({
          phase: "playing",
          sessionId: res.sessionId,
          scenario: res.scenario,
          scales: res.scales,
          node: res.node,
          stepIndex: 1
        });
      },
      () => setS({ phase: "error" })
    );
  }

  useEffect(load, [scenarioId]);
  useEffect(() => () => {
    if (autoAdvanceTimeoutRef.current) clearTimeout(autoAdvanceTimeoutRef.current);
  }, []);

  /**
   * Живой WebSocket-канал таймера/шкал (см. README, раздел «WebSocket: живой таймер и шкалы») —
   * открывается один раз на прохождение, сразу после того как REST-старт дал sessionId, и
   * закрывается при уходе с экрана или смене прохождения (эффект на sessionId, cleanup закрывает
   * сокет). REST остаётся источником истины: сообщения `state`/`completed` намеренно
   * игнорируются (см. handleWsMessage ниже) — сервер шлёт их на КАЖДОЕ применение выбора, включая
   * инициированное этим же клиентом по REST, а значит для собственных действий это дубликат уже
   * обработанного REST-ответа. Пока WS не подключился или замолчал — Timer сам считает от
   * deadlineAt и explicit-таймаут идёт через api.timeout, как раньше (см. Timer.jsx). USE_MOCKS
   * канал не использует — backend с моками не связан.
   */
  useEffect(() => {
    if (api.USE_MOCKS || !s.sessionId) return undefined;
    setWsSecondsRemaining(null);
    const close = connectProgressChannel(s.sessionId, api.getPlayerId(), {
      onMessage: (msg) => handleWsMessageRef.current && handleWsMessageRef.current(msg),
      onDrop: () => setWsSecondsRemaining(null),
      onUnavailable: () => setWsSecondsRemaining(null)
    });
    return close;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [s.sessionId]);

  function handleExit() {
    const ok = window.confirm("Прогресс текущего прохождения будет потерян. Выйти из сценария?");
    if (ok) navigate("/scenarios");
  }

  /** Общий переход в фазу "reacting" — используется и REST-ответом (applyResult), и живым
   * WS-сообщением `timeout` (handleWsMessage) на общей модели api.mapWsAppliedChoice/choose. */
  function applyMapped(mapped) {
    setWsSecondsRemaining(null);
    setS((prev) => ({
      ...prev,
      phase: "reacting",
      scales: mapped.scales,
      reaction: mapped.reaction,
      pendingNode: mapped.node,
      isFinal: mapped.isFinal
    }));
    autoAdvanceTimeoutRef.current = setTimeout(advance, AUTO_ADVANCE_MS);
  }

  function applyResult(promise) {
    promise.then(
      (res) => {
        if (res.error) {
          choosingRef.current = false;
          if (res.error === "progress_already_completed") {
            // Гонка двойного клика/автотаймаута: прохождение уже завершено другим запросом —
            // разбор уже доступен, идём сразу к нему вместо тупикового экрана.
            navigate("/debrief/" + s.sessionId);
            return;
          }
          setS({ phase: "error" });
          return;
        }
        applyMapped(res);
      },
      () => {
        choosingRef.current = false;
        setS({ phase: "error" });
      }
    );
  }

  handleWsMessageRef.current = function handleWsMessage(msg) {
    if (!msg || !msg.type) return;
    if (msg.type === "tick") {
      setWsSecondsRemaining(msg.secondsRemaining);
      return;
    }
    if (msg.type !== "timeout") return; // state/completed — дубликат уже обработанного REST-пути
    if (choosingRef.current || s.phase !== "playing") return; // свой REST-запрос уже в полёте — его ответ и разрулит переход
    choosingRef.current = true;
    applyMapped(api.mapWsAppliedChoice(msg));
  };

  function handleChoice(choiceId) {
    if (choosingRef.current || s.phase !== "playing") return;
    choosingRef.current = true;
    applyResult(api.choose(s.sessionId, choiceId));
  }

  function advance() {
    if (autoAdvanceTimeoutRef.current) {
      clearTimeout(autoAdvanceTimeoutRef.current);
      autoAdvanceTimeoutRef.current = null;
    }
    setS((prev) => {
      if (prev.phase !== "reacting") return prev;
      if (prev.isFinal) {
        navigate("/debrief/" + prev.sessionId);
        return prev;
      }
      choosingRef.current = false;
      return {
        ...prev,
        phase: "playing",
        node: prev.pendingNode,
        reaction: null,
        pendingNode: null,
        stepIndex: (prev.stepIndex || 1) + 1
      };
    });
  }

  function handleTimeout() {
    if (choosingRef.current || s.phase !== "playing") return;
    choosingRef.current = true;
    applyResult(api.timeout(s.sessionId));
  }

  if (s.phase === "loading") {
    return (
      <div className={styles.skeletonStack}>
        <Skeleton height="60px" />
        <Skeleton height="180px" />
        <Skeleton height="200px" />
      </div>
    );
  }

  if (s.phase === "not_found") {
    return (
      <EmptyState
        message="Сценарий не найден или больше не доступен."
        action={<Button as="a" variant="primary" href="#/scenarios">К списку сценариев</Button>}
      />
    );
  }

  if (s.phase === "error") {
    return <ErrorState message="Проблема с соединением. Попробуйте ещё раз." onRetry={load} />;
  }

  const node = s.node;

  return (
    <div className={styles.shell}>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>{s.scenario.title}</h1>
          <p className={styles.subtitle}>{s.scenario.blockLabel} · шаг {s.stepIndex}</p>
        </div>
        <Button variant="secondary" onClick={handleExit}>Выйти</Button>
      </div>

      <ScalesPanel>
        <ScaleBar type="loyalty" value={s.scales.loyalty} />
        <ScaleBar type="safety" value={s.scales.safety} />
      </ScalesPanel>

      <Card className={styles.situationCard}>
        <Avatar initials={node.avatarInitials} />
        <div>
          {node.contextNote && <p className={styles.context}>{node.contextNote}</p>}
          <p className={styles.replica}>{node.situationText}</p>
          {node.deadlineAt && s.phase === "playing" && (
            <Timer
              key={node.id}
              timerSeconds={node.timerSeconds}
              deadlineAt={node.deadlineAt}
              onExpire={handleTimeout}
              remainingOverride={wsSecondsRemaining === null ? undefined : wsSecondsRemaining}
            />
          )}
        </div>
      </Card>

      {s.phase === "playing" && (
        <div className={styles.choiceList}>
          {node.choices.map((choice) => (
            <Button key={choice.id} variant="choice" onClick={() => handleChoice(choice.id)}>
              {choice.text}
            </Button>
          ))}
        </div>
      )}

      {s.phase === "reacting" && (
        <Card className={styles.reactionPanel}>
          {s.reaction.wasTimeout && <Badge variant="escalation">Время вышло</Badge>}
          <p>{s.reaction.text}</p>
          <DeltaBadges deltas={s.reaction.deltas} />
          {s.reaction.escalation && <Badge variant="escalation">☎ Эскалация: вызван начальник поезда</Badge>}
          {s.reaction.hiddenPenalty && <p className={styles.hiddenPenalty}>{s.reaction.hiddenPenalty}</p>}
          <Button variant="primary" onClick={advance}>{s.isFinal ? "К разбору" : "Далее"}</Button>
        </Card>
      )}
    </div>
  );
}
