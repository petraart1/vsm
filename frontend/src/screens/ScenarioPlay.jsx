import { useEffect, useState } from "react";
import { navigate } from "../router.js";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import ChatDialog from "../components/dialog/ChatDialog.jsx";
import useScenarioDialog from "../components/dialog/useScenarioDialog.js";
import useLocalDialog from "../components/dialog/useLocalDialog.js";
import { findStress } from "../shift/stressScenarios.js";
import { SeatedPerson, Person } from "../components/characters/People.jsx";
import { CAR_CLASSES, CLASS_ORDER } from "../shift/shiftModel.js";
import styles from "./ScenarioPlay.module.css";

/**
 * Отработка одной ситуации вне смены: диалог в формате переписки с пассажиром.
 * Класс вагона влияет на ожидания пассажира (?class=STANDARD|COMFORT|BUSINESS|FIRST).
 * props: route (сегменты ["scenarios", id, "play"])
 */
export default function ScenarioPlay({ route }) {
  const scenarioId = route.segments[1];
  const q = String((route.query && route.query.class) || "STANDARD").toUpperCase();
  const [clsKey, setClsKey] = useState(CAR_CLASSES[q] ? q : "STANDARD");
  const cls = CAR_CLASSES[clsKey];
  const local = String(scenarioId).startsWith("local-") ? findStress(String(scenarioId).slice(6)) : null;
  const variant = local ? String(scenarioId).length * 5 : (Number(scenarioId) * 7) % 40;
  const [localResult, setLocalResult] = useState(null);
  const speaker = { kind: "passenger", variant, mood: local ? local.mood : "calm", name: "Пассажир", role: `Вагон ${cls.car} · ${cls.title}` };
  const remote = useScenarioDialog({ speaker, onDone: () => {} });
  const localDlg = useLocalDialog({ speaker, onDone: setLocalResult });
  const dialog = local ? localDlg : remote;

  useEffect(() => {
    setLocalResult(null);
    if (local) localDlg.start(local, 7);
    else remote.start(scenarioId, clsKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioId, clsKey]);

  function exit() {
    if (dialog.phase === "final" || dialog.messages.length <= 1 || window.confirm("Прогресс текущего прохождения будет потерян. Выйти?")) navigate("/scenarios");
  }

  if (dialog.phase === "error" && dialog.messages.length === 0) {
    return (
      <div className={styles.center}>
        <EmptyState title="Сценарий не найден" message="Возможно, он удалён или нет связи с сервером." action={<Button as="a" href="#/scenarios">Открыть каталог</Button>} />
      </div>
    );
  }

  return (
    <div className={styles.shell}>
      <header className={styles.bar}>
        <button type="button" className={styles.round} onClick={exit} aria-label="Выйти"><Icon name="x" size={16} /></button>
        <div className={styles.segmented} role="radiogroup" aria-label="Класс вагона">
          {CLASS_ORDER.map((k) => (
            <button key={k} type="button" role="radio" aria-checked={k === clsKey} data-on={k === clsKey || undefined} disabled={dialog.messages.length > 1 && dialog.phase !== "final"} onClick={() => setClsKey(k)}>{CAR_CLASSES[k].title}</button>
          ))}
        </div>
        <span style={{ width: 36 }} />
      </header>

      <div className={styles.scene} aria-hidden="true">
        <div className={styles.window}><span className={styles.hills} /></div>
        <div className={styles.seat} style={{ background: cls.seat }} />
        <div className={styles.passenger}><SeatedPerson variant={variant} size={120} mood={local ? local.mood : "calm"} /></div>
        <div className={styles.conductor}><Person outfit="conductor" facing="left" size={150} talking={dialog.busy} /></div>
      </div>

      <ChatDialog
        sheet={false}
        speaker={dialog.speaker}
        messages={dialog.messages}
        choices={dialog.phase === "playing" && !dialog.busy ? dialog.choices : null}
        onChoose={dialog.choose}
        timer={dialog.timer}
        busy={dialog.busy}
        scales={dialog.scales}
        className={styles.chat}
        footer={
          dialog.phase === "final" && local ? <LocalDebrief result={localResult} onAgain={() => { setLocalResult(null); localDlg.start(local, Date.now() % 97); }} />
            : dialog.phase === "final" ? <Button size="lg" className={styles.cta} onClick={() => navigate("/debrief/" + dialog.sessionId)}>Открыть разбор</Button>
            : dialog.phase === "error" ? <Button size="lg" variant="secondary" className={styles.cta} onClick={() => dialog.start(scenarioId, clsKey)}>Нет связи — начать заново</Button>
              : null
        }
      />
    </div>
  );
}

/** Разбор локальной стрессовой ситуации — прямо под перепиской. */
function LocalDebrief({ result, onAgain }) {
  if (!result) return null;
  const mistakes = result.log.filter((l) => !l.best);
  return (
    <div className={styles.debrief}>
      <p className={styles.debriefVerdict}>{result.verdict}</p>
      <ul className={styles.debriefList}>
        {result.log.map((l, i) => (
          <li key={i} data-best={l.best || undefined}><b>{l.best ? "Верно" : l.critical ? "Критично" : "Ошибка"}.</b> {l.note}</li>
        ))}
      </ul>
      {mistakes.length === 0 && <p className={styles.debriefNote}>Все решения — по регламенту.</p>}
      <div className={styles.debriefActions}>
        <Button size="lg" className={styles.cta} onClick={onAgain}>Пройти ещё раз</Button>
        <Button size="lg" variant="secondary" className={styles.cta} as="a" href="#/scenarios">К тренировкам</Button>
      </div>
    </div>
  );
}
