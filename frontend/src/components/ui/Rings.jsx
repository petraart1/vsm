import { useEffect, useState } from "react";
import { prefersReducedMotion } from "../motion/Motion.jsx";
import styles from "./Rings.module.css";

/**
 * Кольца активности в духе Apple Fitness, но в корпоративной сине-серой гамме ВСМ.
 * props: rings [{ value: 0..1, tone: 'navy'|'blue'|'sky', label }], size (px), delay (мс).
 */
export default function Rings({ rings, size = 120, delay = 150, className }) {
  const [on, setOn] = useState(prefersReducedMotion());
  useEffect(() => {
    const t = window.setTimeout(() => setOn(true), delay);
    return () => window.clearTimeout(t);
  }, [delay]);
  const stroke = size * 0.105;
  const gap = stroke * 0.22;
  return (
    <svg className={[styles.rings, className].filter(Boolean).join(" ")} width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={rings.map((r) => `${r.label}: ${Math.round(r.value * 100)}%`).join(", ")}>
      {rings.map((r, i) => {
        const rad = size / 2 - stroke / 2 - i * (stroke + gap);
        const c = 2 * Math.PI * rad;
        const v = Math.max(0, Math.min(1, r.value || 0));
        return (
          <g key={r.label} data-tone={r.tone} className={styles.ring}>
            <circle cx={size / 2} cy={size / 2} r={rad} strokeWidth={stroke} className={styles.track} />
            <circle
              cx={size / 2}
              cy={size / 2}
              r={rad}
              strokeWidth={stroke}
              className={styles.fill}
              strokeDasharray={c}
              strokeDashoffset={on ? c * (1 - v) : c}
              style={{ transitionDelay: `${i * 120}ms` }}
            />
          </g>
        );
      })}
    </svg>
  );
}
