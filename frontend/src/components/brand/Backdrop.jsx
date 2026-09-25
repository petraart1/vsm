import { useEffect, useState } from "react";
import { getStyle, subscribeAppearance } from "../../appearance.js";
import styles from "./Backdrop.module.css";

// Детерминированный набор линий: одинаковый рисунок при каждой загрузке.
function makeLines(count) {
  const lines = [];
  let seed = 7;
  const rnd = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  for (let i = 0; i < count; i += 1) {
    lines.push({
      top: 8 + rnd() * 88,
      len: 12 + rnd() * 28,
      dur: 2.6 + rnd() * 4.5,
      delay: -rnd() * 7,
      op: 0.2 + rnd() * 0.5,
      thick: rnd() > 0.82 ? 2 : 1
    });
  }
  return lines;
}

const LINES = makeLines(26);

/**
 * Фоновый слой стиля «Скорость»: графит, свечение у «полотна» и световые шлейфы, летящие
 * справа налево, как огни поезда на фото. Для остальных стилей ничего не рисует — их фон
 * задан в CSS (variants.css).
 */
export default function Backdrop() {
  const [style, setStyleState] = useState(getStyle);
  useEffect(() => subscribeAppearance(() => setStyleState(getStyle())), []);

  if (style !== "speed") return null;

  return (
    <div className={styles.speed} aria-hidden="true">
      <div className={styles.glow} />
      <div className={styles.rails} />
      <div className={styles.streakLayer}>
      {LINES.map((l, i) => (
        <span
          key={i}
          className={styles.streak}
          style={{
            "--top": `${l.top}%`,
            "--len": `${l.len}vw`,
            "--dur": `${l.dur}s`,
            "--delay": `${l.delay}s`,
            "--op": l.op,
            "--thick": `${l.thick}px`
          }}
        />
      ))}
      </div>
    </div>
  );
}
