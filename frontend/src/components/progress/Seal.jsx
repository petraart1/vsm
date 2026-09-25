import { useId } from "react";
import styles from "./Seal.module.css";

/**
 * Печать учебного центра — знак квалификации. Не «игровая медаль», а оттиск, как на
 * свидетельстве о повышении квалификации: кольцевая надпись, номер модуля в центре.
 *
 * props:
 *  - mark: крупный знак в центре (номер модуля «08» или монограмма отличия);
 *  - caption: мелкая подпись под знаком («модуль»);
 *  - ring: кольцевая надпись;
 *  - state: certified | in_training | not_started;
 *  - progress: 0..1 — дуга прогресса для in_training;
 *  - size: px.
 */
export default function Seal({ mark, caption = "модуль", ring = "ReactLab  •  повышение квалификации  •  ", state = "certified", progress = 0, size = 88 }) {
  const rawId = useId();
  const pathId = `seal-${rawId.replace(/[^a-zA-Z0-9_-]/g, "")}`;
  const arcLength = 2 * Math.PI * 44;

  return (
    <svg
      className={styles.seal}
      data-state={state}
      width={size}
      height={size}
      viewBox="0 0 100 100"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <path id={pathId} d="M 50 50 m -36 0 a 36 36 0 1 1 72 0 a 36 36 0 1 1 -72 0" />
      </defs>

      <circle className={styles.outer} cx="50" cy="50" r="44" />
      {state === "in_training" && (
        <circle
          className={styles.arc}
          cx="50"
          cy="50"
          r="44"
          strokeDasharray={`${arcLength * Math.max(0.02, progress)} ${arcLength}`}
          transform="rotate(-90 50 50)"
        />
      )}
      <circle className={styles.inner} cx="50" cy="50" r="28" />

      {size >= 80 && <text className={styles.ring}>
        <textPath href={`#${pathId}`} startOffset="0" textLength="222" lengthAdjust="spacingAndGlyphs">{ring}</textPath>
      </text>}

      <text className={styles.mark} x="50" y={caption ? 52 : 57} textAnchor="middle">{mark}</text>
      {caption && <text className={styles.caption} x="50" y="64" textAnchor="middle">{caption}</text>}
    </svg>
  );
}
