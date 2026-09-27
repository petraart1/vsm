import { useEffect, useRef, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import Medal from "../components/awards/Medal.jsx";
import ChatDialog from "../components/dialog/ChatDialog.jsx";
import useScenarioDialog from "../components/dialog/useScenarioDialog.js";
import { SeatedPerson, Person } from "../components/characters/People.jsx";
import { CAR_CLASSES, CLASS_ORDER } from "../shift/shiftModel.js";
import { blockTitle, BLOCK_ICON } from "../progress.js";
import styles from "./Exam.module.css";

/**
 * Режим экзамена: N ситуаций из разных блоков подряд, без подсказок; единая оценка в конце.
 * Маршруты: #/exam — вводная и настройка; #/exam/:id — прохождение и итог.
 * props: route (сегменты ["exam", id?])
 */

const LS_KEY = "reactlab.exam.current.v1";
const SIZES = [5, 10];

export const GRADES = {
  EXCELLENT: { label: "Отлично", bonus: 300, level: 1, note: "Решения соответствуют регламенту, пассажиры получили внимание. Достижение «Сертификат» добавлено в профиль." },
  GOOD: { label: "Хорошо", bonus: 150, level: 0.75, note: "Уверенный результат. Разбор отдельных ситуаций поможет закрепить навык." },
  SATISFACTORY: { label: "Удовлетворительно", bonus: 50, level: 0.5, note: "Базовые требования выполнены. Стоит повторить темы, отмеченные ниже." },
  UNSATISFACTORY: { label: "Неудовлетворительно", bonus: 0, level: 0, note: "В этот раз экзамен не сдан. Разберите ситуации и повторите попытку, когда будете готовы." }
};
const GRADE_ORDER = ["EXCELLENT", "GOOD", "SATISFACTORY", "UNSATISFACTORY"];

const OUTCOMES = {
  SUCCESS: { label: "Решено", level: 1 },
  PARTIAL: { label: "Частично", level: 0.5 },
  FAILURE: { label: "Не решено", level: 0 }
};

function readCurrent() {
  try { return window.localStorage.getItem(LS_KEY) || null; } catch { return null; }
}
function saveCurrent(id) {
  try { window.localStorage.setItem(LS_KEY, String(id)); } catch { /* недоступно — не критично */ }
}
function clearCurrent(id) {
  try { if (!id || window.localStorage.getItem(LS_KEY) === String(id)) window.localStorage.removeItem(LS_KEY); } catch { /* ignore */ }
}

const isDone = (e) => !!e && (e.status === "COMPLETED" || e.currentIndex >= e.size);
const plural = (n, one, few, many) => {
  const m10 = n % 10, m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 10 || m100 >= 20)) return few;
  return many;
};

/** Круг с заполненностью (как VerdictMark): 1 — полный, 0.75, 0.5, 0 — пустой с чертой. */
export function GradeMark({ level, size = 18, className }) {
  const r = 7;
  let fill = null;
  if (level >= 1) fill = <circle cx="9" cy="9" r={r} className={styles.markFill} />;
  else if (level > 0) {
    const a = level * 2 * Math.PI;
    const x = 9 + r * Math.sin(a), y = 9 - r * Math.cos(a);
    fill = <path d={`M9 9 L9 2 A${r} ${r} 0 ${level > 0.5 ? 1 : 0} 1 ${x.toFixed(3)} ${y.toFixed(3)} Z`} className={styles.markFill} />;
  }
  return (
    <svg viewBox="0 0 18 18" width={size} height={size} aria-hidden="true" className={[styles.mark, className].filter(Boolean).join(" ")} data-level={String(level)}>
      <circle cx="9" cy="9" r={r} className={styles.markRing} />
      {fill}
      {level === 0 && <path d="M4.6 13.4 L13.4 4.6" className={styles.markSlash} />}
      <circle cx="9" cy="9" r={r} className={styles.markEdge} />
    </svg>
  );
}

export default function Exam({ route }) {
  const examId = route && route.segments ? route.segments[1] : null;
  return examId ? <ExamRun key={examId} examId={decodeURIComponent(examId)} /> : <ExamIntro />;
}

/* ============================ Вводная ============================ */

