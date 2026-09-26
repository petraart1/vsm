import styles from "./People.module.css";

/**
 * Персонажи тренажёра — плоские SVG-иллюстрации без внешних ассетов.
 * Person — стоящий человек (проводник, медработник, начальник поезда): умеет идти, говорить, моргать.
 * SeatedPerson — пассажир в кресле: дышит, иногда смотрит в телефон.
 * Все анимации — CSS (transform), уважают prefers-reduced-motion.
 */

const SKIN = ["#f3cfb3", "#e8b894", "#c98e6b", "#9c6a4b", "#f6dcc6"];
const HAIR = ["#2b2118", "#5a3b24", "#a5723e", "#d8b36a", "#8c8c91", "#1c1c1e"];

export const OUTFITS = {
  conductor: { jacket: "#0b3d91", jacketDark: "#082c69", shirt: "#ffffff", trousers: "#10224a", accent: "#d93025", cap: true },
  chief: { jacket: "#0b3d91", jacketDark: "#082c69", shirt: "#ffffff", trousers: "#10224a", accent: "#e6ebf3", cap: true, stripes: true },
  medic: { jacket: "#f4f6fa", jacketDark: "#d5dbe5", shirt: "#dbe8fb", trousers: "#2d4a7a", accent: "#0a64d8", cap: false, cross: true }
};

/** Стоящий персонаж. props: outfit, skin, hair, walking, talking, facing ('left'|'right'), size (px высоты). */
export function Person({ outfit = "conductor", skin = 0, hair = 1, walking = false, talking = false, facing = "right", size = 150, className }) {
  const o = OUTFITS[outfit] || OUTFITS.conductor;
  const skinC = SKIN[skin % SKIN.length];
  const hairC = HAIR[hair % HAIR.length];
  const cls = [styles.person, walking ? styles.walking : null, talking ? styles.talking : null, className].filter(Boolean).join(" ");
  return (
    <svg
      className={cls}
      width={(size * 64) / 150}
      height={size}
      viewBox="0 0 64 150"
      aria-hidden="true"
      style={{ transform: facing === "left" ? "scaleX(-1)" : undefined }}
    >
      <ellipse cx="32" cy="146" rx="17" ry="3" className={styles.shadow} />
      <g className={styles.body}>
        {/* Ноги */}
        <g className={styles.legL}>
          <rect x="22" y="94" width="9" height="44" rx="4" fill={o.trousers} />
          <rect x="19" y="135" width="13" height="7" rx="3.5" fill="#1b1f2a" />
        </g>
        <g className={styles.legR}>
          <rect x="33" y="94" width="9" height="44" rx="4" fill={o.trousers} />
          <rect x="32" y="135" width="13" height="7" rx="3.5" fill="#1b1f2a" />
        </g>
        {/* Задняя рука */}
        <g className={styles.armR}>
          <rect x="44" y="54" width="9" height="36" rx="4.5" fill={o.jacketDark} />
          <circle cx="48.5" cy="91" r="4.5" fill={skinC} />
        </g>
        {/* Корпус */}
        <path d="M17 60c0-8 6-13 15-13s15 5 15 13v36c0 3-2 5-5 5H22c-3 0-5-2-5-5z" fill={o.jacket} />
        <path d="M26 47h12l-6 12z" fill={o.shirt} />
        {outfit === "conductor" && <path d="M29.5 52h5l-2.5 6z" fill={o.accent} />}
        {o.stripes && <><rect x="17" y="84" width="30" height="2" fill={o.accent} opacity=".8" /><rect x="17" y="88" width="30" height="2" fill={o.accent} opacity=".8" /></>}
        <path d="M32 59v38" stroke={o.jacketDark} strokeWidth="1.2" />
        <circle cx="35" cy="68" r="1.3" fill={o.jacketDark} />
        <circle cx="35" cy="78" r="1.3" fill={o.jacketDark} />
        {outfit !== "medic" && <rect x="20" y="63" width="8" height="5" rx="1.2" fill="#c9d3e3" />}
        {o.cross && <path d="M22 64h2v-2h2v2h2v2h-2v2h-2v-2h-2z" fill={o.accent} />}
        {/* Передняя рука */}
        <g className={styles.armL}>
          <rect x="11" y="54" width="9" height="36" rx="4.5" fill={o.jacket} />
          <circle cx="15.5" cy="91" r="4.5" fill={skinC} />
        </g>
        {/* Голова */}
        <rect x="28.5" y="40" width="7" height="9" rx="3" fill={skinC} />
        <g className={styles.head}>
          <circle cx="32" cy="29" r="13" fill={skinC} />
          <path d="M19.5 27c0-9 5.5-14 12.5-14s12.5 5 12.5 13c-3-4-8-6-12.5-6s-9.5 2-12.5 7z" fill={hairC} />
          {o.cap && (
            <g>
              <path d="M18 22c1-8 7-12 14-12s13 4 14 12z" fill={o.jacket} />
              <rect x="17" y="20.5" width="30" height="4" rx="2" fill={o.jacketDark} />
              <path d="M40 23.5h9c1.5 0 1.5 2.5 0 2.5h-9z" fill="#10224a" />
              <circle cx="32" cy="16.5" r="2" fill="#e6ebf3" />
            </g>
          )}
          <g className={styles.eyes}>
            <ellipse cx="36.5" cy="29" rx="1.5" ry="1.9" fill="#1c1c1e" />
            <ellipse cx="28" cy="29" rx="1.5" ry="1.9" fill="#1c1c1e" />
          </g>
          <circle cx="40" cy="33.5" r="2.2" fill="#f19a8b" opacity=".35" />
          <path className={styles.mouth} d="M30.5 35.2q2.8 2.3 5.6 0" stroke="#7a3b2e" strokeWidth="1.4" fill="none" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  );
}

