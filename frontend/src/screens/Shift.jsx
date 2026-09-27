import { useEffect, useMemo, useRef, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import Icon from "../components/ui/Icon.jsx";
import Button from "../components/ui/Button.jsx";
import Rings from "../components/ui/Rings.jsx";
import Medal from "../components/awards/Medal.jsx";
import CarScene from "../components/shift/CarScene.jsx";
import MedRoom from "../components/shift/MedRoom.jsx";
import { TabBar } from "../components/ui/Chrome.jsx";
import ChatDialog from "../components/dialog/ChatDialog.jsx";
import useScenarioDialog from "../components/dialog/useScenarioDialog.js";
import useLocalDialog from "../components/dialog/useLocalDialog.js";
import { rankPhrase } from "../engagement.js";
import { getSettings } from "../settings.js";
import { STRESS_SCENARIOS, MED_CONDITIONS, rollCondition, shuffled, findStress } from "../shift/stressScenarios.js";
import { Person } from "../components/characters/People.jsx";
import { CountUp, SplitText } from "../components/motion/Motion.jsx";
import { buildPreShift } from "../shift/preShift.js";
import { CHAPTERS, readStory, recordChapter, chapterUnlocked, findChapter, randomPlan } from "../shift/modes.js";
import {
  CAR_CLASSES, CLASS_ORDER, STATIONS, VESTIBULE, rng, seatPassengers, planInspection, seatX, worldWidth,
  planIncidents, summarize, ringsFor, saveShift
} from "../shift/shiftModel.js";
import styles from "./Shift.module.css";

/**
 * «Смена проводника» — основной режим тренажёра.
 * setup (режим: сюжет / свободная / случайная) → brief (история и инструкция) → med (заступ:
 * медосмотр и инструктаж) → inspect (приёмка вагона) → trip (рейс Москва — Санкт-Петербург,
 * инциденты без предупреждения) → summary (итог и решение для HR).
 */
const TRIP_SECONDS = 140;
const INSPECT_SECONDS = 75;
const DWELL_MS = 3500;

const INSPECT_ICON = { extinguisher: "extinguisher", firstaid: "cross", hammer: "hammer", callbtn: "bell", socket: "bolt", table: "coffee" };
const SPEAKERS = {
  medic: { kind: "person", outfit: "medic", name: "Ирина Сергеевна", role: "Медработник, предрейсовый осмотр" },
  chief: { kind: "person", outfit: "chief", name: "Начальник поезда", role: "Инструктаж бригады" }
};

/** План свободной смены: вагон и самочувствие — от игрока, остальное — из настроек. */
function freePlan(clsKey, conditionMode) {
  const st = getSettings();
  return { cls: clsKey, condition: conditionMode, medRate: st.simMedProblemRate, stressCount: st.simStressCount, backendCount: 2, tripSeconds: st.simTripSeconds, patience: 1, notice: undefined };
}

export default function Shift({ route }) {
  const preset = String(route.segments[1] || "").toUpperCase();
  const [phase, setPhase] = useState("setup");
  const [mode, setMode] = useState(() => (route.query && route.query.mode) || "story");
  const [clsKey, setClsKey] = useState(CAR_CLASSES[preset] ? preset : "STANDARD");
  const [conditionMode, setConditionMode] = useState("random");
  const [plan, setPlan] = useState(() => freePlan(CAR_CLASSES[preset] ? preset : "STANDARD", "random"));
  const [chapter, setChapter] = useState(null);
  const [seed, setSeed] = useState(() => Date.now() % 100000);
  const cls = CAR_CLASSES[plan.cls] || CAR_CLASSES.STANDARD;
  const rand = useMemo(() => rng(seed), [seed, plan.cls]);
  const passengers = useMemo(() => seatPassengers(cls, rand), [rand, cls]);
  const [situations, setSituations] = useState(null);
  const [medLog, setMedLog] = useState([]);
  const [points, setPoints] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [condition, setCondition] = useState("fit");
  const [preSteps, setPreSteps] = useState(null);

  useEffect(() => {
    api.listScenarios().then((d) => setSituations(d.situations || []), () => setSituations([]));
  }, []);

  /** Старт: зафиксировать план, бросить кубики заступа и перейти к брифингу или медосмотру. */
  function begin(nextPlan, ch) {
    const nextSeed = Date.now() % 100000;
    const r = rng(nextSeed + 17);
    const cond = nextPlan.condition === "random" ? rollCondition(r, nextPlan.medRate ?? 0.25) : nextPlan.condition;
    setSeed(nextSeed);
    setPlan(nextPlan);
    setChapter(ch || null);
    setMedLog([]);
    setCondition(cond);
    setPreSteps(buildPreShift(CAR_CLASSES[nextPlan.cls], rng(nextSeed + 29), { notice: nextPlan.notice }));
    setPhase(nextPlan.brief ? "brief" : "med");
  }

  function startFree() { begin(freePlan(clsKey, conditionMode)); }
  function startChapter(ch) { begin({ ...ch.plan, brief: ch.brief }, ch); }
  function startRandom() { begin(randomPlan(rng(Date.now() % 100000 + 5))); }

  function toInspection() {
    setPoints(planInspection(cls, rand).map((p) => ({ ...p, icon: INSPECT_ICON[p.key] })));
    setPhase("inspect");
  }

  function toTrip(finalPoints) {
    setPoints(finalPoints);
    const extras = finalPoints.filter((p) => p.faulty && !p.checked && p.situationId).map((p) => p.situationId);
    const pool = passengers.length ? passengers : [{ seat: 0 }];
    const backend = planIncidents(situations || [], pool, rand, [...new Set(extras)]);
    const patience = plan.patience || 1;
    // Стрессовые эпизоды: первый — срочный (или заданный главой), остальные — любые, без повторов.
    const urgent = STRESS_SCENARIOS.filter((x) => x.urgent);
    // ?stress=<id> — принудительный сценарий для демонстрации (например, drunk-rowdy).
    const forced = (route.query && route.query.stress ? findStress(route.query.stress) : null) || (plan.forcedStress ? findStress(plan.forcedStress) : null);
    const first = forced || urgent[Math.floor(rand() * urgent.length)];
    const rest = STRESS_SCENARIOS.filter((x) => x.id !== first.id).sort(() => rand() - 0.5);
    const stressCount = Math.max(0, Math.min(3, plan.stressCount ?? 2));
    const taken = new Set(backend.map((i) => i.seat));
    const free = pool.filter((p) => !taken.has(p.seat));
    const stress = [first, rest[0], rest[1]].slice(0, stressCount).map((sc, i) => ({
      key: `st-${i}`,
      local: sc.id,
      title: sc.title,
      block: sc.block,
      blockLabel: sc.blockLabel,
      urgent: sc.urgent,
      mood: sc.mood,
      removal: !!sc.removal,
      look: sc.looks ? sc.looks[Math.floor(rand() * sc.looks.length)] : null,
      responders: sc.responders || null,
      seat: (free[i] || pool[(i + 3) % pool.length]).seat,
      x: sc.at === "vestibule" ? "vestibule" : undefined,
      status: "pending",
      remaining: Math.round((sc.urgent ? 35 : 50) * patience),
      result: null
    }));
    // Основные сценарии из каталога + всплывшие после приёмки; вперемешку со стрессовыми.
    const main = backend.filter((i) => !i.fromInspection).slice(0, Math.max(0, Math.min(3, plan.backendCount ?? 2))).map((i) => ({ ...i, remaining: Math.round(i.remaining * patience) }));
    const extra = backend.filter((i) => i.fromInspection);
    const order = [main[0], stress[0], main[1], stress[1], main[2], stress[2], ...extra].filter(Boolean);
    const at = [0.07, 0.2, 0.34, 0.48, 0.6, 0.72, 0.82, 0.9];
    setIncidents(order.map((i, n) => ({ ...i, spawnAt: at[n] ?? 0.92 })));
    setPhase("trip");
  }

  function finish(finalIncidents) {
    setIncidents(finalIncidents);
    setPhase("summary");
  }

  const exit = () => { setPhase("setup"); };

  if (phase === "setup") {
    return (
      <Setup
        mode={mode} setMode={setMode}
        clsKey={clsKey} setClsKey={setClsKey}
        conditionMode={conditionMode} setConditionMode={setConditionMode}
        onStartFree={startFree} onStartChapter={startChapter} onStartRandom={startRandom}
        ready={situations !== null}
      />
    );
  }
  if (phase === "brief") return <Briefing plan={plan} cls={cls} chapter={chapter} onGo={() => setPhase("med")} onExit={exit} />;
  if (phase === "med") return <MedCheck cls={cls} condition={condition} steps={preSteps} onDone={(log, admitted) => { setMedLog(log); if (admitted) toInspection(); else setPhase("rejected"); }} onExit={exit} />;
  if (phase === "rejected") return <NotAdmitted cls={cls} condition={condition} med={medLog} chapter={chapter} onAgain={() => setPhase("setup")} />;
  if (phase === "inspect") return <Inspection cls={cls} passengers={passengers} points={points} onDone={toTrip} onExit={exit} />;
  if (phase === "trip") return <Trip cls={cls} passengers={passengers} initial={incidents} tripSeconds={plan.tripSeconds || TRIP_SECONDS} onDone={finish} onExit={exit} />;
  return <Summary cls={cls} med={medLog} inspection={points} incidents={incidents} chapter={chapter} onAgain={() => setPhase("setup")} onNext={(ch) => startChapter(ch)} />;
}

// ---------------------------------------------------------------------------
// Выбор режима и вагона — панель выезжает справа
// ---------------------------------------------------------------------------

const MODES = [
  ["story", "Сюжет", "flag"],
  ["free", "Свободная", "train"],
  ["random", "Случайный рейс", "shuffle"]
];

function Setup({ mode, setMode, clsKey, setClsKey, conditionMode, setConditionMode, onStartFree, onStartChapter, onStartRandom, ready }) {
  const story = readStory();
  const nextIndex = CHAPTERS.findIndex((c, i) => chapterUnlocked(story, i) && !(story[c.id] && story[c.id].passed));
  const [chapterId, setChapterId] = useState(() => (CHAPTERS[nextIndex === -1 ? CHAPTERS.length - 1 : nextIndex]).id);
  const chapter = findChapter(chapterId);
  const passedCount = CHAPTERS.filter((c) => story[c.id] && story[c.id].passed).length;

  let cta;
  if (mode === "story") cta = <Button size="lg" className={styles.ctaBtn} onClick={() => onStartChapter(chapter)} disabled={!ready}>{ready ? `Начать: «${chapter.title}»` : "Готовим рейс…"}</Button>;
  else if (mode === "random") cta = <Button size="lg" className={styles.ctaBtn} onClick={onStartRandom} disabled={!ready}>{ready ? "Бросить кубики и начать" : "Готовим рейс…"}</Button>;
  else cta = <Button size="lg" className={styles.ctaBtn} onClick={onStartFree} disabled={!ready}>{ready ? "Начать смену" : "Готовим рейс…"}</Button>;

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
      <div className={styles.setupBody}>
        <h1 className={styles.largeTitle}>Новая смена</h1>
        <p className={styles.lead}>Рейс Москва — Санкт-Петербург. Что случится в пути, заранее не известно.</p>

        <div className={`${styles.segmented} ${styles.modeSeg}`} role="radiogroup" aria-label="Режим смены">
          {MODES.map(([k, label, icon]) => (
            <button key={k} type="button" role="radio" aria-checked={mode === k} data-on={mode === k || undefined} onClick={() => setMode(k)}>
              <Icon name={icon} size={14} />{label}
            </button>
          ))}
        </div>

        {mode === "story" && (
          <>
            <h2 className={styles.groupLabel}>Карьера проводника · пройдено {passedCount} из {CHAPTERS.length}</h2>
            <ol className={styles.chapters}>
              {CHAPTERS.map((c, i) => {
                const open = chapterUnlocked(story, i);
                const st = story[c.id];
                const on = c.id === chapterId;
                return (
                  <li key={c.id}>
                    <button type="button" className={styles.chapter} data-on={on || undefined} disabled={!open} onClick={() => setChapterId(c.id)} aria-pressed={on}>
                      <span className={styles.chapterNum} data-done={st && st.passed ? true : undefined}>{st && st.passed ? <Icon name="check" size={14} /> : open ? i + 1 : <Icon name="lock" size={13} />}</span>
                      <span className={styles.carText}>
                        <span className={styles.carTitle}>{c.title}</span>
                        <span className={styles.carNote}>{open ? `${c.subtitle} · вагон «${CAR_CLASSES[c.plan.cls].title}»` : "Откроется после допуска в предыдущей главе"}</span>
                      </span>
                      {st && st.best ? <span className={styles.chapterBest}>{st.best}</span> : null}
                    </button>
                  </li>
                );
              })}
            </ol>
            <p className={styles.groupNote}>{chapter.brief.story}</p>
          </>
        )}

        {mode === "random" && (
          <>
            <h2 className={styles.groupLabel}>Полная случайность</h2>
            <ul className={styles.steps}>
              <li><span className={styles.stepIcon}><Icon name="shuffle" size={18} /></span><span><b>Всё решает случай</b>Вагон, самочувствие на заступе, особое указание, темп и число сложных ситуаций</span></li>
              <li><span className={styles.stepIcon}><Icon name="clipboard" size={18} /></span><span><b>Короткая история перед сменой</b>Начальник поезда расскажет, что за рейс и что от вас ждут</span></li>
              <li><span className={styles.stepIcon}><Icon name="flag" size={18} /></span><span><b>Оценка — как обычно</b>Допуск по итогам всей смены</span></li>
            </ul>
          </>
        )}

        {mode === "free" && (
          <>
            <h2 className={styles.groupLabel}>Вагон по наряду</h2>
            <ul className={styles.group} role="radiogroup" aria-label="Класс вагона">
              {CLASS_ORDER.map((k) => {
                const c = CAR_CLASSES[k];
                const on = k === clsKey;
                return (
                  <li key={k}>
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
            <p className={styles.groupNote}>Длительность рейса, число сложных ситуаций и частота проблем на медосмотре — в <a href="#/settings">настройках</a>.</p>
          </>
        )}

        <h2 className={styles.groupLabel}>Порядок смены</h2>
        <ol className={styles.steps}>
          <li><span className={styles.stepIcon}><Icon name="stethoscope" size={18} /></span><span><b>Заступ</b>Медосмотр и инструктаж — каждый раз немного по-разному</span></li>
          <li><span className={styles.stepIcon}><Icon name="clipboard" size={18} /></span><span><b>Приёмка вагона</b>{INSPECT_SECONDS} секунд, чтобы найти неисправности</span></li>
          <li><span className={styles.stepIcon}><Icon name="train" size={18} /></span><span><b>Рейс</b>Пассажиры позовут сами — подходите вовремя</span></li>
          <li><span className={styles.stepIcon}><Icon name="flag" size={18} /></span><span><b>Итог</b>Оценивается вся смена, а не отдельный ответ</span></li>
        </ol>
      </div>
      <div className={styles.cta}>{cta}</div>
    </div>
    {/* На телефоне — обычный таб-бар: из выбора смены можно уйти в любой раздел. */}
    <TabBar activeScreen="shift" />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Брифинг перед сменой: история и инструкция (сюжет и случайный рейс)
// ---------------------------------------------------------------------------

function Briefing({ plan, cls, chapter, onGo, onExit }) {
  const b = plan.brief;
  return (
    <div className={styles.briefWrap}>
      <div className={styles.briefCard} role="dialog" aria-labelledby="brief-title">
        <div className={styles.briefHead}>
          <span className={styles.briefChief} aria-hidden="true"><Person outfit="chief" hair={2} size={190} /></span>
          <div>
            <p className={styles.eyebrow}>{chapter ? `Глава ${CHAPTERS.indexOf(chapter) + 1} · ${chapter.title}` : "Случайный рейс"}</p>
            <h1 id="brief-title" className={styles.briefTitle}>{chapter ? chapter.subtitle : `Вагон ${cls.car} · ${cls.title}`}</h1>
          </div>
        </div>
        <p className={styles.briefStory}>{b.story}</p>
        <h2 className={styles.groupLabel}>Инструкция на смену</h2>
        <ol className={styles.briefTasks}>
          {b.tasks.map((t) => <li key={t}>{t}</li>)}
        </ol>
        <dl className={styles.briefFacts}>
          <div><dt>Вагон</dt><dd>{cls.car} · {cls.title}</dd></div>
          <div><dt>Сложных ситуаций</dt><dd>{plan.stressCount === 0 ? "нет" : plan.stressCount}</dd></div>
          <div><dt>Темп</dt><dd>{plan.pace || (plan.patience > 1.1 ? "Спокойный" : plan.patience < 0.95 ? "Напряжённый" : "Обычный")}</dd></div>
        </dl>
        <p className={styles.briefGoal}><Icon name="flag" size={16} />Цель: {b.goal}</p>
        <div className={styles.briefActions}>
          <Button size="lg" className={styles.ctaBtn} onClick={onGo}>На медосмотр</Button>
          <Button size="lg" variant="ghost" className={styles.ctaBtn} onClick={onExit}>Назад к выбору</Button>
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

function MedCheck({ cls, condition, steps, onDone, onExit }) {
  const cond = condition !== "fit" ? MED_CONDITIONS[condition] : null;
  const script = useMemo(() => (cond
    ? cond.steps.map((st, i) => ({ id: `${condition}-${i}`, speaker: st.speaker, text: st.text, context: i === 0 ? `${cond.context} ${cond.measure}.` : undefined, choices: st.choices }))
    : steps), [cond, condition, steps]);
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
        <MedRoom
          place={cur.speaker === "chief" ? "briefing" : "medpoint"}
          npc={cur.speaker}
          heroTalking={busy}
          npcTalking={waiting}
          readout={cond ? { icon: condition === "fever" ? "thermometer" : condition === "alcohol" ? "alert" : "stethoscope", text: shortMeasure(condition, cond.measure), bad: true } : null}
        />
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
function NotAdmitted({ cls, condition, med, chapter, onAgain }) {
  const cond = MED_CONDITIONS[condition];
  const critical = med.some((m) => m.critical);
  const honest = med.every((m) => m.best);
  const savedRef = useRef(false);
  useEffect(() => {
    if (savedRef.current) return;
    savedRef.current = true;
    saveShift({ cls: cls.key, score: 0, rings: { procedure: honest ? 1 : 0, reaction: 0, quality: 0 }, admitted: false, rejected: condition, safety: med.reduce((a, m) => a + m.safety, 0), loyalty: 0 });
    if (chapter) recordChapter(chapter.id, false, 0);
  }, [cls, condition, honest, med, chapter]);
  return (
    <div className={styles.summary}>
      <div className={styles.summaryInner}>
        <p className={`${styles.eyebrow} rv`}>Заступ на смену · Вагон {cls.car}</p>
        <SplitText as="h1" text="Не допущен к смене" className={styles.largeTitle} />
<div className={styles.sumLeft}>
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
          <p className={styles.verdictTitle}>{critical ? "Серьёзное нарушение порядка медосмотра" : honest ? "Вы действовали правильно" : "Есть что улучшить"}</p>
          <p className={styles.verdictNote}>{critical
            ? "Попытка скрыть состояние, подделать результат или выйти на смену вопреки решению медработника — повод для служебного расследования."
            : "Недопуск — не провал тренировки. Проводник, который честно сообщает о своём состоянии, защищает пассажиров и бригаду."}</p>
        </section>
</div>
<div className={styles.sumRight}>
        <h2 className={styles.groupLabel}>Разбор</h2>
        <ul className={styles.group}>
          {med.map((m, i) => (
            <li key={i} className={styles.factNote} data-best={m.best || undefined}>
              <b className={styles.noteMark}>{m.best ? "Верно" : m.critical ? "Серьёзная ошибка" : "Ошибка"}</b> {m.note}
            </li>
          ))}
        </ul>
</div>
        <div className={styles.summaryActions}>
          <Button size="lg" className={styles.ctaBtn} onClick={onAgain}>К выбору смены</Button>
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

const SPEEDS = [1, 2, 4];
const ROLE_OFFSET = { guard: 46, police: -46, medic: -92, chief: 46 };
const ROLE_NAME = { guard: "Охрана поезда", police: "Наряд транспортной полиции", medic: "Бригада скорой помощи", chief: "Начальник поезда" };

function Trip({ cls, passengers, initial, tripSeconds, onDone, onExit }) {
  const W = worldWidth(cls);
  const doorOf = (x) => (x > W / 2 ? W - 60 : 60);
  const [incidents, setIncidents] = useState(initial);
  // Пассажиры в креслах: у мест будущих стрессовых ситуаций — «проблемная» внешность по референсу.
  const [crew, setCrew] = useState(() => {
    const byLook = new Map(initial.filter((i) => i.look != null && i.x !== "vestibule").map((i) => [i.seat, i.look]));
    return passengers.map((p) => (byLook.has(p.seat) ? { ...p, variant: byLook.get(p.seat), kid: false, phone: false } : p));
  });
  const [walkers, setWalkers] = useState([]);
  const [visits, setVisits] = useState([]);
  const [speed, setSpeed] = useState(() => getSettings().tripSpeed || 1);
  const [progress, setProgress] = useState(0);
  const [stop, setStop] = useState({ name: STATIONS[0].name, until: Date.now() + 2500 });
  const [paused, setPaused] = useState(false);
  const [talk, setTalk] = useState(null); // ключ инцидента в диалоге
  const [toast, setToast] = useState(null);
  const [finished, setFinished] = useState(false);
  const passedRef = useRef(new Set([0]));
  const incRef = useRef(incidents);
  incRef.current = incidents;
  const crewRef = useRef(crew);
  crewRef.current = crew;
  const visitsRef = useRef(visits);
  visitsRef.current = visits;
  const seq = useRef(0);
  const nextKey = (p) => { seq.current += 1; return `${p}${seq.current}`; };

  const talkInc = incidents.find((i) => i.key === talk) || null;
  const talkPassenger = talkInc ? crew.find((p) => p.seat === talkInc.seat) : null;
  const talkSpeaker = talkInc
    ? { kind: "passenger", variant: talkPassenger ? talkPassenger.variant : 3, mood: talkInc.mood || "calm", name: talkInc.x === "vestibule" ? "Пассажир в тамбуре" : `Пассажир, место ${talkInc.seat + 1}`, role: `Вагон ${cls.car} · ${cls.title}` }
    : { kind: "passenger", variant: 0, name: "", role: "" };

  /** Сотрудники подходят к месту: из ближайшего тамбура, встают по обе стороны от кресла. */
  function callResponders(visitKey, seat, roles, x) {
    const target = x != null ? x : seatX(cls, seat);
    setWalkers((w) => [...w, ...roles.map((role) => ({ key: nextKey(role), visit: visitKey, role, from: doorOf(target), to: target + (ROLE_OFFSET[role] || 40), stay: true, faceRight: (ROLE_OFFSET[role] || 40) < 0 }))]);
  }

  const finishIncident = (result) => {
    const key = talkRef.current;
    const inc = incRef.current.find((i) => i.key === key);
    setIncidents((list) => list.map((i) => (i.key === key ? { ...i, status: "done", result } : i)));
    if (!inc || !inc.responders) return;
    const critical = result.verdict === "Критическая ошибка безопасности";
    const x = inc.x === "vestibule" ? W - VESTIBULE * 0.62 : null;
    const visit = { key: `v-${inc.key}`, seat: inc.seat, x, station: inc.responders.station || [], removal: !!inc.removal && !critical, state: "onboard", mood: inc.mood };
    setVisits((v) => [...v, visit]);
    if (inc.responders.onboard && inc.responders.onboard.length) {
      callResponders(visit.key, inc.seat, inc.responders.onboard, x);
      setToast({ text: `${ROLE_NAME[inc.responders.onboard[0]]} идёт к месту ${inc.seat + 1}`, icon: "shield", at: Date.now() });
    }
    if (visit.station.length) {
      setToast({ text: `${visit.station.map((r) => ROLE_NAME[r]).join(" и ")} встретят поезд на ближайшей станции`, icon: "flag", at: Date.now() + 1 });
    }
  };
  const remoteDialog = useScenarioDialog({ speaker: talkSpeaker, onDone: finishIncident });
  const localDialog = useLocalDialog({ speaker: talkSpeaker, onDone: finishIncident });
  const dialog = talkInc && talkInc.local ? localDialog : remoteDialog;
  const talkRef = useRef(null);
  talkRef.current = talk;

  const frozen = paused || talk !== null;

  /** Стоянка: сотрудники на станции входят в вагон; иногда пассажиры выходят и заходят. */
  function arriveAtStation(last) {
    // Вызванные службы
    const pending = visitsRef.current.filter((v) => v.state === "onboard" && v.station.length);
    if (pending.length) {
      setVisits((vs) => vs.map((v) => (pending.some((p) => p.key === v.key) ? { ...v, state: "station" } : v)));
      pending.forEach((v) => callResponders(v.key, v.seat, v.station, v.x));
    }
    if (last) return;
    // Смена пассажиров — не на каждой станции.
    if (Math.random() < 0.3) return;
    const busy = new Set([
      ...incRef.current.filter((i) => i.status !== "done" && i.status !== "missed").map((i) => i.seat),
      ...visitsRef.current.filter((v) => v.state !== "done").map((v) => v.seat)
    ]);
    const seated = crewRef.current.filter((p) => !busy.has(p.seat));
    const leaving = shuffle(seated).slice(0, Math.floor(Math.random() * 3));
    const occupied = new Set(crewRef.current.map((p) => p.seat));
    const empty = [];
    for (let i = 0; i < cls.seats; i += 1) if (!occupied.has(i) && !busy.has(i)) empty.push(i);
    const boarding = shuffle(empty.concat(leaving.map((p) => p.seat))).slice(0, Math.floor(Math.random() * 3));
    if (!leaving.length && !boarding.length) return;
    setCrew((c) => c.filter((p) => !leaving.some((l) => l.seat === p.seat)));
    setWalkers((w) => [
      ...w,
      ...leaving.map((p) => ({ key: nextKey("out"), role: "passenger", variant: p.variant, from: seatX(cls, p.seat), to: doorOf(seatX(cls, p.seat)), vanish: true })),
      ...boarding.map((seat, n) => ({ key: nextKey("in"), role: "passenger", variant: Math.floor(Math.random() * 40), from: doorOf(seatX(cls, seat)), to: seatX(cls, seat), board: seat, delay: 900 + n * 700 }))
    ]);
    const parts = [];
    if (leaving.length) parts.push(`вышли ${leaving.length}`);
    if (boarding.length) parts.push(`вошли ${boarding.length}`);
    setToast({ text: `Посадка: ${parts.join(", ")}`, icon: "users", at: Date.now() + 2 });
  }

  /** Отправление: службы уходят, при удалении из поезда пассажир выходит вместе с ними. */
  function departStation() {
    const leavingVisits = visitsRef.current.filter((v) => v.state === "station");
    if (!leavingVisits.length) return;
    setVisits((vs) => vs.map((v) => (leavingVisits.some((l) => l.key === v.key) ? { ...v, state: "done" } : v)));
    setWalkers((ws) => {
      const out = [];
      const keep = [];
      ws.forEach((w) => {
        const v = leavingVisits.find((l) => l.key === w.visit);
        if (v) out.push({ ...w, key: nextKey("go"), from: w.to, to: doorOf(w.to), stay: false, vanish: true });
        else keep.push(w);
      });
      leavingVisits.filter((v) => v.removal).forEach((v) => {
        const p = crewRef.current.find((c) => c.seat === v.seat);
        if (p) out.push({ key: nextKey("rm"), role: "passenger", variant: p.variant, mood: v.mood === "drunk" ? "drunk" : undefined, from: seatX(cls, v.seat), to: doorOf(seatX(cls, v.seat)), vanish: true });
      });
      return [...keep, ...out];
    });
    const removed = leavingVisits.filter((v) => v.removal).map((v) => v.seat);
    if (removed.length) setCrew((c) => c.filter((p) => !removed.includes(p.seat)));
  }

  function onWalkerArrive(w) {
    if (w.vanish) setWalkers((ws) => ws.filter((x) => x.key !== w.key));
    else if (w.board != null) {
      setWalkers((ws) => ws.filter((x) => x.key !== w.key));
      setCrew((c) => (c.some((p) => p.seat === w.board) ? c : [...c, { seat: w.board, variant: w.variant, kid: false, phone: Math.random() < 0.3 }]));
    }
  }

  const arriveRef = useRef(arriveAtStation);
  arriveRef.current = arriveAtStation;
  const departRef = useRef(departStation);
  departRef.current = departStation;

  // Главный цикл рейса: 4 тика в секунду; скорость рейса — 1×, 2× или 4×.
  useEffect(() => {
    const TICK = 250;
    const t = window.setInterval(() => {
      if (frozen) return;
      const now = Date.now();
      const atStation = stop && now < stop.until;
      if (stop && now >= stop.until && stop.name !== STATIONS[STATIONS.length - 1].name) {
        setStop(null);
        departRef.current();
      }

      if (!atStation) {
        setProgress((p) => {
          if (p >= 1) return 1;
          const np = Math.min(1, p + (TICK / 1000 / (tripSeconds || TRIP_SECONDS)) * speed);
          STATIONS.forEach((st, idx) => {
            if (idx > 0 && !passedRef.current.has(idx) && np >= st.at) {
              passedRef.current.add(idx);
              const last = idx === STATIONS.length - 1;
              setStop({ name: st.name, until: last ? Infinity : now + Math.max(DWELL_MS / speed, 2600) });
              setToast({ text: last ? `Прибытие: ${st.name}` : `Стоянка: ${st.name}`, icon: "flag", at: now });
              window.setTimeout(() => arriveRef.current(last), 0);
            }
          });
          return np;
        });
      }

      // Инциденты: появление и таймер ожидания пассажира (идёт в реальном времени и на стоянках).
      setIncidents((list) => {
        let changed = false;
        const next = list.map((i) => {
          if (i.status === "pending" && progressRef.current >= i.spawnAt) {
            changed = true;
            setToast({ text: i.urgent ? `Срочный вызов: ${i.x === "vestibule" ? "тамбур" : `место ${i.seat + 1}`}` : `Пассажир зовёт: место ${i.seat + 1}`, icon: i.urgent ? "alert" : "hand", urgent: i.urgent, at: now });
            try { if (getSettings().haptics && navigator.vibrate) navigator.vibrate(i.urgent ? [60, 40, 60] : 40); } catch (e) { /* нет API */ }
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
  }, [frozen, stop, speed]);

  const progressRef = useRef(0);
  progressRef.current = progress;

  // Конец рейса: прибыли, все вызовы закрыты или пропущены.
  useEffect(() => {
    if (finished || progress < 1 || talk !== null) return undefined;
    const open = incidents.some((i) => i.status === "pending" || i.status === "active");
    if (open) return undefined;
    const t = window.setTimeout(() => { setFinished(true); onDone(incRef.current); }, 2600);
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

  function closeDialog() {
    setTalk(null);
  }

  const signals = incidents.filter((i) => i.status === "active").map((i) => ({ key: i.key, seat: i.seat, x: i.x, urgent: i.urgent, remaining: i.remaining, total: i.total }));
  const moods = {};
  incidents.forEach((i) => {
    if (!i.mood || i.x === "vestibule") return;
    const visiting = visits.some((v) => v.seat === i.seat && v.state !== "done");
    if (i.status === "active" || i.status === "talking" || i.status === "pending" && i.look != null || visiting) moods[i.seat] = i.mood;
  });
  const moving = !(stop && Date.now() < stop.until) && progress < 1;
  const doneCount = incidents.filter((i) => i.status === "done").length;

  return (
    <div className={styles.stage}>
      <header className={styles.tripBar}>
        <button type="button" className={styles.roundBtn} onClick={() => setPaused(true)} aria-label="Пауза"><Icon name="pause" size={16} /></button>
        <RouteLine progress={progress} />
        <button
          type="button"
          className={styles.speedBtn}
          onClick={() => setSpeed((v) => SPEEDS[(SPEEDS.indexOf(v) + 1) % SPEEDS.length])}
          aria-label={`Скорость рейса ${speed}×, нажмите, чтобы изменить`}
          data-fast={speed > 1 || undefined}
        >
          <Icon name="bolt" size={13} />{speed}×
        </button>
        <span className={styles.counter} title="Обработано вызовов"><Icon name="hand" size={14} />{doneCount}</span>
      </header>

      <div className={styles.scene}>
        <CarScene
          cls={cls}
          passengers={crew}
          moods={moods}
          walkers={walkers}
          onWalkerArrive={onWalkerArrive}
          signals={signals}
          moving={moving}
          speed={speed}
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
                : dialog.phase === "error" ? <Button size="lg" variant="secondary" className={styles.ctaBtn} onClick={() => { setIncidents((l) => l.map((i) => (i.key === talk ? { ...i, status: "done", result: { safety: 0, loyalty: 0, verdict: "Не удалось сохранить результат" } } : i))); closeDialog(); }}>Нет связи — продолжить смену</Button>
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

function shuffle(list) {
  const a = list.slice();
  for (let i = a.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
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

function Summary({ cls, med, inspection, incidents, chapter, onAgain, onNext }) {
  const sum = useMemo(() => summarize({ cls, med, inspection, incidents }), [cls, med, inspection, incidents]);
  const rings = useMemo(() => ringsFor(sum, incidents), [sum, incidents]);
  const score = Math.round(((rings.procedure + rings.reaction + rings.quality) / 3) * 100);
  const [rank, setRank] = useState(null);
  const savedRef = useRef(false);

  useEffect(() => {
    if (savedRef.current) return;
    savedRef.current = true;
    saveShift({ cls: cls.key, score, rings, admitted: sum.admitted, upgrade: sum.upgrade ? sum.upgrade.key : null, safety: sum.safety, loyalty: sum.loyalty });
    if (chapter) recordChapter(chapter.id, sum.admitted, score);
    api.getLeaderboard().then((d) => {
      if (d && d.me) setRank(rankPhrase(d.me.rank, d.total));
    }, () => {});
  }, [cls, score, rings, sum, chapter]);

  const mistakes = med.filter((m) => !m.best);
  const nextChapter = chapter ? CHAPTERS[CHAPTERS.indexOf(chapter) + 1] || null : null;

  return (
    <div className={styles.summary}>
      <div className={styles.summaryInner}>
        <p className={`${styles.eyebrow} rv`}>Вагон {cls.car} · {cls.title} · Москва — Санкт-Петербург</p>
        <SplitText as="h1" text="Смена завершена" className={styles.largeTitle} />

<div className={styles.sumLeft}>
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
                : sum.critical ? "В одном из диалогов — критическая ошибка безопасности." : sum.incidents.missed ? "Пассажир не дождался проводника." : !sum.honest ? "На медосмотре — ответ не по регламенту." : sum.inspection.found * 2 < sum.inspection.faults ? "Вагон принят с неисправностями." : "Итоговая безопасность ниже порога."}
            </p>
            <p className={styles.hrScore}>Балл смены <b className="num"><CountUp value={score} /></b>{rank && <> · {rank.short.startsWith("Топ") ? `${rank.short} рейтинга` : `место ${rank.short}`}</>}</p>
          </div>
        </section>

</div>
<div className={styles.sumRight}>
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

</div>
        {chapter && (
          <section className={`${styles.chapterResult} rv`}>
            <p className={styles.hrLabel}>Глава {CHAPTERS.indexOf(chapter) + 1} · {chapter.title}</p>
            <p className={styles.hrTitle}>{sum.admitted ? (nextChapter ? `Глава пройдена. Открыта следующая: «${nextChapter.title}»` : "Все главы пройдены — карьера проводника завершена") : "Глава не пройдена — нужен допуск по итогам смены"}</p>
            <p className={styles.hrNote}>Цель главы: {chapter.brief.goal}.</p>
          </section>
        )}
        <div className={styles.summaryActions}>
          {chapter && sum.admitted && nextChapter
            ? <Button size="lg" className={styles.ctaBtn} onClick={() => onNext(nextChapter)}>Следующая глава</Button>
            : chapter
              ? <Button size="lg" className={styles.ctaBtn} onClick={() => onNext(chapter)}>{sum.admitted ? "Пройти главу ещё раз" : "Попробовать ещё раз"}</Button>
              : <Button size="lg" className={styles.ctaBtn} onClick={onAgain}>Новая смена</Button>}
          <Button size="lg" variant="secondary" className={styles.ctaBtn} onClick={onAgain}>К выбору смены</Button>
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

/** Короткое показание для дисплея прибора: «37,9 °C», «0,21 мг/л», «ПАВ +». */
function shortMeasure(condition, measure) {
  if (condition === "substances") return "ПАВ +";
  const m = String(measure).match(/(\d+[,.]\d+\s*(°C|мг\/л))/);
  return m ? m[1] : String(measure);
}

function clamp(v) { return Math.max(0, Math.min(100, v)); }
