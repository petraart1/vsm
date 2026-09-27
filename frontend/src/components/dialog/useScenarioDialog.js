import { useCallback, useEffect, useRef, useState } from "react";
import * as api from "../../api.js";
import { connectProgressChannel } from "../../ws.js";

/**
 * Ведёт диалог по сценарию backend: старт с классом вагона, выбор, таймаут, финал с вердиктом
 * из разбора. Сообщения копятся лентой для ChatDialog.
 * onDone({ sessionId, safety, loyalty, verdict, scales }) — вызывается один раз по завершении.
 */
export default function useScenarioDialog({ speaker, onDone, exam = false }) {
  const [state, setState] = useState({ phase: "idle", messages: [], choices: null, node: null, scales: null, busy: false });
  const ref = useRef({ sessionId: null, sum: { safety: 0, loyalty: 0 }, lock: false, seq: 0 });

  const [wsRemaining, setWsRemaining] = useState(null);
  const [sessionId, setSessionId] = useState(null);
  const applyMappedRef = useRef(null);

  // Живой WebSocket-канал таймера (если backend его поднял). REST остаётся источником истины:
  // от WS берём только тики и серверный таймаут, когда свой запрос не в полёте.
  useEffect(() => {
    if (api.USE_MOCKS || !sessionId) return undefined;
    setWsRemaining(null);
    return connectProgressChannel(sessionId, api.getPlayerId(), {
      onMessage: (msg) => {
        if (!msg || !msg.type) return;
        if (msg.type === "tick") setWsRemaining(msg.secondsRemaining);
        else if (msg.type === "timeout" && !ref.current.lock) {
          ref.current.lock = true;
          applyMappedRef.current && applyMappedRef.current(Promise.resolve(api.mapWsAppliedChoice(msg)), null);
        }
      },
      onDrop: () => setWsRemaining(null),
      onUnavailable: () => setWsRemaining(null)
    });
  }, [sessionId]);

  const id = () => { ref.current.seq += 1; return `m${ref.current.seq}`; };

  const npcMessage = (node) => ({ id: id(), from: "npc", text: node.situationText, context: node.contextNote, speaker: node.avatarInitials === "НП" ? { kind: "person", outfit: "chief", name: "Начальник поезда" } : null });

  /** starter — функция, возвращающая промис старта (по умолчанию обычный сценарий; для экзамена — пункт экзамена). */
  const start = useCallback((scenarioId, carClass, starter) => {
    ref.current = { sessionId: null, sum: { safety: 0, loyalty: 0 }, lock: false, seq: 0 };
    setState({ phase: "loading", messages: [], choices: null, node: null, scales: null, busy: true });
    (starter ? starter() : api.startScenario(scenarioId, carClass)).then((res) => {
      if (!res || res.error) { setState((s) => ({ ...s, phase: "error", busy: false })); return; }
      ref.current.sessionId = res.sessionId;
      setSessionId(res.sessionId);
      setState({ phase: "playing", messages: [npcMessage(res.node)], choices: res.node.choices, node: res.node, scales: exam ? null : res.scales, busy: false });
    }, () => setState((s) => ({ ...s, phase: "error", busy: false })));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function apply(promise, myText) {
    setWsRemaining(null);
    setState((s) => ({
      ...s,
      choices: null,
      busy: true,
      messages: myText ? [...s.messages, { id: id(), from: "me", text: myText }] : [...s.messages, { id: id(), from: "note", text: "Время вышло — решение принято за вас", tone: "bad" }]
    }));
    promise.then((res) => {
      if (!res || res.error) { setState((s) => ({ ...s, phase: "error", busy: false })); return; }
      const d = res.reaction.deltas;
      ref.current.sum.safety += d.safety || 0;
      ref.current.sum.loyalty += d.loyalty || 0;
      // На экзамене влияние решений не раскрывается — ни дельт, ни шкал до итога.
      const notes = exam
        ? (res.reaction.escalation ? [{ id: id(), from: "note", text: "Подключается начальник поезда" }] : [])
        : [{ id: id(), from: "note", text: res.reaction.escalation ? "Подключается начальник поезда" : null, deltas: d, tone: d.safety < 0 ? "bad" : undefined }];
      if (res.isFinal) {
        notes.unshift({ id: id(), from: "npc", text: exam ? (res.node && res.node.situationText) || "Ситуация завершена." : res.reaction.text });
        setState((s) => ({ ...s, phase: "final", busy: false, scales: exam ? null : res.scales, messages: [...s.messages, ...notes] }));
        const finish = (verdict) => onDone && onDone({ sessionId: ref.current.sessionId, safety: ref.current.sum.safety, loyalty: ref.current.sum.loyalty, verdict, scales: res.scales });
        if (exam) { finish(null); return; }
        api.getDebrief(ref.current.sessionId).then((db) => finish(db && db.verdict), () => finish(fallbackVerdict(res.scales)));
        return;
      }
      // Пауза «собеседник печатает», затем следующая реплика.
      window.setTimeout(() => {
        ref.current.lock = false;
        setState((s) => ({ ...s, busy: false, scales: exam ? null : res.scales, node: res.node, choices: res.node.choices, messages: [...s.messages, ...notes, npcMessage(res.node)] }));
      }, 650);
    }, () => setState((s) => ({ ...s, phase: "error", busy: false })));
  }

  applyMappedRef.current = apply;

  function choose(choiceId) {
    if (ref.current.lock || state.phase !== "playing") return;
    ref.current.lock = true;
    const c = state.choices && state.choices.find((x) => x.id === choiceId);
    apply(api.choose(ref.current.sessionId, choiceId), c ? c.text : "…");
  }

  function timeout() {
    if (ref.current.lock || state.phase !== "playing") return;
    ref.current.lock = true;
    apply(api.timeout(ref.current.sessionId), null);
  }

  const timer = state.phase === "playing" && state.node && state.node.deadlineAt && !state.busy
    ? { key: state.node.id, seconds: state.node.timerSeconds, deadlineAt: state.node.deadlineAt, onExpire: timeout, remaining: wsRemaining === null ? undefined : wsRemaining }
    : null;

  return { ...state, speaker, start, choose, timer, sessionId };
}

function fallbackVerdict(scales) {
  if (scales.safety < 40) return "Критическая ошибка безопасности";
  if (scales.loyalty < 40 || scales.safety < 55) return "Есть над чем поработать";
  return "Хорошо справились";
}