/** Пассажир в кресле (вид спереди). variant — детерминированный номер внешности. */
export function SeatedPerson({ variant = 0, size = 96, kid = false, phone = false, className }) {
  const skinC = SKIN[(variant * 3 + 1) % SKIN.length];
  const hairC = HAIR[(variant * 5 + 2) % HAIR.length];
  const tops = ["#5b7fb8", "#d6dde8", "#2f3e57", "#8fb3d9", "#4a5568", "#e9edf3", "#3b6fc4", "#a7b4c7"];
  const top = tops[(variant * 7) % tops.length];
  const bottoms = ["#2d3748", "#4a5d7a", "#1f2a3d", "#6b7a90"];
  const bottom = bottoms[(variant * 3) % bottoms.length];
  const hairStyle = variant % 4; // 0 короткие, 1 длинные, 2 пучок, 3 кепка
  const glasses = variant % 5 === 2;
  const scale = kid ? 0.72 : 1;
  return (
    <svg
      className={[styles.seated, className].filter(Boolean).join(" ")}
      width={size * 0.7 * scale}
      height={size * scale}
      viewBox="0 0 70 100"
      aria-hidden="true"
      style={{ "--breath-delay": `${(variant % 7) * -0.6}s` }}
    >
      <g className={styles.torso}>
        {/* Ноги (согнуты, к зрителю) */}
        <rect x="18" y="72" width="15" height="12" rx="5" fill={bottom} />
        <rect x="37" y="72" width="15" height="12" rx="5" fill={bottom} />
        <rect x="19" y="82" width="12" height="15" rx="4" fill={bottom} />
        <rect x="39" y="82" width="12" height="15" rx="4" fill={bottom} />
        <rect x="17" y="94" width="15" height="5" rx="2.5" fill="#1b1f2a" />
        <rect x="38" y="94" width="15" height="5" rx="2.5" fill="#1b1f2a" />
        {/* Корпус и руки */}
        <path d="M17 50c0-7 7-12 18-12s18 5 18 12v26H17z" fill={top} />
        <rect x="11" y="46" width="9" height="28" rx="4.5" fill={top} />
        <rect x="50" y="46" width="9" height="28" rx="4.5" fill={top} />
        <circle cx="15.5" cy="74" r="4" fill={skinC} />
        <circle cx="54.5" cy="74" r="4" fill={skinC} />
        {phone && <rect className={styles.phone} x="30" y="64" width="10" height="14" rx="2" fill="#1c1c1e" />}
        <rect x="31.5" y="33" width="7" height="7" rx="3" fill={skinC} />
        {/* Голова */}
        <g className={styles.seatHead}>
          <circle cx="35" cy="23" r="12" fill={skinC} />
          {hairStyle === 0 && <path d="M23 22c0-8 5-12 12-12s12 4 12 11c-3-3-7-5-12-5s-9 2-12 6z" fill={hairC} />}
          {hairStyle === 1 && <path d="M22.5 24c0-9 5-14 12.5-14S47.5 15 47.5 24v12h-4V22c-3-4-6-5-8.5-5s-6 1-8.5 5v14h-4z" fill={hairC} />}
          {hairStyle === 2 && <><circle cx="35" cy="8.5" r="5" fill={hairC} /><path d="M23 22c0-8 5-12 12-12s12 4 12 11c-3-3-7-5-12-5s-9 2-12 6z" fill={hairC} /></>}
          {hairStyle === 3 && <><path d="M22.5 20c1-7 6-11 12.5-11s11.5 4 12.5 11z" fill="#3b6fc4" /><rect x="22" y="18.5" width="26" height="3.5" rx="1.75" fill="#2a4f8f" /></>}
          <ellipse cx="31" cy="24" rx="1.4" ry="1.7" fill="#1c1c1e" />
          <ellipse cx="39" cy="24" rx="1.4" ry="1.7" fill="#1c1c1e" />
          {glasses && <g fill="none" stroke="#1c1c1e" strokeWidth="1"><circle cx="31" cy="24" r="3.4" /><circle cx="39" cy="24" r="3.4" /><path d="M34.4 24h1.2" /></g>}
          <path d="M32.5 29.5q2.5 1.8 5 0" stroke="#7a3b2e" strokeWidth="1.2" fill="none" strokeLinecap="round" />
        </g>
      </g>
    </svg>
  );
}

/** Портрет пассажира для диалога — крупный план головы и плеч. */
export function PassengerBust({ variant = 0, size = 48, talking = false }) {
  return (
    <span className={`${styles.bust} ${talking ? styles.bustTalking : ""}`} style={{ width: size, height: size }} aria-hidden="true">
      <SeatedPerson variant={variant} size={size * 2.1} />
    </span>
  );
}

/** Портрет стоящего персонажа (проводник, медработник) для диалога. */
export function PersonBust({ outfit = "conductor", size = 48 }) {
  return (
    <span className={styles.bust} style={{ width: size, height: size }} aria-hidden="true">
      <Person outfit={outfit} size={size * 3.2} />
    </span>
  );
}