function ExamIntro() {
  const [clsKey, setClsKey] = useState("STANDARD");
  const [size, setSize] = useState(5);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(false);
  const [resume, setResume] = useState(null);

  useEffect(() => {
    const id = readCurrent();
    if (!id) return;
    let alive = true;
    api.getExam(id).then(
      (e) => { if (!alive) return; if (isDone(e)) { clearCurrent(id); setResume(null); } else setResume(e); },
      () => { if (alive) clearCurrent(id); }
    );
    return () => { alive = false; };
  }, []);

  function begin() {
    if (creating) return;
    setCreating(true);
    setError(false);
    api.createExam({ carClass: clsKey, size }).then(
      (e) => {
        if (!e || !e.examId) { setCreating(false); setError(true); return; }
        saveCurrent(e.examId);
        navigate(`/exam/${encodeURIComponent(e.examId)}`);
      },
      () => { setCreating(false); setError(true); }
    );
  }

  return (
    <div className={styles.shell}>
      <header className={styles.bar}>
        <a className={styles.round} href="#/scenarios" aria-label="К тренировкам"><Icon name="chevronLeft" size={16} /></a>
        <span className={styles.barTitle}>Экзамен</span>
        <span className={styles.barSpacer} />
      </header>

      <div className={styles.scroll}>
        <div className={styles.page}>
          <section className={`${styles.hero} rv`}>
            <span className={styles.heroIcon} aria-hidden="true"><Icon name="clipboard" size={26} /></span>
            <p className={styles.kicker}>Режим экзамена</p>
            <h1 className={styles.heroTitle}>Проверка без подсказок</h1>
            <p className={styles.heroNote}>Несколько ситуаций из разных блоков подряд — как в реальном рейсе. Шкалы и разбор откроются только в конце, вместе с итоговой оценкой.</p>
          </section>

          {resume && (
            <section className={`${styles.resume} rv`} style={{ "--i": 1 }}>
              <div className={styles.resumeText}>
                <p className={styles.resumeTitle}>Незавершённый экзамен</p>
                <p className={styles.resumeMeta}>Пройдено {Math.min(resume.currentIndex, resume.size)} из {resume.size}</p>
              </div>
              <Button onClick={() => navigate(`/exam/${encodeURIComponent(resume.examId)}`)}><Icon name="play" size={14} />Продолжить экзамен</Button>
            </section>
          )}

          <div className={styles.introGrid}>
            <section className={`${styles.panel} rv`} style={{ "--i": 2 }} aria-labelledby="exam-rules">
              <h2 id="exam-rules" className={styles.h2}>Правила</h2>
              <ul className={styles.rules}>
                <Rule icon="eye" title="Без подсказок">Шкалы безопасности и лояльности, влияние решений и разбор скрыты до конца экзамена.</Rule>
                <Rule icon="list" title="Все ситуации подряд">Ситуации из разных блоков идут одна за другой. Выйти можно в любой момент — экзамен продолжится с текущей ситуации.</Rule>
                <Rule icon="flag" title="Одна оценка в конце">Отлично, хорошо, удовлетворительно или неудовлетворительно — по средним показателям и доле решённых ситуаций.</Rule>
                <Rule icon="shield" title="Безопасность решает">Если средняя безопасность ниже 60, оценка не может быть выше «Удовлетворительно».</Rule>
                <Rule icon="medal" title="Бонусные очки">За оценку начисляются очки, а «Отлично» даёт достижение «Сертификат».</Rule>
              </ul>
              <ul className={styles.gradeTable} aria-label="Бонус за оценку">
                {GRADE_ORDER.map((g) => (
                  <li key={g}>
                    <GradeMark level={GRADES[g].level} size={18} />
                    <span className={styles.gradeName}>{GRADES[g].label}</span>
                    <span className={styles.gradePts}>{GRADES[g].bonus ? `+${GRADES[g].bonus}` : "0"}</span>
                  </li>
                ))}
              </ul>
            </section>

            <section className={`${styles.panel} ${styles.setup} rv`} style={{ "--i": 3 }} aria-labelledby="exam-setup">
              <h2 id="exam-setup" className={styles.h2}>Параметры</h2>
              <div className={styles.field}>
                <span className={styles.label} id="exam-class">Класс вагона</span>
                <div className={`${styles.segmented} ${styles.segWide}`} role="radiogroup" aria-labelledby="exam-class">
                  {CLASS_ORDER.map((k) => (
                    <button key={k} type="button" role="radio" aria-checked={k === clsKey} data-on={k === clsKey || undefined} onClick={() => setClsKey(k)}>{CAR_CLASSES[k].title}</button>
                  ))}
                </div>
                <p className={styles.hint}>{CAR_CLASSES[clsKey].note}</p>
              </div>
              <div className={styles.field}>
                <span className={styles.label} id="exam-size">Длина</span>
                <div className={`${styles.segmented} ${styles.segWide}`} role="radiogroup" aria-labelledby="exam-size">
                  {SIZES.map((n) => (
                    <button key={n} type="button" role="radio" aria-checked={n === size} data-on={n === size || undefined} onClick={() => setSize(n)}>{n} {plural(n, "ситуация", "ситуации", "ситуаций")}</button>
                  ))}
                </div>
              </div>
              {error && <p className={styles.error} role="alert">Не удалось начать экзамен. Проверьте связь с сервером и попробуйте ещё раз.</p>}
              <Button size="lg" className={styles.cta} onClick={begin} disabled={creating}>
                {creating ? "Готовим ситуации…" : "Начать экзамен"}
              </Button>
              {resume && <p className={styles.hint}>Новый экзамен заменит незавершённый в списке «Продолжить».</p>}
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}

function Rule({ icon, title, children }) {
  return (
    <li className={styles.rule}>
      <span className={styles.ruleIcon} aria-hidden="true"><Icon name={icon} size={16} /></span>
      <span><b>{title}.</b> {children}</span>
    </li>
  );
}

/* ============================ Прохождение ============================ */

function ExamRun({ examId }) {
  const [exam, setExam] = useState(null);
  const [loadState, setLoadState] = useState("loading"); // loading | ready | error
  const [itemDone, setItemDone] = useState(false);
  const [advancing, setAdvancing] = useState(false);
  const [advanceErr, setAdvanceErr] = useState(false);
  const aliveRef = useRef(true);

  const done = isDone(exam);
  const idx = exam ? Math.min(exam.currentIndex, Math.max(exam.size - 1, 0)) : 0;
  const item = exam && exam.scenarios ? exam.scenarios[idx] : null;
  const clsKey = exam && CAR_CLASSES[exam.carClass] ? exam.carClass : "STANDARD";
  const cls = CAR_CLASSES[clsKey];
  const variant = item ? (Math.abs(Number(item.scenarioId) || String(item.scenarioId).length) * 7) % 40 : 0;
  const speaker = { kind: "passenger", variant, mood: "calm", name: "Пассажир", role: `Вагон ${cls.car} · ${cls.title}` };

  const dialog = useScenarioDialog({
    speaker,
    exam: true,
    onDone: ({ sessionId, scales }) => {
      api.markExamItemDone(sessionId, scales);
      if (aliveRef.current) setItemDone(true);
    }
  });

  function startItem() {
    setItemDone(false);
    setAdvanceErr(false);
    dialog.start(null, null, () => api.startExamItem(examId));
  }

  function apply(e) {
    setExam(e);
    if (isDone(e)) { clearCurrent(examId); return; }
    saveCurrent(examId);
    startItem();
  }

  useEffect(() => {
    aliveRef.current = true;
    setLoadState("loading");
    api.getExam(examId).then(
      (e) => { if (!aliveRef.current) return; if (!e || !e.examId) { setLoadState("error"); return; } setLoadState("ready"); apply(e); },
      () => { if (aliveRef.current) setLoadState("error"); }
    );
    return () => { aliveRef.current = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [examId]);

  function next() {
    if (advancing) return;
    setAdvancing(true);
    setAdvanceErr(false);
    api.getExam(examId).then(
      (e) => { if (!aliveRef.current) return; setAdvancing(false); apply(e); },
      () => { if (!aliveRef.current) return; setAdvancing(false); setAdvanceErr(true); }
    );
  }

  function exit() {
    const quiet = itemDone || dialog.messages.length <= 1;
    if (quiet || window.confirm("Выйти из экзамена? Его можно будет продолжить позже, но ответы в текущей ситуации не сохранятся.")) navigate("/exam");
  }

  if (loadState === "loading") {
    return (
      <div className={styles.shell}>
        <div className={styles.center}><p className={styles.loading} role="status">Загружаем экзамен…</p></div>
      </div>
    );
  }

  if (loadState === "error") {
    return (
      <div className={styles.shell}>
        <div className={styles.center}>
          <EmptyState
            title="Экзамен не найден"
            message="Возможно, он уже завершён или нет связи с сервером."
            action={<Button as="a" href="#/exam" onClick={() => clearCurrent(examId)}>К экзамену</Button>}
          />
        </div>
      </div>
    );
  }

  if (done) return <ExamResults exam={exam} onRefresh={next} refreshing={advancing} />;

  const isLast = exam.currentIndex + 1 >= exam.size;
  const position = Math.min(exam.currentIndex + 1, exam.size);

  let footer = null;
  if (itemDone) {
    footer = (
      <div className={styles.between}>
        <p className={styles.betweenTitle}><Icon name="check" size={16} />Ситуация завершена</p>
        <p className={styles.betweenNote}>{isLast ? "Это была последняя ситуация. Итог готов." : "Результаты будут показаны в конце экзамена."}</p>
        {advanceErr && <p className={styles.error} role="alert">Нет связи с сервером. Попробуйте ещё раз.</p>}
        <Button size="lg" className={styles.cta} onClick={next} disabled={advancing}>
          {advancing ? "Загрузка…" : isLast ? "Узнать результат" : "Следующая ситуация"}
        </Button>
      </div>
    );
  } else if (dialog.phase === "error") {
    footer = (
      <div className={styles.between}>
        <p className={styles.betweenNote}>Не удалось получить ситуацию — нет связи с сервером.</p>
        <Button size="lg" variant="secondary" className={styles.cta} onClick={next} disabled={advancing}>{advancing ? "Загрузка…" : "Повторить"}</Button>
      </div>
    );
  }

  return (
    <div className={`${styles.shell} ${styles.playShell}`}>
      <header className={styles.bar}>
        <button type="button" className={styles.round} onClick={exit} aria-label="Выйти из экзамена"><Icon name="x" size={16} /></button>
        <div className={styles.progress}>
          <span className={styles.progressLabel} aria-live="polite">Вопрос {position} из {exam.size}</span>
          <ol className={styles.dots} aria-hidden="true">
            {Array.from({ length: exam.size }, (_, i) => (
              <li key={i} data-state={i < exam.currentIndex || (i === exam.currentIndex && itemDone) ? "done" : i === exam.currentIndex ? "current" : "todo"} />
            ))}
          </ol>
        </div>
        <span className={styles.barSpacer} />
      </header>

      <div className={styles.scene} aria-hidden="true">
        <div className={styles.sceneInner}>
          <div className={styles.window}><span className={styles.hills} /></div>
          <div className={styles.seat} style={{ background: cls.seat }} />
          <div className={styles.passenger} key={`p${exam.currentIndex}`}><SeatedPerson variant={variant} size={120} mood="calm" /></div>
          <div className={styles.conductor}><Person outfit="conductor" facing="left" size={150} talking={dialog.busy} /></div>
        </div>
        {item && <p className={styles.sceneTag}>{item.blockLabel || blockTitle(item.block)}</p>}
      </div>

      <ChatDialog
        sheet={false}
        speaker={dialog.speaker}
        messages={dialog.messages}
        choices={dialog.phase === "playing" && !dialog.busy ? dialog.choices : null}
        onChoose={dialog.choose}
        timer={dialog.timer}
        busy={dialog.busy}
        scales={null}
        className={styles.chat}
        footer={footer}
      />
    </div>
  );
}

/* ============================ Итог ============================ */

function ExamResults({ exam, onRefresh, refreshing }) {
  const res = exam.result;
  const g = res && GRADES[res.grade] ? GRADES[res.grade] : null;
  const cls = CAR_CLASSES[exam.carClass];
  const pct = (v) => `${Math.round((Number(v) || 0) * (v <= 1 ? 100 : 1))}%`;
  const safetyCapped = res && res.avgSafetyScore < 60 && (res.grade === "SATISFACTORY" || res.grade === "UNSATISFACTORY");
  const weak = res ? Array.from(new Set(res.weakBlocks || [])) : [];

  return (
    <div className={styles.shell}>
      <header className={styles.bar}>
        <a className={styles.round} href="#/scenarios" aria-label="К тренировкам"><Icon name="x" size={16} /></a>
        <span className={styles.barTitle}>Итог экзамена</span>
        <span className={styles.barSpacer} />
      </header>

      <div className={styles.scroll}>
        <div className={styles.page}>
          {!res || !g ? (
            <EmptyState
              title="Итог формируется"
              message="Все ситуации пройдены. Обновите через несколько секунд, чтобы увидеть оценку."
              action={<Button onClick={onRefresh} disabled={refreshing}>{refreshing ? "Загрузка…" : "Обновить"}</Button>}
            />
          ) : (
            <>
              <section className={`${styles.gradeCard} rv`} data-grade={res.grade}>
                <p className={styles.kicker}>
                  Экзамен · {exam.size} {plural(exam.size, "ситуация", "ситуации", "ситуаций")}{cls ? ` · ${cls.title}` : ""}
                </p>
                <div className={styles.gradeRow}>
                  <GradeMark level={g.level} size={88} className={styles.gradeMark} />
                  <div className={styles.gradeText}>
                    <h1 className={styles.gradeLabel}>{g.label}</h1>
                    <p className={styles.bonus}>{g.bonus ? <><b>+{g.bonus}</b> бонусных очков</> : "Бонусные очки не начислены"}</p>
                  </div>
                  {res.grade === "EXCELLENT" && (
                    <div className={styles.cert}>
                      <Medal shape="shield" finish="enamel" glyph="clipboard" size={76} spin backTitle="Сертификат" backNote="Экзамен сдан на «Отлично»" />
                      <span>Сертификат</span>
                    </div>
                  )}
                </div>
                <p className={styles.gradeNote}>{g.note}</p>
                {safetyCapped && (
                  <p className={styles.capNote}><Icon name="shield" size={14} />Средняя безопасность ниже 60 — по правилам экзамена оценка не выше «Удовлетворительно».</p>
                )}
              </section>

              <dl className={`${styles.stats} rv`} style={{ "--i": 1 }}>
                <div data-kind="safety"><dt><Icon name="shield" size={14} />Безопасность</dt><dd>{Math.round(res.avgSafetyScore)}</dd></div>
                <div data-kind="loyalty"><dt><Icon name="smile" size={14} />Лояльность</dt><dd>{Math.round(res.avgLoyaltyScore)}</dd></div>
                <div><dt><Icon name="check" size={14} />Решено ситуаций</dt><dd>{pct(res.successRate)}</dd></div>
              </dl>

              <div className={styles.resultsGrid}>
                <section className={`${styles.panel} rv`} style={{ "--i": 2 }} aria-labelledby="exam-items">
                  <h2 id="exam-items" className={styles.h2}>Ситуации</h2>
                  <ol className={styles.items}>
                    {exam.scenarios.map((it, i) => {
                      const o = OUTCOMES[it.outcome];
                      return (
                        <li key={it.sortOrder != null ? it.sortOrder : i} className={styles.item}>
                          <span className={styles.itemNum}>{i + 1}</span>
                          <span className={styles.itemMain}>
                            <span className={styles.itemTitle}>{it.title || it.scenarioCode}</span>
                            <span className={styles.itemMeta}>
                              {it.blockLabel || blockTitle(it.block)}
                              {it.safetyScore != null && <span className={styles.score}><Icon name="shield" size={12} />{Math.round(it.safetyScore)}</span>}
                              {it.loyaltyScore != null && <span className={styles.score}><Icon name="smile" size={12} />{Math.round(it.loyaltyScore)}</span>}
                            </span>
                          </span>
                          <span className={styles.outcome}>
                            {o ? <><GradeMark level={o.level} size={16} />{o.label}</> : <span className={styles.muted}>—</span>}
                          </span>
                          {it.userProgressId ? (
                            <a className={styles.itemLink} href={`#/debrief/${encodeURIComponent(it.userProgressId)}`}>Разбор<Icon name="chevronRight" size={14} /></a>
                          ) : <span className={styles.itemLinkEmpty} />}
                        </li>
                      );
                    })}
                  </ol>
                </section>

                <section className={`${styles.panel} rv`} style={{ "--i": 3 }} aria-labelledby="exam-weak">
                  <h2 id="exam-weak" className={styles.h2}>Что стоит повторить</h2>
                  {weak.length === 0 ? (
                    <p className={styles.hint}>Во всех блоках ситуации решены. Можно усложнить задачу — выбрать другой класс вагона или длинный экзамен.</p>
                  ) : (
                    <ul className={styles.weak}>
                      {weak.map((b) => (
                        <li key={b}>
                          <a className={styles.weakLink} href="#/scenarios">
                            <span className={styles.ruleIcon} aria-hidden="true"><Icon name={BLOCK_ICON[b] || "help"} size={16} /></span>
                            <span className={styles.weakText}>
                              <span className={styles.weakTitle}>{blockTitle(b)}</span>
                              <span className={styles.weakMeta}>Потренироваться в каталоге</span>
                            </span>
                            <Icon name="chevronRight" size={16} className={styles.muted} />
                          </a>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              </div>

              <div className={`${styles.actions} rv`} style={{ "--i": 4 }}>
                <Button size="lg" as="a" href="#/exam">Новый экзамен</Button>
                <Button size="lg" variant="secondary" as="a" href="#/scenarios">К тренировкам</Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
