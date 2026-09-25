import { useEffect, useMemo, useRef, useState } from "react";
import { buildCalendar, intensityLevel, pluralRu } from "../../progress.js";
import styles from "./ContributionGraph.module.css";

const WEEKDAY_LABELS = ["Пн", "", "Ср", "", "Пт", "", ""];

function describe(day) {
  const date = day.date.toLocaleDateString("ru-RU", { day: "numeric", month: "long", weekday: "short" });
  if (day.count === 0) return `${date}: без тренировок`;
  return `${date}: ${day.count} ${pluralRu(day.count, "сценарий", "сценария", "сценариев")}`;
}

/**
 * Журнал тренировок в виде календарной сетки: колонка — неделя, строка — день недели,
 * насыщенность клетки — число пройденных сценариев за день.
 * props: activity (из progress.readActivity), weeks (по умолчанию 52 — год).
 */
export default function ContributionGraph({ activity, weeks = 52 }) {
  const { columns, months } = useMemo(() => buildCalendar(activity, weeks), [activity, weeks]);
  const [hovered, setHovered] = useState(null);
  const scrollerRef = useRef(null);

  // На узком экране сетка прокручивается — показываем сначала текущую неделю, как в GitHub.
  useEffect(() => {
    const el = scrollerRef.current;
    if (el) el.scrollLeft = el.scrollWidth;
  }, [columns]);

  const total = columns.reduce((acc, col) => acc + col.reduce((a, d) => a + d.count, 0), 0);
  const activeDays = columns.reduce((acc, col) => acc + col.filter((d) => d.count > 0).length, 0);
  const summary = `${total} ${pluralRu(total, "прохождение", "прохождения", "прохождений")} за ${weeks} ${pluralRu(weeks, "неделю", "недели", "недель")}, активных дней: ${activeDays}`;

  return (
    <figure className={styles.figure}>
      <div className={styles.scroller} ref={scrollerRef}>
        <div className={styles.frame} style={{ "--weeks": weeks }}>
          <div className={styles.months} aria-hidden="true">
            {months.map((m) => (
              <span key={`${m.column}-${m.label}`} style={{ gridColumn: m.column + 1 }}>{m.label}</span>
            ))}
          </div>
          <div className={styles.weekdays} aria-hidden="true">
            {WEEKDAY_LABELS.map((label, i) => <span key={i}>{label}</span>)}
          </div>
          <div className={styles.grid} role="img" aria-label={summary} onMouseLeave={() => setHovered(null)}>
            {columns.map((col, w) =>
              col.map((day) => (
                <span
                  key={day.key}
                  className={styles.cell}
                  style={{ "--c": w, "--r": day.date.getDay() }}
                  data-level={day.future ? "future" : intensityLevel(day.count)}
                  onMouseEnter={() => !day.future && setHovered({ day, w })}
                />
              ))
            )}
          </div>
        </div>
      </div>

      <figcaption className={styles.caption}>
        <span className={styles.readout} aria-live="polite">
          {hovered ? describe(hovered.day) : summary}
        </span>
        <span className={styles.legend} aria-hidden="true">
          Меньше
          {[0, 1, 2, 3, 4].map((l) => <span key={l} className={styles.swatch} data-level={l} />)}
          Больше
        </span>
      </figcaption>
    </figure>
  );
}
