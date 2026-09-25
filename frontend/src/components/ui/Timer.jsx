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
 * props: timerSeconds (число, опц.), deadlineAt (ISO-строка, опц.), onExpire (колбэк без
 * аргументов, вызывается один раз). Родитель должен передавать key={node.id}, чтобы таймер
 * пересоздавался на каждом узле — реальный истёкший таймер обрабатывается вызовом
 * api.timeout(progressId) на уровне экрана, этот компонент только визуализирует отсчёт.
 */
export default function Timer({ timerSeconds, deadlineAt, onExpire }) {
  const deadlineMs = deadlineAt ? new Date(deadlineAt).getTime() : null;

  function computeRemaining() {
    if (deadlineMs) return Math.max(0, Math.round((deadlineMs - Date.now()) / 1000));
    return null;
  }

  const totalSecondsRef = useRef(timerSeconds || (deadlineMs ? Math.max(1, computeRemaining()) : 1));
  const [remaining, setRemaining] = useState(deadlineMs ? computeRemaining() : (timerSeconds || 0));
  const expiredFired = useRef(false);

  useEffect(() => {
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
  }, [remaining]);

  const totalSeconds = totalSecondsRef.current || 1;
  const ratio = remaining / totalSeconds;
  let status = "normal";
  if (remaining <= 0) status = "expired";
  else if (ratio <= 0.1) status = "critical";
  else if (ratio <= 0.3) status = "urgent";

  const label = remaining > 0 ? `${remaining} с` : "Время вышло";

  return (
    <div className={`${styles.timer} ${styles[status]}`} role="timer" aria-live="polite">
      <span aria-hidden="true">⏱</span>
      <span>{label}</span>
    </div>
  );
}
