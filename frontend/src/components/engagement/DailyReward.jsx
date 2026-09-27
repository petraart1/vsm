import { useEffect, useRef, useState } from "react";
import Icon from "../ui/Icon.jsx";
import Button from "../ui/Button.jsx";
import Medal from "../awards/Medal.jsx";
import { readFinish } from "../awards/awards.js";
import { STREAK_MILESTONES, streakMedal, weekStrip, rewardShownToday, markRewardShown } from "../../engagement.js";
import { daysWord } from "./StreakCard.jsx";
import { getSettings } from "../../settings.js";
import styles from "./Engagement.module.css";

/**
 * Награда за вход: при первом заходе за день — шторка с серией; на вехах 3/7/14/30 дней —
 * новая медаль с анимацией получения. props: visit — результат recordVisit(), delay (мс).
 */
export default function DailyReward({ visit, delay = 0, onClose }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    if (!visit || !visit.firstToday || rewardShownToday() || !getSettings().dailyReward) return undefined;
    const t = window.setTimeout(() => {
      if (rewardShownToday()) return;
      markRewardShown();
      setOpen(true);
    }, delay);
    return () => window.clearTimeout(t);
  }, [visit, delay]);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
  }, [open]);
  if (!visit || !visit.firstToday || !open) return null;
  const week = weekStrip(visit.visits);
  const next = STREAK_MILESTONES.find((m) => m.days > visit.streak);
  function close() {
    if (ref.current && ref.current.open) ref.current.close();
    setOpen(false);
    onClose && onClose();
  }
  return (
    <dialog ref={ref} className={styles.reward} onClose={close} onClick={(e) => { if (e.target === ref.current) close(); }} aria-label="Награда за вход">
      <div className={styles.rewardInner}>
        {visit.reward ? (
          <Medal {...streakMedal(visit.reward.days, readFinish())} size={132} unlock spin />
        ) : (
          <span className={styles.rewardFlame}><Icon name="flame" size={40} /></span>
        )}
        <p className={styles.kicker} tabIndex={-1} autoFocus>{visit.reward ? "Новая награда" : "Награда за вход"}</p>
        <h2 className={styles.rewardTitle}>{visit.reward ? visit.reward.title : `${visit.streak} ${daysWord(visit.streak)} подряд`}</h2>
        <p className={styles.rewardNote}>
          {visit.reward
            ? "Медаль добавлена в «Награды». Её можно выставить на витрину профиля."
            : visit.streak === 1 ? "Серия начинается сегодня. Заходите завтра, чтобы её продолжить." : "Серия продолжается — отличная дисциплина."}
        </p>
        <div className={styles.week}>
          {week.map((d) => (
            <span key={d.key} className={styles.day} data-done={d.done || undefined} data-today={d.today || undefined} data-future={d.future || undefined}>
              <i>{d.done && <Icon name="check" size={12} strokeWidth={2.6} />}</i>
              {d.label}
            </span>
          ))}
        </div>
        {next && <p className={styles.rewardHint}>Следующая медаль — «{next.title}» через {next.days - visit.streak} {daysWord(next.days - visit.streak)}</p>}
        <Button size="lg" className={styles.rewardBtn} onClick={close}>Продолжить</Button>
      </div>
    </dialog>
  );
}
