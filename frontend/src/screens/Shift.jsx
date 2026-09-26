import { useEffect, useMemo, useRef, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import Icon from "../components/ui/Icon.jsx";
import Button from "../components/ui/Button.jsx";
import Rings from "../components/ui/Rings.jsx";
import Medal from "../components/awards/Medal.jsx";
import CarScene from "../components/shift/CarScene.jsx";
import ChatDialog from "../components/dialog/ChatDialog.jsx";
import useScenarioDialog from "../components/dialog/useScenarioDialog.js";
import useLocalDialog from "../components/dialog/useLocalDialog.js";
import { STRESS_SCENARIOS, MED_CONDITIONS, rollCondition, shuffled, findStress } from "../shift/stressScenarios.js";
import { Person } from "../components/characters/People.jsx";
import { CountUp, SplitText } from "../components/motion/Motion.jsx";
import {
  CAR_CLASSES, CLASS_ORDER, STATIONS, rng, seatPassengers, medCheckScript, planInspection,
  planIncidents, summarize, ringsFor, saveShift
} from "../shift/shiftModel.js";
import styles from "./Shift.module.css";

/**
 * «Смена проводника» — основной режим тренажёра.
 * setup (выбор вагона) → med (заступ: медосмотр и инструктаж) → inspect (приёмка вагона) →
 * trip (рейс Москва — Санкт-Петербург, инциденты без предупреждения) → summary (итог и решение для HR).
 */
const TRIP_SECONDS = 140;
const INSPECT_SECONDS = 75;
const DWELL_MS = 3500;

const INSPECT_ICON = { extinguisher: "extinguisher", firstaid: "cross", hammer: "hammer", callbtn: "bell", socket: "bolt", table: "coffee" };
const SPEAKERS = {
  medic: { kind: "person", outfit: "medic", name: "Ирина Сергеевна", role: "Медработник, предрейсовый осмотр" },
  chief: { kind: "person", outfit: "chief", name: "Начальник поезда", role: "Инструктаж бригады" }
};

export default function Shift({ route }) {
  const preset = String(route.segments[1] || "").toUpperCase();
  const [phase, setPhase] = useState("setup");
  const [clsKey, setClsKey] = useState(CAR_CLASSES[preset] ? preset : "STANDARD");
  const [seed, setSeed] = useState(() => Date.now() % 100000);
  const cls = CAR_CLASSES[clsKey];
  const rand = useMemo(() => rng(seed), [seed, clsKey]);
  const passengers = useMemo(() => seatPassengers(cls, rand), [rand, cls]);
  const [situations, setSituations] = useState(null);
  const [medLog, setMedLog] = useState([]);
  const [points, setPoints] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [conditionMode, setConditionMode] = useState("random");
  const [condition, setCondition] = useState("fit");

  useEffect(() => {
    api.listScenarios().then((d) => setSituations(d.situations || []), () => setSituations([]));
  }, []);

  function begin() {
    const nextSeed = Date.now() % 100000;
    setSeed(nextSeed);
    setMedLog([]);
    setCondition(conditionMode === "random" ? rollCondition(rng(nextSeed + 17)) : conditionMode);
    setPhase("med");
  }

  function toInspection() {
    setPoints(planInspection(cls, rand).map((p) => ({ ...p, icon: INSPECT_ICON[p.key] })));
    setPhase("inspect");
  }

  function toTrip(finalPoints) {
    setPoints(finalPoints);
    const extras = finalPoints.filter((p) => p.faulty && !p.checked && p.situationId).map((p) => p.situationId);
    const pool = passengers.length ? passengers : [{ seat: 0 }];
    const backend = planIncidents(situations || [], pool, rand, [...new Set(extras)]);
    // Два стрессовых эпизода: один срочный и один любой, на местах, не занятых другими вызовами.
    const urgent = STRESS_SCENARIOS.filter((x) => x.urgent);
    // ?stress=<id> — принудительный сценарий для демонстрации (например, drunk-rowdy).
    const forced = route.query && route.query.stress ? findStress(route.query.stress) : null;
    const first = forced || urgent[Math.floor(rand() * urgent.length)];
    const rest = STRESS_SCENARIOS.filter((x) => x.id !== first.id);
    const second = rest[Math.floor(rand() * rest.length)];
    const taken = new Set(backend.map((i) => i.seat));
    const free = pool.filter((p) => !taken.has(p.seat));
    const stress = [first, second].map((sc, i) => ({
      key: `st-${i}`,
      local: sc.id,
      title: sc.title,
      block: sc.block,
      blockLabel: sc.blockLabel,
      urgent: sc.urgent,
      mood: sc.mood,
      removal: !!sc.removal,
      seat: (free[i] || pool[(i + 3) % pool.length]).seat,
      x: sc.at === "vestibule" ? "vestibule" : undefined,
      status: "pending",
      remaining: sc.urgent ? 35 : 50,
      result: null
    }));
    // Основные сценарии — 2 из backend + всплывшие после приёмки; вперемешку со стрессовыми.
    const main = backend.filter((i) => !i.fromInspection).slice(0, 2);
    const extra = backend.filter((i) => i.fromInspection);
    const order = [main[0], stress[0], main[1], stress[1], ...extra].filter(Boolean);
    const at = [0.07, 0.22, 0.4, 0.55, 0.7, 0.8, 0.88];
    setIncidents(order.map((i, n) => ({ ...i, spawnAt: at[n] ?? 0.9 })));
    setPhase("trip");
  }

  function finish(finalIncidents) {
    setIncidents(finalIncidents);
    setPhase("summary");
  }

  const exit = () => navigate("/today");

  if (phase === "setup") return <Setup clsKey={clsKey} setClsKey={setClsKey} conditionMode={conditionMode} setConditionMode={setConditionMode} onStart={begin} ready={situations !== null} onBack={exit} />;
  if (phase === "med") return <MedCheck cls={cls} condition={condition} onDone={(log, admitted) => { setMedLog(log); if (admitted) toInspection(); else setPhase("rejected"); }} onExit={exit} />;
  if (phase === "rejected") return <NotAdmitted cls={cls} condition={condition} med={medLog} onAgain={() => setPhase("setup")} />;
  if (phase === "inspect") return <Inspection cls={cls} passengers={passengers} points={points} onDone={toTrip} onExit={exit} />;
  if (phase === "trip") return <Trip cls={cls} passengers={passengers} initial={incidents} onDone={finish} onExit={exit} />;
  return <Summary cls={cls} med={medLog} inspection={points} incidents={incidents} onAgain={() => setPhase("setup")} />;
}

// ---------------------------------------------------------------------------
// Выбор вагона
// ---------------------------------------------------------------------------

function Setup({ clsKey, setClsKey, conditionMode, setConditionMode, onStart, ready, onBack }) {
  return (
    <div className={styles.setupWrap}>
    <aside className={styles.setupArt} aria-hidden="true">
      <img src={`${import.meta.env.BASE_URL}backgrounds/express.jpg`} alt="" />
      <div className={styles.setupShade} />
      <div className={styles.setupCrew}>
        <Person outfit="medic" hair={1} size={210} />
        <Person outfit="chief" hair={2} size={230} />
        <Person outfit="conductor" size={250} />
        <Person outfit="guard" hair={2} size={225} />
      </div>
      <p className={styles.setupQuote}>Бригада поезда Москва — Санкт-Петербург. Ваша смена начинается с медпункта.</p>
    </aside>
    <div className={styles.setup}>
      <header className={styles.navBar}>
        <button type="button" className={styles.back} onClick={onBack}><Icon name="chevronLeft" size={20} />Сегодня</button>
      </header>
      <div className={styles.setupBody}>
        <SplitText as="h1" text="Новая смена" className={styles.largeTitle} />
        <p className={`${styles.lead} rv`} style={{ "--i": 1 }}>Рейс Москва — Санкт-Петербург. Что случится в пути, заранее не известно.</p>

        <h2 className={styles.groupLabel}>Вагон по наряду</h2>
        <ul className={styles.group} role="radiogroup" aria-label="Класс вагона">
          {CLASS_ORDER.map((k, i) => {
            const c = CAR_CLASSES[k];
            const on = k === clsKey;
            return (
              <li key={k} className="rv" style={{ "--i": i + 2 }}>
                <button type="button" role="radio" aria-checked={on} className={styles.carRow} onClick={() => setClsKey(k)}>
                  <CarThumb cls={c} />
                  <span className={styles.carText}>
                    <span className={styles.carTitle}>Вагон {c.car} · {c.title}</span>
                    <span className={styles.carNote}>{c.note}</span>
                  </span>
                  <span className={styles.radio} data-on={on || undefined}>{on && <Icon name="check" size={14} />}</span>
                </button>
              </li>
            );
          })}
        </ul>

        <h2 className={styles.groupLabel}>Самочувствие на заступе</h2>
        <div className={styles.segmented} role="radiogroup" aria-label="Самочувствие на заступе">
          {[["random", "Случайно"], ["fit", "Норма"], ["fever", "Температура"], ["alcohol", "Алкоголь"], ["substances", "Препараты"]].map(([k, label]) => (
            <button key={k} type="button" role="radio" aria-checked={conditionMode === k} data-on={conditionMode === k || undefined} onClick={() => setConditionMode(k)}>{label}</button>
          ))}
        </div>
        <p className={styles.groupNote}>«Случайно» — как в жизни: примерно каждая четвёртая смена начинается с проблемы на медосмотре.</p>

        <h2 className={styles.groupLabel}>Порядок смены</h2>
        <ol className={styles.steps}>
          <li><span className={styles.stepIcon}><Icon name="stethoscope" size={18} /></span><span><b>Заступ</b>Медосмотр и инструктаж у начальника поезда</span></li>
          <li><span className={styles.stepIcon}><Icon name="clipboard" size={18} /></span><span><b>Приёмка вагона</b>{INSPECT_SECONDS} секунд, чтобы найти неисправности</span></li>
          <li><span className={styles.stepIcon}><Icon name="train" size={18} /></span><span><b>Рейс</b>Пассажиры позовут сами — подходите вовремя</span></li>
          <li><span className={styles.stepIcon}><Icon name="flag" size={18} /></span><span><b>Итог</b>Оценивается вся смена, а не отдельный ответ</span></li>
        </ol>
      </div>
      <div className={styles.cta}>
        <Button size="lg" className={styles.ctaBtn} onClick={onStart} disabled={!ready}>{ready ? "Начать смену" : "Готовим рейс…"}</Button>
      </div>
    </div>
    </div>
  );
}

function CarThumb({ cls }) {
  const n = cls.key === "STANDARD" ? 4 : cls.key === "COMFORT" ? 3 : cls.key === "BUSINESS" ? 3 : 2;
  const w = 64 / n;
  return (
    <svg className={styles.thumb} viewBox="0 0 72 44" width="72" height="44" aria-hidden="true">
      <rect x="1" y="1" width="70" height="42" rx="10" className={styles.thumbBody} />
      {Array.from({ length: n }).map((_, i) => (
        <g key={i}>
          <rect x={4 + i * w + w * 0.2} y="8" width={w * 0.6} height="10" rx="3" className={styles.thumbWin} />
          <rect x={4 + i * w + w * 0.18} y="20" width={w * 0.64} height="17" rx="4" style={{ fill: cls.seat }} />
          <rect x={4 + i * w + w * 0.26} y="21.5" width={w * 0.48} height="4" rx="2" style={{ fill: cls.headrest }} />
        </g>
      ))}
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Заступ: медосмотр и инструктаж (локальный сценарий)
// ---------------------------------------------------------------------------

function MedCheck({ cls, condition, onDone, onExit }) {
  const cond = condition !== "fit" ? MED_CONDITIONS[condition] : null;
  const script = useMemo(() => (cond
    ? cond.steps.map((st, i) => ({ id: `${condition}-${i}`, speaker: st.speaker, text: st.text, context: i === 0 ? `${cond.context} ${cond.measure}.` : undefined, choices: st.choices }))
    : medCheckScript(cls)), [cls, cond, condition]);
  const [step, setStep] = useState(0);
  const [messages, setMessages] = useState(() => [npcMsg(script[0], 0)]);
  const [waiting, setWaiting] = useState(true);
  const [busy, setBusy] = useState(false);
  const [log, setLog] = useState([]);
  const [scales, setScales] = useState({ safety: 60, loyalty: 60 });
  const done = step >= script.length;
  const cur = script[Math.min(step, script.length - 1)];
  const choices = useMemo(() => (step < script.length ? shuffled(script[step].choices, step + 5) : null), [script, step]);

  function choose(id) {
    if (!waiting) return;
    const s = script[step];
    const c = s.choices.find((x) => x.id === id);
    const best = Math.max(...s.choices.map((x) => x.safety + x.loyalty)) === c.safety + c.loyalty;
    setWaiting(false);
    setBusy(true);
    setMessages((m) => [...m, { id: `me${step}`, from: "me", text: c.text }]);
    setLog((l) => [...l, { step: s.id, choice: c.id, safety: c.safety, loyalty: c.loyalty, best, critical: !!c.critical, note: c.note, question: s.text }]);
    window.setTimeout(() => {
      setBusy(false);
      setScales((v) => ({ safety: clamp(v.safety + c.safety), loyalty: clamp(v.loyalty + c.loyalty) }));
      const next = step + 1;
      setMessages((m) => [
        ...m,
        { id: `r${step}`, from: "npc", text: c.reply, speaker: SPEAKERS[s.speaker] },
        { id: `d${step}`, from: "note", deltas: { safety: c.safety, loyalty: c.loyalty }, tone: c.safety < 0 ? "bad" : undefined },
        ...(next < script.length ? [npcMsg(script[next], next)] : [])
      ]);
      setStep(next);
      setWaiting(next < script.length);
    }, 800);
  }

  const speaker = SPEAKERS[cur.speaker];
  return (
    <div className={styles.stage}>
      <PhaseBar title="Заступ на смену" subtitle={`Шаг ${Math.min(step + 1, script.length)} из ${script.length}`} onExit={onExit} />
      <div className={styles.room} aria-hidden="true">
        <div className={styles.roomWall}>
          <span className={styles.roomSign}>Медпункт · Ленинградский вокзал</span>
          <span className={styles.roomPoster}><Icon name="cross" size={22} /></span>
          {cond && <span className={styles.readout} data-bad><Icon name={condition === "fever" ? "thermometer" : condition === "alcohol" ? "alert" : "stethoscope"} size={16} />{cond.measure}</span>}
        </div>
        <div className={styles.roomFloor} />
        <div className={styles.roomDesk} />
        <div className={styles.roomHero}><Person outfit="conductor" facing="right" size={170} talking={busy} /></div>
        <div className={styles.roomNpc} key={cur.speaker}><Person outfit={cur.speaker} facing="left" hair={cur.speaker === "medic" ? 1 : 2} skin={cur.speaker === "medic" ? 4 : 1} size={170} talking={waiting} /></div>
      </div>
      <ChatDialog
        speaker={speaker}
        messages={messages}
        choices={waiting ? choices : null}
        onChoose={choose}
        busy={busy}
        scales={scales}
        className={styles.medSheet}
        footer={done && !busy ? (
          cond
            ? <Button size="lg" className={styles.ctaBtn} onClick={() => onDone(log, false)}>Итог заступа</Button>
            : <Button size="lg" className={styles.ctaBtn} onClick={() => onDone(log, true)}>Выйти к вагону — приёмка</Button>
        ) : null}
      />
    </div>
  );
}

/** Итог заступа при недопуске: смена не начинается, но честное поведение засчитывается. */
function NotAdmitted({ cls, condition, med, onAgain }) {
  const cond = MED_CONDITIONS[condition];
  const critical = med.some((m) => m.critical);
  const honest = med.every((m) => m.best);
  const savedRef = useRef(false);
  useEffect(() => {
    if (savedRef.current) return;
    savedRef.current = true;
    saveShift({ cls: cls.key, score: 0, rings: { procedure: honest ? 1 : 0, reaction: 0, quality: 0 }, admitted: false, rejected: condition, safety: med.reduce((a, m) => a + m.safety, 0), loyalty: 0 });
  }, [cls, condition, honest, med]);
  return (
    <div className={styles.summary}>
      <div className={styles.summaryInner}>
        <p className={`${styles.eyebrow} rv`}>Заступ на смену · Вагон {cls.car}</p>
        <SplitText as="h1" text="Не допущен к смене" className={styles.largeTitle} />
        <section className={`${styles.hr} ${styles.hrReject} rv`} style={{ "--i": 1 }}>
          <span className={styles.rejectIcon}><Icon name={condition === "fever" ? "thermometer" : condition === "alcohol" ? "alert" : "stethoscope"} size={28} /></span>
          <div className={styles.hrText}>
            <p className={styles.hrLabel}>Решение медработника</p>
            <p className={styles.hrTitle}>{cond.verdict}</p>
            <p className={styles.hrNote}>{cond.law}</p>
          </div>
        </section>
        <section className={`${styles.verdictCard} rv`} style={{ "--i": 2 }} data-ok={!critical || undefined}>
          <p className={styles.hrLabel}>Оценка поведения</p>
          <p className={styles.verdictTitle}>{critical ? "Грубое нарушение на медосмотре" : honest ? "Вы действовали правильно" : "Есть ошибки в поведении"}</p>
          <p className={styles.verdictNote}>{critical
            ? "Попытка скрыть состояние, подделать результат или выйти на смену вопреки решению медработника — повод для служебного расследования."
            : "Недопуск — не провал тренировки. Проводник, который честно сообщает о своём состоянии, защищает пассажиров и бригаду."}</p>
        </section>
        <h2 className={styles.groupLabel}>Разбор</h2>
        <ul className={styles.group}>
          {med.map((m, i) => (
            <li key={i} className={styles.factNote} data-best={m.best || undefined}>
              <b className={styles.noteMark}>{m.best ? "Верно" : m.critical ? "Критично" : "Ошибка"}</b> {m.note}
            </li>
          ))}
        </ul>
        <div className={styles.summaryActions}>
          <Button size="lg" className={styles.ctaBtn} onClick={onAgain}>Новая смена</Button>
          <Button size="lg" variant="secondary" className={styles.ctaBtn} as="a" href="#/today">На главную</Button>
        </div>
      </div>
    </div>
  );
}

function npcMsg(s, i) {
  return { id: `q${i}`, from: "npc", text: s.text, context: s.context, speaker: SPEAKERS[s.speaker] };
}

// ---------------------------------------------------------------------------
// Приёмка вагона
// ---------------------------------------------------------------------------

function Inspection({ cls, passengers, points: initial, onDone, onExit }) {
  const [points, setPoints] = useState(initial);
  const [left, setLeft] = useState(INSPECT_SECONDS);
  const [card, setCard] = useState(null);
  const doneRef = useRef(false);

  useEffect(() => {
    const t = window.setInterval(() => setLeft((v) => Math.max(0, v - 1)), 1000);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => { if (left === 0) accept(); });

  function accept() {
    if (doneRef.current) return;
    doneRef.current = true;
    onDone(points);
  }

  function onInteract({ type, key }) {
    if (type !== "hotspot") return;
    const p = points.find((x) => x.key === key);
    if (!p || p.checked) return;
    setPoints((list) => list.map((x) => (x.key === key ? { ...x, checked: true } : x)));
    setCard({ ...p, at: Date.now() });
  }

  useEffect(() => {
    if (!card) return undefined;
    const t = window.setTimeout(() => setCard(null), 3200);
    return () => window.clearTimeout(t);
  }, [card]);

  const checked = points.filter((p) => p.checked).length;
  // Пассажиров на приёмке нет: вагон пустой.
  return (
    <div className={styles.stage}>
      <PhaseBar
        title="Приёмка вагона"
        subtitle={`Проверено ${checked} из ${points.length}`}
        onExit={onExit}
        right={<span className={styles.clock} data-low={left <= 15 || undefined}><CountDown value={left} /></span>}
      />
      <div className={styles.scene}>
        <CarScene
          cls={cls}
          passengers={[]}
          hotspots={points.map((p) => ({ key: p.key, x: p.x, icon: p.icon, title: p.title, state: p.checked ? (p.faulty ? "fault" : "ok") : "idle" }))}
          onInteract={onInteract}
          stationName="Москва"
        />
        {card && (
          <div className={styles.toastCard} key={card.at} data-tone={card.faulty ? "bad" : "ok"} role="status">
            <span className={styles.toastIcon}><Icon name={card.faulty ? "alert" : "check"} size={18} /></span>
            <span>
              <b>{card.title}</b>
              {card.faulty ? `${card.fault} Заявка передана в депо.` : card.ok}
            </span>
          </div>
        )}
        {!card && checked === 0 && <div className={styles.coach}>Обойдите вагон: у каждой точки осмотра — синяя метка.</div>}
      </div>
      <div className={styles.dockBar}>
        <Button size="lg" className={styles.ctaBtn} onClick={accept}>
          {checked === points.length ? "Принять вагон" : `Принять вагон (${checked}/${points.length})`}
        </Button>
      </div>
      <span hidden>{passengers.length}</span>
    </div>
  );
}

function CountDown({ value }) {
  const m = Math.floor(value / 60);
  const s = String(value % 60).padStart(2, "0");
  return <span className="num">{m}:{s}</span>;
}

// ---------------------------------------------------------------------------
// Рейс
// ---------------------------------------------------------------------------

function Trip({ cls, passengers, initial, onDone, onExit }) {
  const [incidents, setIncidents] = useState(initial);
  const [progress, setProgress] = useState(0);
  const [stop, setStop] = useState({ name: STATIONS[0].name, until: Date.now() + 2500 });
  const [paused, setPaused] = useState(false);
  const [talk, setTalk] = useState(null); // ключ инцидента в диалоге
  const [toast, setToast] = useState(null);
  const [finished, setFinished] = useState(false);
  const passedRef = useRef(new Set([0]));
  const incRef = useRef(incidents);
  incRef.current = incidents;

  const talkInc = incidents.find((i) => i.key === talk) || null;
  const talkPassenger = talkInc ? passengers.find((p) => p.seat === talkInc.seat) : null;
  const [removals, setRemovals] = useState([]);
  const talkSpeaker = talkInc
    ? { kind: "passenger", variant: talkPassenger ? talkPassenger.variant : 3, mood: talkInc.mood || "calm", name: talkInc.x === "vestibule" ? "Пассажир в тамбуре" : `Пассажир, место ${talkInc.seat + 1}`, role: `Вагон ${cls.car} · ${cls.title}` }
    : { kind: "passenger", variant: 0, name: "", role: "" };
  const finishIncident = (result) => {
    const key = talkRef.current;
    const inc = incRef.current.find((i) => i.key === key);
    setIncidents((list) => list.map((i) => (i.key === key ? { ...i, status: "done", result } : i)));
    if (inc && inc.removal && result.verdict !== "Критическая ошибка безопасности") {
      setRemovals((r) => [...r, { seat: inc.seat, state: "pending" }]);
    }
  };
  const remoteDialog = useScenarioDialog({ speaker: talkSpeaker, onDone: finishIncident });
  const localDialog = useLocalDialog({ speaker: talkSpeaker, onDone: finishIncident });
  const dialog = talkInc && talkInc.local ? localDialog : remoteDialog;
  const talkRef = useRef(null);
  talkRef.current = talk;

  const frozen = paused || talk !== null;

  // Главный цикл рейса: 4 тика в секунду.
  useEffect(() => {
    const TICK = 250;
    const t = window.setInterval(() => {
      if (frozen) return;
      const now = Date.now();
      const atStation = stop && now < stop.until;
      if (stop && now >= stop.until && stop.name !== STATIONS[STATIONS.length - 1].name) setStop(null);

      if (!atStation) {
        setProgress((p) => {
          if (p >= 1) return 1;
          const np = Math.min(1, p + TICK / 1000 / TRIP_SECONDS);
          STATIONS.forEach((s, idx) => {
            if (idx > 0 && !passedRef.current.has(idx) && np >= s.at) {
              passedRef.current.add(idx);
              const last = idx === STATIONS.length - 1;
              setStop({ name: s.name, until: last ? Infinity : now + DWELL_MS });
              setToast({ text: last ? `Прибытие: ${s.name}` : `Стоянка: ${s.name}`, icon: "flag", at: now });
            }
          });
          return np;
        });
      }

      // Инциденты: появление и таймер ожидания пассажира (идёт и на стоянках).
      setIncidents((list) => {
        let changed = false;
        const next = list.map((i) => {
          if (i.status === "pending" && progressRef.current >= i.spawnAt) {
            changed = true;
            setToast({ text: i.urgent ? `Срочный вызов: место ${i.seat + 1}` : `Пассажир зовёт: место ${i.seat + 1}`, icon: i.urgent ? "alert" : "hand", urgent: i.urgent, at: now });
            try { navigator.vibrate && navigator.vibrate(i.urgent ? [60, 40, 60] : 40); } catch (e) { /* нет API */ }
            return { ...i, status: "active", total: i.remaining };
          }
          if (i.status === "active") {
            changed = true;
            const r = i.remaining - TICK / 1000;
            if (r <= 0) {
              setToast({ text: `Пассажир на месте ${i.seat + 1} не дождался проводника`, icon: "alert", urgent: true, at: now });
              return { ...i, status: "missed", remaining: 0 };
            }
            return { ...i, remaining: r };
          }
          return i;
        });
        return changed ? next : list;
      });
    }, TICK);
    return () => window.clearInterval(t);
  }, [frozen, stop]);

  const progressRef = useRef(0);
  progressRef.current = progress;

  // Конец рейса: прибыли, все вызовы закрыты или пропущены.
  useEffect(() => {
    if (finished || progress < 1 || talk !== null) return undefined;
    const open = incidents.some((i) => i.status === "pending" || i.status === "active");
    if (open) return undefined;
    const t = window.setTimeout(() => { setFinished(true); onDone(incRef.current); }, 1800);
    return () => window.clearTimeout(t);
  }, [progress, incidents, talk, finished, onDone]);

  useEffect(() => {
    if (!toast) return undefined;
    const t = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(t);
  }, [toast]);

  function onInteract({ type, key }) {
    if (type !== "signal") return;
    const inc = incidents.find((i) => i.key === key);
    if (!inc || inc.status !== "active") return;
    setIncidents((list) => list.map((i) => (i.key === key ? { ...i, status: "talking" } : i)));
    setTalk(key);
    if (inc.local) localDialog.start(findStress(inc.local), inc.seat + 11);
    else remoteDialog.start(inc.scenarioId, cls.key);
  }

  // Наряд полиции с охраной приходят на ближайшей стоянке и уводят нарушителя (п. 33 «а» ПП № 810).
  useEffect(() => {
    if (stop && stop.name !== STATIONS[0].name) {
      setRemovals((r) => (r.some((x) => x.state === "pending") ? r.map((x) => (x.state === "pending" ? { ...x, state: "active" } : x)) : r));
    } else if (!stop) {
      setRemovals((r) => (r.some((x) => x.state === "active") ? r.map((x) => (x.state === "active" ? { ...x, state: "done" } : x)) : r));
    }
  }, [stop]);
  useEffect(() => {
    if (removals.some((x) => x.state === "active")) setToast({ text: "Наряд транспортной полиции в вагоне", icon: "shield", at: Date.now() });
  }, [removals]);

  function closeDialog() {
    setTalk(null);
  }

  const signals = incidents.filter((i) => i.status === "active").map((i) => ({ key: i.key, seat: i.seat, x: i.x, urgent: i.urgent, remaining: i.remaining, total: i.total }));
  const moods = {};
  incidents.forEach((i) => { if (i.mood && i.x !== "vestibule" && (i.status === "active" || i.status === "talking")) moods[i.seat] = i.mood; });
  const gone = new Set(removals.filter((x) => x.state === "done").map((x) => x.seat));
  const visitors = removals.filter((x) => x.state === "active").flatMap((x) => [{ key: `p${x.seat}`, role: "police", seat: x.seat, offset: -46 }, { key: `g${x.seat}`, role: "guard", seat: x.seat, offset: 46 }]);
  const moving = !(stop && Date.now() < stop.until) && progress < 1;
  const doneCount = incidents.filter((i) => i.status === "done").length;

  return (
    <div className={styles.stage}>
      <header className={styles.tripBar}>
        <button type="button" className={styles.roundBtn} onClick={() => setPaused(true)} aria-label="Пауза"><Icon name="pause" size={16} /></button>
        <RouteLine progress={progress} />
        <span className={styles.counter} title="Обработано вызовов"><Icon name="hand" size={14} />{doneCount}</span>
      </header>

      <div className={styles.scene}>
        <CarScene
          cls={cls}
          passengers={passengers.filter((p) => !gone.has(p.seat))}
          moods={moods}
          visitors={visitors}
          signals={signals}
          moving={moving}
          stationName={stop ? stop.name : null}
          disabled={frozen}
          onInteract={onInteract}
          focusSeat={talkInc ? talkInc.seat : null}
        />
        {toast && (
          <div className={styles.banner} key={toast.at} data-urgent={toast.urgent || undefined} role="status">
            <Icon name={toast.icon} size={16} />{toast.text}
          </div>
        )}
      </div>

      {talk !== null && (
        <>
          <div className={styles.scrim} />
          <ChatDialog
            speaker={dialog.speaker}
            messages={dialog.messages}
            choices={dialog.phase === "playing" && !dialog.busy ? dialog.choices : null}
            onChoose={dialog.choose}
            timer={dialog.timer}
            busy={dialog.busy}
            scales={dialog.scales}
            footer={
              dialog.phase === "final" ? <Button size="lg" className={styles.ctaBtn} onClick={closeDialog}>Вернуться к работе</Button>
                : dialog.phase === "error" ? <Button size="lg" variant="secondary" className={styles.ctaBtn} onClick={() => { setIncidents((l) => l.map((i) => (i.key === talk ? { ...i, status: "done", result: { safety: 0, loyalty: 0, verdict: "Нет связи с сервером" } } : i))); closeDialog(); }}>Нет связи — продолжить смену</Button>
                  : null
            }
          />
        </>
      )}

      {paused && (
        <>
          <div className={styles.scrim} onClick={() => setPaused(false)} />
          <div className={styles.pauseSheet} role="dialog" aria-label="Пауза">
            <span className={styles.grabber} />
            <h2>Смена на паузе</h2>
            <p>Таймеры остановлены. Досрочное завершение не засчитывается.</p>
            <Button size="lg" className={styles.ctaBtn} onClick={() => setPaused(false)}>Продолжить</Button>
            <Button size="lg" variant="ghost" className={styles.ctaBtn} onClick={onExit}>Завершить смену</Button>
          </div>
        </>
      )}
    </div>
  );
}

function RouteLine({ progress }) {
  return (
    <div className={styles.route} aria-label={`Пройдено ${Math.round(progress * 100)}% пути`}>
      <div className={styles.routeTrack}>
        <div className={styles.routeFill} style={{ transform: `scaleX(${progress})` }} />
        {STATIONS.map((s) => (
          <span key={s.name} className={styles.routeDot} data-passed={progress >= s.at || undefined} style={{ left: `${s.at * 100}%` }} />
        ))}
        <span className={styles.routeTrain} style={{ left: `${progress * 100}%` }}><Icon name="train" size={12} /></span>
      </div>
      <div className={styles.routeNames}>
        <span>Москва</span>
        <span>Санкт-Петербург</span>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Итог смены
// ---------------------------------------------------------------------------

function Summary({ cls, med, inspection, incidents, onAgain }) {
  const sum = useMemo(() => summarize({ cls, med, inspection, incidents }), [cls, med, inspection, incidents]);
  const rings = useMemo(() => ringsFor(sum, incidents), [sum, incidents]);
  const score = Math.round(((rings.procedure + rings.reaction + rings.quality) / 3) * 100);
  const [rank, setRank] = useState(null);
  const savedRef = useRef(false);

  useEffect(() => {
    if (savedRef.current) return;
    savedRef.current = true;
    saveShift({ cls: cls.key, score, rings, admitted: sum.admitted, upgrade: sum.upgrade ? sum.upgrade.key : null, safety: sum.safety, loyalty: sum.loyalty });
    api.getLeaderboard().then((d) => {
      if (d && d.me && d.total > 1) setRank(Math.round(((d.total - d.me.rank) / (d.total - 1)) * 100));
    }, () => {});
  }, [cls, score, rings, sum]);

  const mistakes = med.filter((m) => !m.best);

  return (
    <div className={styles.summary}>
      <div className={styles.summaryInner}>
        <p className={`${styles.eyebrow} rv`}>Вагон {cls.car} · {cls.title} · Москва — Санкт-Петербург</p>
        <SplitText as="h1" text="Смена завершена" className={styles.largeTitle} />

        <section className={`${styles.ringsCard} rv`} style={{ "--i": 1 }}>
          <Rings size={148} rings={[
            { value: rings.procedure, tone: "navy", label: "Регламент" },
            { value: rings.reaction, tone: "blue", label: "Реакция" },
            { value: rings.quality, tone: "sky", label: "Качество" }
          ]} />
          <dl className={styles.legend}>
            <div data-tone="navy"><dt>Регламент</dt><dd><CountUp value={Math.round(rings.procedure * 100)} />%</dd></div>
            <div data-tone="blue"><dt>Реакция</dt><dd><CountUp value={Math.round(rings.reaction * 100)} />%</dd></div>
            <div data-tone="sky"><dt>Качество</dt><dd><CountUp value={Math.round(rings.quality * 100)} />%</dd></div>
          </dl>
        </section>

        <section className={`${styles.hr} rv`} style={{ "--i": 2 }} data-ok={sum.admitted || undefined}>
          {sum.admitted && (
            <Medal shape="circle" finish="enamel" glyph="train" size={84} unlock backTitle="Смена без замечаний" backNote={`Вагон ${cls.car}`} />
          )}
          <div className={styles.hrText}>
            <p className={styles.hrLabel}>Решение для HR</p>
            <p className={styles.hrTitle}>{sum.admitted ? "Допуск к самостоятельной работе подтверждён" : "Нужна повторная смена"}</p>
            <p className={styles.hrNote}>
              {sum.admitted
                ? (sum.upgrade ? `Рекомендация: перевод в вагон класса «${sum.upgrade.title}».` : "Класс вагона сохраняется.")
                : sum.critical ? "В одном из диалогов — критическая ошибка безопасности." : sum.incidents.missed ? "Пассажир не дождался проводника." : !sum.honest ? "На медосмотре скрыты симптомы." : sum.inspection.found * 2 < sum.inspection.faults ? "Вагон принят с неисправностями." : "Итоговая безопасность ниже порога."}
            </p>
            <p className={styles.hrScore}>Балл смены <b className="num"><CountUp value={score} /></b>{rank !== null && <> · лучше, чем у {rank}% коллег</>}</p>
          </div>
        </section>

        <h2 className={styles.groupLabel}>Что произошло в рейсе</h2>
        <ul className={styles.group}>
          {incidents.map((i, n) => (
            <li key={i.key} className={`${styles.incRow} rv`} style={{ "--i": n + 3 }}>
              <span className={styles.incIcon} data-urgent={i.urgent || undefined}><Icon name={i.urgent ? "alert" : "hand"} size={16} /></span>
              <span className={styles.incText}>
                <b>{i.title}</b>
                <span>{i.x === "vestibule" ? "Тамбур" : `Место ${i.seat + 1}`} · {i.status === "done" ? i.result?.verdict : "Не подошли вовремя"}{i.fromInspection ? " · из-за пропуска на приёмке" : ""}</span>
                {i.result?.log && i.result.log.filter((l) => !l.best && l.note).map((l, k) => (
                  <span key={k} className={styles.incNote}>{l.note}</span>
                ))}
              </span>
              {i.status === "done" && i.result?.sessionId && (
                <a className={styles.incLink} href={`#/debrief/${i.result.sessionId}`}>Разбор<Icon name="chevronRight" size={14} /></a>
              )}
            </li>
          ))}
        </ul>

        <h2 className={styles.groupLabel}>Заступ и приёмка</h2>
        <ul className={styles.group}>
          <li className={styles.factRow}><span>Медосмотр и инструктаж</span><b className="num">{sum.med.correct}/{sum.med.total}</b></li>
          <li className={styles.factRow}><span>Неисправности найдены</span><b className="num">{sum.inspection.found}/{sum.inspection.faults}</b></li>
          {!sum.honest && <li className={styles.factNote}>{med.find((m) => m.step === "health")?.note}</li>}
          {mistakes.filter((m) => m.step !== "health").map((m) => <li key={m.step} className={styles.factNote}>{m.note}</li>)}
          {sum.inspection.missed.map((p) => <li key={p.key} className={styles.factNote}>Пропущено на приёмке: {p.fault.toLowerCase()}</li>)}
        </ul>

        <div className={styles.summaryActions}>
          <Button size="lg" className={styles.ctaBtn} onClick={onAgain}>Новая смена</Button>
          <Button size="lg" variant="secondary" className={styles.ctaBtn} as="a" href="#/today">На главную</Button>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function PhaseBar({ title, subtitle, onExit, right }) {
  return (
    <header className={styles.phaseBar}>
      <button type="button" className={styles.roundBtn} onClick={onExit} aria-label="Выйти из смены"><Icon name="x" size={16} /></button>
      <div className={styles.phaseText}>
        <p className={styles.phaseTitle}>{title}</p>
        <p className={styles.phaseSub}>{subtitle}</p>
      </div>
      {right || <span style={{ width: 36 }} />}
    </header>
  );
}

function clamp(v) { return Math.max(0, Math.min(100, v)); }
