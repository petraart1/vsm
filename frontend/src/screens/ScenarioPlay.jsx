import { useState, useEffect, useRef } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import { connectProgressChannel } from "../ws.js";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import Timer from "../components/ui/Timer.jsx";
import ScaleBar from "../components/ui/ScaleBar.jsx";
import DeltaBadges from "../components/ui/DeltaBadges.jsx";
import { SplitText } from "../components/motion/Motion.jsx";
import { LogoMark } from "../components/brand/Logo.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ErrorState from "../components/ui/ErrorState.jsx";
import styles from "./ScenarioPlay.module.css";

// Сколько показывается реакция пассажира до автоперехода к следующему узлу (полоса внизу панели
// показывает остаток; «Далее» или Enter переходят сразу).
const AUTO_ADVANCE_MS = 4200;

const SPEAKERS = {
  "НП": "Начальник поезда",
  "ПС": "Пассажир"
};

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

  // Клавиатура: 1–9 — выбрать вариант, Enter — дальше после реакции.
  const keyHandlerRef = useRef(null);
  keyHandlerRef.current = function onKey(ev) {
    if (ev.altKey || ev.ctrlKey || ev.metaKey) return;
    if (s.phase === "playing" && /^[1-9]$/.test(ev.key)) {
      const choice = s.node.choices[Number(ev.key) - 1];
      if (choice) { ev.preventDefault(); handleChoice(choice.id); }
    } else if (s.phase === "reacting" && ev.key === "Enter") {
      ev.preventDefault();
      advance();
    }
  };
  useEffect(() => {
    function listener(ev) { keyHandlerRef.current && keyHandlerRef.current(ev); }
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, []);

  if (s.phase === "loading") {
    return (
      <div className={styles.loading}>
        <Skeleton height="56px" />
        <div className={styles.loadingStage}>
          <Skeleton height="28px" />
          <Skeleton height="120px" />
          <Skeleton height="56px" />
          <Skeleton height="56px" />
        </div>
      </div>
    );
  }

  if (s.phase === "not_found") {
    return (
      <div className={styles.center}>
        <EmptyState
          title="Сценарий не найден"
          message="Возможно, он удалён или ссылка устарела."
          action={<Button as="a" href="#/scenarios">Открыть каталог</Button>}
        />
      </div>
    );
  }

  if (s.phase === "error") {
    return (
      <div className={styles.center}>
        <ErrorState title="Связь с сервером потеряна" message="Решение не отправлено. Повторите — прохождение начнётся с текущего узла." onRetry={load} />
      </div>
    );
  }

  const node = s.node;
  const speaker = SPEAKERS[node.avatarInitials] || "Пассажир";

  return (
    <div className={styles.shell}>
      <header className={styles.bar}>
        <button type="button" className={styles.exit} onClick={handleExit}>
          <Icon name="x" size={16} />
          <span>Выйти</span>
        </button>
        <div className={styles.barTitle}>
          <LogoMark size={20} />
          <span className={styles.barName}>{s.scenario.title}</span>
          <span className={styles.step}>Шаг {s.stepIndex}</span>
        </div>
        <div className={styles.barScales}>
          <ScaleBar type="safety" value={s.scales.safety} compact />
          <ScaleBar type="loyalty" value={s.scales.loyalty} compact />
        </div>
      </header>

      <div className={styles.stage}>
        <div className={styles.speaker} key={`sp-${node.id}`}>
          <Avatar initials={node.avatarInitials} size={36} tone={node.avatarInitials === "НП" ? "solid" : "soft"} />
          <div>
            <p className={styles.speakerName}>{speaker}</p>
            {node.contextNote && <p className={styles.context}>{node.contextNote}</p>}
          </div>
        </div>

        <SplitText
          key={`t-${node.id}`}
          as="p"
          text={node.situationText}
          className={styles.replica}
          maxDuration={900}
          delay={120}
        />

        {node.deadlineAt && s.phase === "playing" && (
          <div className={`${styles.timerRow} rv`} style={{ "--i": 3 }}>
            <Timer
              key={node.id}
              timerSeconds={node.timerSeconds}
              deadlineAt={node.deadlineAt}
              onExpire={handleTimeout}
              remainingOverride={wsSecondsRemaining === null ? undefined : wsSecondsRemaining}
            />
          </div>
        )}

        {s.phase === "playing" && (
          <ol className={styles.choices} key={`c-${node.id}`} aria-label="Варианты ответа">
            {node.choices.map((choice, i) => (
              <li key={choice.id} className="rv" style={{ "--i": i + 4 }}>
                <button type="button" className={styles.choice} onClick={() => handleChoice(choice.id)}>
                  <kbd className={styles.key} aria-hidden="true">{i + 1}</kbd>
                  <span>{choice.text}</span>
                </button>
              </li>
            ))}
          </ol>
        )}

        {s.phase === "reacting" && (
          <section className={styles.reaction} aria-live="polite">
            <div className={styles.reactionHead}>
              {s.reaction.wasTimeout && (
                <span className={styles.flag} data-tone="red"><Icon name="alert" size={14} />Время вышло</span>
              )}
              {s.reaction.escalation && (
                <span className={styles.flag}><Icon name="phone" size={14} />Вызван начальник поезда</span>
              )}
            </div>
            <p className={styles.reactionText}>{s.reaction.text}</p>
            <DeltaBadges deltas={s.reaction.deltas} />
            {s.reaction.hiddenPenalty && <p className={styles.hiddenPenalty}>{s.reaction.hiddenPenalty}</p>}
            <div className={styles.reactionFoot}>
              <span className={styles.hint}>Enter — продолжить</span>
              <Button onClick={advance}>{s.isFinal ? "Открыть разбор" : "Далее"}</Button>
            </div>
            <span className={styles.autoBar} style={{ animationDuration: `${AUTO_ADVANCE_MS}ms` }} aria-hidden="true" />
          </section>
        )}
      </div>
    </div>
  );
}
