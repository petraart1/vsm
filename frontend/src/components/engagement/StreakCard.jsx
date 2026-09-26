import Icon from "../ui/Icon.jsx";
import Medal from "../awards/Medal.jsx";
import { CountUp } from "../motion/Motion.jsx";
import { STREAK_MILESTONES, longestStreak, readVisits, streakOf, streakMedal, weekStrip, claimedStreakRewards } from "../../engagement.js";
import { readFinish } from "../awards/awards.js";
import styles from "./Engagement.module.css";

/** Серия входов: неделя кружками (как «Кольца» в Fitness), текущая и лучшая серия, следующая медаль. */
export default function StreakCard({ compact = false }) {
  const visits = readVisits();
  const streak = streakOf(visits);
  const best = longestStreak(visits);
  const week = weekStrip(visits);
  const next = STREAK_MILESTONES.find((m) => m.days > streak);
  const claimed = claimedStreakRewards();
  return (
    <section className={styles.card} aria-label="Серия входов">
      <header className={styles.head}>
        <span className={styles.flame}><Icon name="flame" size={18} /></span>
        <div>
          <p className={styles.kicker}>Серия входов</p>
          <p className={styles.big}><CountUp value={streak} /> <small>{daysWord(streak)}</small></p>
        </div>
        <p className={styles.best}>Лучшая<br /><b className="num">{best}</b></p>
      </header>
      <div className={styles.week}>
        {week.map((d) => (
          <span key={d.key} className={styles.day} data-done={d.done || undefined} data-today={d.today || undefined} data-future={d.future || undefined}>
            <i>{d.done && <Icon name="check" size={12} strokeWidth={2.6} />}</i>
            {d.label}
          </span>
        ))}
      </div>
      {next && (
        <div className={styles.next}>
          <div className={styles.nextBar}><span style={{ transform: `scaleX(${Math.min(1, streak / next.days)})` }} /></div>
          <p>До медали «{next.title}» — {next.days - streak} {daysWord(next.days - streak)}</p>
        </div>
      )}
      {!compact && (
        <div className={styles.milestones}>
          {STREAK_MILESTONES.map((m) => (
            <span key={m.days} className={styles.milestone} title={m.title}>
              <Medal {...streakMedal(m.days, readFinish(), claimed.includes(m.days))} size={44} />
            </span>
          ))}
        </div>
      )}
    </section>
  );
}

export function daysWord(n) {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return "день";
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "дня";
  return "дней";
}
