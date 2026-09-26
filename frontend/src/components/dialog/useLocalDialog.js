import { useCallback, useRef, useState } from "react";
import { localVerdict, shuffled } from "../../shift/stressScenarios.js";

/**
 * Ведёт локальный граф диалога (стрессовые ситуации рейса) в том же формате ленты, что и
 * useScenarioDialog для backend: ChatDialog рисует оба одинаково.
 * onDone({ safety, loyalty, verdict, log, local: true }) — один раз по завершении.
 */
const STAFF = {
  chief: { kind: "person", outfit: "chief", name: "Начальник поезда", role: "По рации" },
  guard: { kind: "person", outfit: "guard", name: "Охрана поезда", role: "Сопровождение состава" },
  police: { kind: "person", outfit: "police", name: "Транспортная полиция", role: "Наряд на станции" },
  medic: { kind: "person", outfit: "medic", name: "Медицинская помощь", role: "Бригада на станции" }
};

export default function useLocalDialog({ speaker, onDone }) {
  const [state, setState] = useState({ phase: "idle", messages: [], choices: null, node: null, scales: null, busy: false });
  const ref = useRef({ script: null, nodeId: null, log: [], seq: 0, lock: false, seed: 1, deadline: null, speaker });
  ref.current.speaker = speaker;

  const id = () => { ref.current.seq += 1; return `l${ref.current.seq}`; };

  function speakerFor(role) {
    const sp = ref.current.speaker;
    if (role === "passenger") return null; // основной собеседник — портрет в шапке
    if (role === "neighbor") return { kind: "passenger", variant: (sp.variant || 0) + 13, name: "Пассажир рядом" };
    return STAFF[role] || null;
  }

  function nodeMessages(node) {
    if (node.speaker === "narrator") {
      return [{ id: id(), from: "note", text: [node.context, node.text].filter(Boolean).join(" "), tone: "scene" }];
    }
    return [{ id: id(), from: "npc", text: node.text, context: node.context, speaker: speakerFor(node.speaker) }];
  }

  function enter(nodeId, extra = []) {
    const st = ref.current;
    const node = st.script.nodes[nodeId];
    st.nodeId = nodeId;
    st.deadline = node.timer ? Date.now() + node.timer * 1000 : null;
    const msgs = [...extra, ...nodeMessages(node)];
    if (node.end) {
      setState((s) => ({ ...s, phase: "final", busy: false, choices: null, node, messages: [...s.messages, ...msgs] }));
      const log = st.log;
      onDone && onDone({ local: true, safety: log.reduce((a, l) => a + l.safety, 0), loyalty: log.reduce((a, l) => a + l.loyalty, 0), verdict: localVerdict(log), log });
      return;
    }
    st.seed += 7;
    st.lock = false;
    setState((s) => ({ ...s, phase: "playing", busy: false, node: { ...node, id: nodeId }, choices: shuffled(node.choices, st.seed), messages: [...s.messages, ...msgs] }));
  }

  const start = useCallback((script, seed = 1) => {
    ref.current = { ...ref.current, script, nodeId: null, log: [], seq: 0, lock: false, seed, deadline: null };
    setState({ phase: "playing", messages: [], choices: null, node: null, scales: { safety: 60, loyalty: 60 }, busy: false });
    enter(script.start);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function apply(choice, timedOut) {
    const st = ref.current;
    const node = st.script.nodes[st.nodeId];
    const best = Math.max(...node.choices.map((c) => c.safety + c.loyalty)) === choice.safety + choice.loyalty;
    st.log.push({ node: st.nodeId, question: node.text, choice: choice.id, text: choice.text, safety: choice.safety, loyalty: choice.loyalty, critical: !!choice.critical, best: best && !timedOut, note: choice.note, timedOut });
    setState((s) => ({
      ...s,
      choices: null,
      busy: true,
      scales: { safety: clamp(s.scales.safety + choice.safety), loyalty: clamp(s.scales.loyalty + choice.loyalty) },
      messages: [
        ...s.messages,
        timedOut ? { id: id(), from: "note", text: "Время вышло — ситуация развивается без вас", tone: "bad" } : { id: id(), from: "me", text: choice.text }
      ]
    }));
    window.setTimeout(() => {
      const notes = [];
      if (choice.reply) notes.push({ id: id(), from: "npc", text: choice.reply, speaker: speakerFor(node.speaker) });
      notes.push({ id: id(), from: "note", deltas: { safety: choice.safety, loyalty: choice.loyalty }, tone: choice.safety < 0 ? "bad" : undefined });
      if (choice.next) enter(choice.next, notes);
      else setState((s) => ({ ...s, busy: false, messages: [...s.messages, ...notes] }));
    }, 750);
  }

  function choose(choiceId) {
    const st = ref.current;
    if (st.lock || state.phase !== "playing") return;
    const c = state.choices && state.choices.find((x) => x.id === choiceId);
    if (!c) return;
    st.lock = true;
    apply(c, false);
  }

  function timeout() {
    const st = ref.current;
    if (st.lock || state.phase !== "playing") return;
    st.lock = true;
    const node = st.script.nodes[st.nodeId];
    const worst = node.choices.slice().sort((a, b) => (a.safety + a.loyalty) - (b.safety + b.loyalty))[0];
    apply(worst, true);
  }

  const node = state.node;
  const timer = state.phase === "playing" && node && node.timer && !state.busy && ref.current.deadline
    ? { key: `${node.id}-${ref.current.seq}`, seconds: node.timer, deadlineAt: new Date(ref.current.deadline).toISOString(), onExpire: timeout }
    : null;

  return { ...state, speaker, start, choose, timer, sessionId: null, log: ref.current.log };
}

function clamp(v) { return Math.max(0, Math.min(100, v)); }
