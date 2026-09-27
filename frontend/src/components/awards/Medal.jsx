import { useEffect, useId, useRef } from "react";
import { ICON_PATHS } from "../ui/Icon.jsx";
import { prefersReducedMotion } from "../motion/Motion.jsx";
import styles from "./Medal.module.css";

/**
 * Объёмная медаль в духе наград Apple Fitness: металлический обод с фаской, лицевая сторона с
 * тиснением, блик, толщина ребра при повороте. Интерактивная медаль (spin) крутится пальцем или
 * мышью с инерцией и докатывается до ближайшей стороны; на обороте — гравировка.
 *
 * props:
 *  - shape: circle | hexagon | octagon | shield
 *  - finish: metal | enamel | glass
 *  - glyph: имя иконки (Icon) или короткий текст (text)
 *  - earned: false — «заблокированная» серая медаль
 *  - size (px), spin (перетаскивание), unlock (оборот при первом показе)
 *  - backTitle / backNote — гравировка на обороте
 */

const SHAPES = {
  circle: (s) => `M100 ${100 - 92 * s}a${92 * s} ${92 * s} 0 1 1 0 ${184 * s}a${92 * s} ${92 * s} 0 1 1 0 ${-184 * s}z`,
  hexagon: (s) => poly(6, 94 * s, -90),
  octagon: (s) => poly(8, 94 * s, -67.5),
  shield: (s) => {
    const k = s;
    const p = (x, y) => `${100 + (x - 100) * k} ${100 + (y - 100) * k}`;
    return `M${p(100, 8)}C${p(130, 22)} ${p(160, 24)} ${p(182, 22)}C${p(184, 100)} ${p(170, 158)} ${p(100, 194)}C${p(30, 158)} ${p(16, 100)} ${p(18, 22)}C${p(40, 24)} ${p(70, 22)} ${p(100, 8)}Z`;
  }
};

function poly(n, r, startDeg) {
  const pts = [];
  for (let i = 0; i < n; i += 1) {
    const a = ((startDeg + (360 / n) * i) * Math.PI) / 180;
    pts.push(`${(100 + r * Math.cos(a)).toFixed(2)} ${(100 + r * Math.sin(a)).toFixed(2)}`);
  }
  return `M${pts.join("L")}Z`;
}

const FINISHES = {
  metal: {
    rim: ["#fbfcfe", "#a9b2c2", "#eef1f6", "#6f7a8c", "#dce1e9"],
    bevel: ["#7d889b", "#e9edf3"],
    face: ["#e7ebf1", "#aeb7c6"],
    ink: "#57627a",
    light: "#ffffff"
  },
  enamel: {
    rim: ["#fdfefe", "#aab3c3", "#f0f3f7", "#707b8e", "#e0e5ec"],
    bevel: ["#6f7a8c", "#eef1f6"],
    face: ["#2a6bd6", "#0b3d91"],
    ink: "#ffffff",
    light: "#9cc3ff"
  },
  glass: {
    rim: ["#e9f3ff", "#8fb8ee", "#f6fbff", "#5a86c4", "#cfe2fb"],
    bevel: ["#5a86c4", "#e6f1ff"],
    face: ["rgba(214,232,255,0.85)", "rgba(120,168,236,0.55)"],
    ink: "#0b3d91",
    light: "#ffffff"
  },
  locked: {
    rim: ["#d9dce2", "#9ea4ae", "#cdd1d8", "#8a909b", "#c3c8cf"],
    bevel: ["#8a909b", "#d5d9df"],
    face: ["#c7cbd2", "#aab0ba"],
    ink: "#8a909b",
    light: "#eceef1"
  }
};

