import { useState, useRef, useEffect } from "react";
import styles from "./Timer.module.css";

/**
 * Таймер решения. Источник истины — серверный `deadlineAt` (абсолютный ISO-момент,
 * см. ru.vsm.backend.scenario.web.dto.NodeStateResponse#deadlineAt): каждую секунду remaining
 * пересчитывается от Date.now(), а не декрементируется локально — так таймер не расходится с
 * сервером при подтормаживании вкладки. `timerSeconds` используется только как приблизительная
 * длина отсчёта для порогов цвета (critical/urgent) и как fallback, если deadlineAt почему-то
 * не пришёл (тогда тикаем локально от timerSeconds, как раньше).
 *
 * `remainingOverride` (число, опц.) — управляемый режим: секунды приходят снаружи (WS-канал,
 * событие `tick`, см. ws.js) вместо локального пересчёта. Пока проп не передан (undefined),
 * компонент тикает сам, как описано выше — это и есть REST-фолбэк на случай, если WebSocket не
 * подключён. Если управляемый режим прерывается посреди отсчёта (WS оборвался), компонент сам
 * пересчитывает remaining от deadlineAt в момент перехода — без скачка и без потери секунды.
 * В управляемом режиме `onExpire` не вызывается: обнуление отсчёта — это только отображение,
 * фактическое действие по таймауту в этом режиме приходит отдельным сообщением WS (`timeout`).
 *
 * props: timerSeconds (число, опц.), deadlineAt (ISO-строка, опц.), onExpire (колбэк без
 * аргументов, вызывается один раз, только вне управляемого режима), remainingOverride (число,
 * опц.). Родитель должен передавать key={node.id}, чтобы таймер пересоздавался на каждом узле.
 */
export default function Timer({ timerSeconds, deadlineAt, onExpire, remainingOverride }) {
  const controlled = typeof remainingOverride === "number";
  const deadlineMs = deadlineAt ? new Date(deadlineAt).getTime() : null;

  function computeRemaining() {
    if (deadlineMs) return Math.max(0, Math.round((deadlineMs - Date.now()) / 1000));
    return null;
  }

  const totalSecondsRef = useRef(timerSeconds || (deadlineMs ? Math.max(1, computeRemaining()) : 1));
  const [remaining, setRemaining] = useState(deadlineMs ? computeRemaining() : (timerSeconds || 0));
  const expiredFired = useRef(false);
  const wasControlledRef = useRef(controlled);

  // Переход управляемый → самостоятельный (WS оборвался посреди отсчёта узла) — пересчитываем
  // remaining от deadlineAt заново, а не продолжаем с замороженного при входе в управляемый режим
  // значения.
  useEffect(() => {
    if (wasControlledRef.current && !controlled) {
      setRemaining(deadlineMs ? computeRemaining() : (timerSeconds || 0));
      expiredFired.current = false;
    }
    wasControlledRef.current = controlled;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [controlled]);

  useEffect(() => {
    if (controlled) return undefined;
    if (remaining <= 0) {
      if (!expiredFired.current) {
        expiredFired.current = true;
        onExpire && onExpire();
      }
      return undefined;
    }
    const id = setTimeout(() => {
      setRemaining(deadlineMs ? computeRemaining() : (r) => r - 1);
    }, 1000);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remaining, controlled]);

  const displayRemaining = controlled ? remainingOverride : remaining;
  const totalSeconds = totalSecondsRef.current || 1;
  const ratio = displayRemaining / totalSeconds;
  let status = "normal";
  if (displayRemaining <= 0) status = "expired";
  else if (ratio <= 0.1) status = "critical";
  else if (ratio <= 0.3) status = "urgent";

  const label = displayRemaining > 0 ? `${displayRemaining} с` : "Время вышло";

  return (
    <div className={`${styles.timer} ${styles[status]}`} role="timer" aria-live="polite">
      <span aria-hidden="true">⏱</span>
      <span>{label}</span>
    </div>
  );
}
