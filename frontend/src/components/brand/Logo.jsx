import styles from "./Logo.module.css";

/** Контуры мозга в сетке 24×24 — знак ReactLab. pathLength=1 нужен для анимации прорисовки. */
export const BRAIN_PATHS = [
  "M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z",
  "M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z",
  "M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4",
  "M17.599 6.5a3 3 0 0 0 .399-1.375",
  "M6.003 5.125A3 3 0 0 0 6.401 6.5",
  "M3.477 10.896a4 4 0 0 1 .585-.396",
  "M19.938 10.5a4 4 0 0 1 .585.396",
  "M6 18a4 4 0 0 1-1.967-.516",
  "M19.967 17.484A4 4 0 0 1 18 18"
];

/** Знак: скруглённый квадрат цвета текста, внутри — мозг цвета фона. */
export function LogoMark({ size = 28, className, draw = false }) {
  return (
    <svg
      className={[styles.mark, draw ? styles.draw : null, className].filter(Boolean).join(" ")}
      width={size}
      height={size}
      viewBox="0 0 32 32"
      aria-hidden="true"
      focusable="false"
    >
      <rect className={styles.tile} width="32" height="32" rx="8" />
      <g transform="translate(5.2 5.2) scale(0.9)" className={styles.brain}>
        {BRAIN_PATHS.map((d, i) => (
          <path key={i} d={d} pathLength="1" style={{ "--p": i }} />
        ))}
      </g>
    </svg>
  );
}

/** Вордмарк: «React» — жирный, «Lab» — обычный, как в логотипе. */
export function Wordmark({ className }) {
  return (
    <span className={[styles.wordmark, className].filter(Boolean).join(" ")}>
      <span className={styles.react}>React</span><span className={styles.lab}>Lab</span>
    </span>
  );
}

export default function Logo({ size = 28, href = "#/scenarios" }) {
  return (
    <a className={styles.logo} href={href} aria-label="ReactLab, на главную">
      <LogoMark size={size} />
      <Wordmark />
    </a>
  );
}