function Face({ id, shape, finish, glyph, text, back, backTitle, backNote }) {
  const f = FINISHES[finish];
  const glyphPaths = glyph ? ICON_PATHS[glyph] : null;
  return (
    <svg viewBox="0 0 200 200" className={styles.svg} aria-hidden="true">
      <defs>
        <linearGradient id={`${id}rim`} x1="0" y1="0" x2="1" y2="1">
          {f.rim.map((c, i) => <stop key={i} offset={i / (f.rim.length - 1)} stopColor={c} />)}
        </linearGradient>
        <linearGradient id={`${id}bevel`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor={f.bevel[0]} />
          <stop offset="1" stopColor={f.bevel[1]} />
        </linearGradient>
        <radialGradient id={`${id}face`} cx="0.38" cy="0.3" r="0.85">
          <stop offset="0" stopColor={f.face[0]} />
          <stop offset="1" stopColor={f.face[1]} />
        </radialGradient>
        <linearGradient id={`${id}shine`} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#fff" stopOpacity="0" />
          <stop offset="0.5" stopColor="#fff" stopOpacity="0.55" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
        <clipPath id={`${id}clip`}><path d={SHAPES[shape](1)} /></clipPath>
      </defs>

      <path d={SHAPES[shape](1)} fill={`url(#${id}rim)`} />
      <path d={SHAPES[shape](0.88)} fill={`url(#${id}bevel)`} />
      <path d={SHAPES[shape](0.84)} fill={`url(#${id}face)`} />
      <path d={SHAPES[shape](0.74)} fill="none" stroke={f.light} strokeOpacity="0.35" strokeWidth="1.2" />

      {!back && glyphPaths && (
        <g>
          <g transform="translate(58 62) scale(3.5)" fill="none" stroke="#000" strokeOpacity="0.22" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">{glyphPaths}</g>
          <g transform="translate(58 58) scale(3.5)" fill="none" stroke={f.ink} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">{glyphPaths}</g>
        </g>
      )}
      {!back && !glyphPaths && text && (
        <g className={styles.num}>
          <text x="100" y={text.length > 2 ? 116 : 122} textAnchor="middle" fill="#000" fillOpacity="0.25" fontSize={text.length > 2 ? 46 : 64}>{text}</text>
          <text x="100" y={text.length > 2 ? 113 : 119} textAnchor="middle" fill={f.ink} fontSize={text.length > 2 ? 46 : 64}>{text}</text>
        </g>
      )}
      {back && (
        <g className={styles.engrave} fill={f.ink}>
          <text x="100" y="92" textAnchor="middle" fontSize="15" fontWeight="700">{backTitle}</text>
          <text x="100" y="116" textAnchor="middle" fontSize="12" opacity="0.8">{backNote}</text>
          <text x="100" y="146" textAnchor="middle" fontSize="10" opacity="0.6">ReactLab · ВСМ</text>
        </g>
      )}

      <g clipPath={`url(#${id}clip)`} className={styles.shineWrap}>
        <rect className={styles.shine} x="-60" y="-20" width="70" height="260" fill={`url(#${id}shine)`} transform="rotate(20 100 100)" />
      </g>
    </svg>
  );
}

export default function Medal({
  shape = "circle",
  finish = "metal",
  glyph,
  text,
  earned = true,
  size = 96,
  spin = false,
  unlock = false,
  backTitle = "",
  backNote = "",
  className
}) {
  const rawId = useId().replace(/[^a-zA-Z0-9]/g, "");
  const f = earned ? finish : "locked";
  const rotRef = useRef(null);
  const state = useRef({ angle: 0, vel: 0, dragging: false, lastX: 0, raf: 0 });

  // Перетаскивание с инерцией: угол живёт в ref, DOM обновляется напрямую — без ре-рендеров.
  useEffect(() => {
    if (!spin) return undefined;
    const el = rotRef.current;
    const st = state.current;
    function apply() {
      el.style.transform = `rotateY(${st.angle}deg)`;
      el.style.setProperty("--shine-x", `${((st.angle % 360) + 360) % 360 / 3.6}%`);
    }
    function settle() {
      st.vel *= 0.95;
      st.angle += st.vel;
      if (Math.abs(st.vel) < 0.25) {
        const target = Math.round(st.angle / 180) * 180;
        st.angle += (target - st.angle) * 0.12;
        if (Math.abs(target - st.angle) < 0.2) { st.angle = target; apply(); return; }
      }
      apply();
      st.raf = requestAnimationFrame(settle);
    }
    function down(e) {
      st.dragging = true;
      st.lastX = e.clientX;
      st.vel = 0;
      cancelAnimationFrame(st.raf);
      el.setPointerCapture && el.setPointerCapture(e.pointerId);
    }
    function move(e) {
      if (!st.dragging) return;
      const dx = e.clientX - st.lastX;
      st.lastX = e.clientX;
      st.angle += dx * 0.9;
      st.vel = dx * 0.9;
      apply();
    }
    function up() {
      if (!st.dragging) return;
      st.dragging = false;
      if (prefersReducedMotion()) {
        // Без инерции и доводки: сразу ближайшая сторона медали.
        st.vel = 0;
        st.angle = Math.round(st.angle / 180) * 180;
        apply();
        return;
      }
      st.raf = requestAnimationFrame(settle);
    }
    el.addEventListener("pointerdown", down);
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    return () => {
      cancelAnimationFrame(st.raf);
      el.removeEventListener("pointerdown", down);
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
    };
  }, [spin]);

  const edge = [];
  for (let i = 1; i <= 6; i += 1) edge.push(i);

  return (
    <div
      className={[styles.medal, spin ? styles.spinnable : null, unlock ? styles.unlock : null, className].filter(Boolean).join(" ")}
      style={{ width: size, height: size }}
      data-earned={earned || undefined}
    >
      <div className={styles.rot} ref={rotRef}>
        {edge.map((i) => (
          <div key={i} className={styles.edge} style={{ transform: `translateZ(${-i}px)` }}>
            <svg viewBox="0 0 200 200" className={styles.svg} aria-hidden="true">
              <path d={SHAPES[shape](1)} fill={earned ? "#7b8597" : "#8a909b"} opacity={0.9} />
            </svg>
          </div>
        ))}
        <div className={styles.face}>
          <Face id={`${rawId}f`} shape={shape} finish={f} glyph={glyph} text={text} />
        </div>
        <div className={`${styles.face} ${styles.back}`} style={{ transform: "translateZ(-7px) rotateY(180deg)" }}>
          <Face id={`${rawId}b`} shape={shape} finish={f} back backTitle={backTitle} backNote={backNote} />
        </div>
      </div>
      <span className={styles.ground} aria-hidden="true" />
    </div>
  );
}
