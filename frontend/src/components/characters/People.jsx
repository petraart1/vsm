import styles from "./People.module.css";

/**
 * Персонажи тренажёра — плоские SVG-иллюстрации в едином стиле (крупная голова, простое лицо,
 * мягкие тени), без внешних ассетов.
 *
 * Person — стоящий человек: проводник, начальник поезда, медработник, охранник, сотрудник
 * транспортной полиции или пассажир. Стоя — вид спереди; в движении — профиль с «суставной»
 * ходьбой: бедро → колено → стопа, руки в противофазе, корпус приседает на двойной опоре.
 * SeatedPerson — пассажир в кресле (вид спереди).
 * Все анимации — CSS transform; в режиме «меньше движения» (data-motion="reduce") выключены.
 */

export const SKIN = ["#f7d7c2", "#eec4a6", "#dcaa88", "#b27a58", "#f9e2d2"];
export const HAIR = ["#3a2a22", "#5a3b2a", "#241d1a", "#8a6a4a", "#b9b9be", "#6b4a33"];

const INK = "#1d1f24";

/** Рабочая одежда персонала. */
const ROLES = {
  conductor: { top: "#1c3170", shade: "#132457", shirt: "#ffffff", tie: "#d6312f", trim: "#d6312f", pants: "#1c3170", pantsShade: "#132457", shoes: "#1b1c21", hat: "rail", bag: true, badge: true },
  chief: { top: "#1c3170", shade: "#132457", shirt: "#ffffff", tie: "#0b3d91", trim: "#e9edf4", pants: "#1c3170", pantsShade: "#132457", shoes: "#1b1c21", hat: "rail", stripes: true, badge: true },
  medic: { top: "#f7f8fb", shade: "#dde2ea", shirt: "#2a4282", tie: null, trim: null, pants: "#2a4282", pantsShade: "#1f3368", shoes: "#f4f5f8", sole: "#c8ced8", coat: true, stethoscope: true, badge: true },
  guard: { top: "#26272c", shade: "#18191c", shirt: "#26272c", tie: null, trim: null, pants: "#26272c", pantsShade: "#18191c", shoes: "#121214", hat: "guard", vest: "#303137", radio: true },
  police: { top: "#34466e", shade: "#26365a", shirt: "#cfe0f4", tie: "#26365a", trim: null, pants: "#26365a", pantsShade: "#1c2944", shoes: "#141416", hat: "police", badge: true }
};

/** Пассажиры: «архетипы» по референсу — худи с рюкзаком, костюм с портфелем, пальто с сумкой… */
const LOOKS = [
  { top: "#ece1cf", shade: "#d8cab3", pants: "#3f5f95", pantsShade: "#324d7b", shoes: "#f4f4f6", sole: "#cfd3da", hood: true, backpack: "#2a2b30", hair: "short" },
  { top: "#1f2a4f", shade: "#161f3c", shirt: "#ffffff", tie: "#1c3170", pants: "#1f2a4f", pantsShade: "#161f3c", shoes: "#1b1c21", briefcase: true, hair: "side" },
  { top: "#c49a72", shade: "#a98260", shirt: "#f3ede4", pants: "#e3dccf", pantsShade: "#cfc6b6", shoes: "#f4f4f6", sole: "#cfd3da", coat: true, handbag: "#7a4e30", hair: "long" },
  { top: "#1f2c55", shade: "#172142", pants: "#3b3e46", pantsShade: "#2e3037", shoes: "#23252b", hood: true, headphones: true, backpack: "#8a8f99", hair: "short" },
  { top: "#ece6d8", shade: "#d6ceba", pants: "#4c5448", pantsShade: "#3d4439", shoes: "#2a2b30", hood: true, cap: "#5d6b4a", backpack: "#2a2b30", hair: "short" },
  { top: "#eeb3c4", shade: "#dc9aad", pants: "#8fb0d6", pantsShade: "#7b9cc3", shoes: "#f4f4f6", sole: "#cfd3da", handbag: "#6b4632", hair: "long" },
  { top: "#8e949e", shade: "#777d88", shirt: "#e9ecf1", pants: "#4a505b", pantsShade: "#3c414b", shoes: "#2a2b30", glasses: true, hair: "grey" },
  { top: "#5d86c2", shade: "#4b72ad", pants: "#2e3a52", pantsShade: "#252f44", shoes: "#f4f4f6", sole: "#cfd3da", hood: true, hair: "short" }
];

/**
 * Пассажиры с признаками нарушений (по референсу команды): variant 100+.
 * 100–101 — нетрезвые (мятая куртка поверх худи, бутылка), 102–103 — агрессивные (тёмный худи
 * с лампасами и кепкой, чёрная куртка), 104–106 — под воздействием веществ (капюшон, бесформенная
 * одежда, заторможенность).
 */
export const TROUBLE_LOOKS = {
  100: { top: "#4d5a44", shade: "#3d4836", innerTop: "#c9ccd2", pants: "#2a2d33", pantsShade: "#202328", shoes: "#e9eaee", sole: "#c8ccd4", hood: true, backpack: "#2a2b30", hair: "messy", bottle: true, skinTone: 0 },
  101: { top: "#26272c", shade: "#1c1d21", innerTop: "#ece6dc", pants: "#6f8fbf", pantsShade: "#5e7dab", shoes: "#f1f1f3", sole: "#c8ccd4", hair: "long", bottle: true, skinTone: 4 },
  102: { top: "#1f2023", shade: "#161719", pants: "#1f2023", pantsShade: "#161719", shoes: "#1a1b1e", hood: true, cap: "#161719", capBrim: "#0e0f10", trackStripes: true, hair: "short", skinTone: 1 },
  103: { top: "#1c1d20", shade: "#141517", innerTop: "#e8e4dc", pants: "#23252a", pantsShade: "#1b1c20", shoes: "#eeeeef", sole: "#c8ccd4", hair: "side", skinTone: 1 },
  104: { top: "#2a2b31", shade: "#1f2025", pants: "#4a463d", pantsShade: "#3c3931", shoes: "#e9eaee", sole: "#c8ccd4", hoodUp: true, backpack: "#26272b", hair: "short", skinTone: 4 },
  105: { top: "#d6ccbb", shade: "#c2b7a4", pants: "#33363c", pantsShade: "#282a2f", shoes: "#e0ddd6", sole: "#c8ccd4", backpack: "#1f2023", hair: "messy", skinTone: 4 },
  106: { top: "#2b2c31", shade: "#202126", pants: "#34363b", pantsShade: "#2a2b30", shoes: "#1a1b1e", beanie: "#1c1d21", hood: true, handbag: "#26272b", hair: "short", skinTone: 1 }
};

export function lookOf(variant) {
  if (TROUBLE_LOOKS[variant]) return TROUBLE_LOOKS[variant];
  return LOOKS[Math.abs(variant) % LOOKS.length];
}

function outfitOf(role, variant) {
  if (role === "passenger") {
    const look = lookOf(variant);
    return { shirt: look.top, ...look };
  }
  return ROLES[role] || ROLES.conductor;
}

/**
 * Стоящий персонаж.
 * props: outfit (роль: conductor|chief|medic|guard|police|passenger), variant (внешность пассажира),
 * skin, hair (цвет), hairStyle, walking, talking, facing ('left'|'right'), size (px высоты).
 */
export function Person({ outfit = "conductor", variant = 0, skin = 0, hair = 0, hairStyle, walking = false, talking = false, facing = "right", size = 150, mood, className }) {
  const o = outfitOf(outfit, variant);
  const skinC = SKIN[(o.skinTone ?? skin) % SKIN.length];
  const hairC = o.hair === "grey" ? HAIR[4] : HAIR[hair % HAIR.length];
  const style = hairStyle || o.hair || (outfit === "medic" ? "side" : "short");
  const cls = [styles.person, walking ? styles.walking : styles.idle, talking ? styles.talking : null, mood === "drunk" ? styles.sway : null, className].filter(Boolean).join(" ");
  return (
    <svg
      className={cls}
      width={(size * 80) / 180}
      height={size}
      viewBox="0 0 80 180"
      aria-hidden="true"
      style={{ transform: facing === "left" ? "scaleX(-1)" : undefined }}
    >
      <ellipse cx="40" cy="175" rx={walking ? 17 : 20} ry="3.4" className={styles.shadow} />
      {walking ? <Side o={o} skinC={skinC} hairC={hairC} hairStyle={style} /> : <Front o={o} skinC={skinC} hairC={hairC} hairStyle={style} mood={mood} />}
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Вид спереди
// ---------------------------------------------------------------------------

function Front({ o, skinC, hairC, hairStyle, mood }) {
  const long = hairStyle === "long";
  return (
    <g className={styles.body}>
      {long && <path d="M21 34 Q19 62 24 80 L56 80 Q61 62 59 34 Z" fill={hairC} />}
      {o.backpack && <rect x="21" y="60" width="38" height="40" rx="8" fill={o.backpack} />}

      {/* Ноги */}
      <rect x="27" y="100" width="12.5" height="66" rx="5" fill={o.pants} />
      <rect x="40.5" y="100" width="12.5" height="66" rx="5" fill={o.pantsShade} />
      {o.trim && <><rect x="27" y="157" width="12.5" height="2.6" fill={o.trim} /><rect x="40.5" y="157" width="12.5" height="2.6" fill={o.trim} /></>}
      {o.trackStripes && <><rect x="28" y="102" width="1.8" height="60" fill="#e9eaee" /><rect x="50.2" y="102" width="1.8" height="60" fill="#e9eaee" /></>}
      <path d="M24 170 Q24 163 30 163 L38 163 Q40 163 40 166 L40 172 L25 172 Q24 172 24 170 Z" fill={o.shoes} />
      <path d="M56 170 Q56 163 50 163 L42 163 Q40 163 40 166 L40 172 L55 172 Q56 172 56 170 Z" fill={o.shoes} />
      {o.sole && <><rect x="24" y="170" width="16" height="2.4" rx="1.2" fill={o.sole} /><rect x="40" y="170" width="16" height="2.4" rx="1.2" fill={o.sole} /></>}

      {/* Корпус */}
      <g className={styles.torso}>
        {o.coat ? (
          <>
            <path d="M21 64 Q21 55 30 55 L50 55 Q59 55 59 64 L62 139 Q62 142 59 142 L21 142 Q18 142 18 139 Z" fill={o.top} />
            <path d="M40 64 L40 142" stroke={o.shade} strokeWidth="1.2" />
            <path d="M50 55 Q59 55 59 64 L62 139 Q62 142 59 142 L52 142 Z" fill={o.shade} opacity=".7" />
          </>
        ) : (
          <>
            <path d="M22 64 Q22 55 30 55 L50 55 Q58 55 58 64 L56 104 Q56 107 53 107 L27 107 Q24 107 24 104 Z" fill={o.top} />
            <path d="M50 55 Q58 55 58 64 L56 104 Q56 107 53 107 L49 107 Z" fill={o.shade} opacity=".75" />
          </>
        )}
        {/* Ворот, рубашка, галстук */}
        {o.innerTop && <path d="M31 55 L49 55 L47 104 L33 104 Z" fill={o.innerTop} />}
        {o.innerTop && <><path d="M31 55 L36 104 L27 107 L24 60 Z" fill={o.top} /><path d="M49 55 L44 104 L53 107 L56 60 Z" fill={o.shade} /></>}
        {o.hood ? (
          <path d="M28 55 Q40 66 52 55 Q50 51 40 51 Q30 51 28 55 Z" fill={o.innerTop && !o.hoodUp ? "#b8bcc4" : o.shade} />
        ) : o.innerTop ? null : (
          <path d="M33 55 L40 71 L47 55 Z" fill={o.shirt || o.top} />
        )}
        {o.tie && <path d="M38.7 58 L41.3 58 L42.4 77 L40 81 L37.6 77 Z" fill={o.tie} />}
        {o.hood && <><path d="M36 60 L35.5 72" stroke="#fff" strokeWidth="1" opacity=".8" /><path d="M44 60 L44.5 72" stroke="#fff" strokeWidth="1" opacity=".8" /></>}
        {!o.hood && !o.coat && o.shirt !== o.top && (
          <>
            <path d="M33 55 L40 71 L35 73 L29 58 Z" fill={o.shade} />
            <path d="M47 55 L40 71 L45 73 L51 58 Z" fill={o.shade} />
          </>
        )}
        {o.coat && <><path d="M31 55 L40 72 L33 76 L26 60 Z" fill={o.shade} /><path d="M49 55 L40 72 L47 76 L54 60 Z" fill={o.shade} /></>}
        {o.vest && (
          <g>
            <rect x="25" y="62" width="30" height="40" rx="4" fill={o.vest} />
            <rect x="28" y="80" width="10" height="9" rx="1.5" fill={o.shade} />
            <rect x="42" y="80" width="10" height="9" rx="1.5" fill={o.shade} />
            <path d="M45 66 l4 0 l0 4 l-2 2 l-2 -2 z" fill="#9aa0aa" />
            <rect x="25" y="99" width="30" height="4" fill={o.shade} />
          </g>
        )}
        {o.badge && !o.vest && <rect x="44" y="66" width="7" height="4.5" rx="1" fill="#eef2f8" />}
        {o.stripes && <><rect x="17" y="90" width="10" height="1.6" fill={o.trim} /><rect x="53" y="90" width="10" height="1.6" fill={o.trim} /></>}
        {o.stethoscope && (
          <g fill="none" stroke="#3b3f48" strokeWidth="1.5" strokeLinecap="round">
            <path d="M33 56 Q30 72 36 82" />
            <path d="M47 56 Q51 70 47 82" />
            <circle cx="47" cy="85" r="2.8" fill="#9aa3b1" />
          </g>
        )}
        {o.coat && o.badge && <rect x="44" y="72" width="7" height="9" rx="1" fill="#dfeaf8" stroke="#9fb4d6" strokeWidth=".6" />}
        {o.headphones && <path d="M28 57 Q40 66 52 57" fill="none" stroke="#1b1c21" strokeWidth="3" strokeLinecap="round" />}
        {o.backpack && <><rect x="25" y="56" width="3.6" height="36" rx="1.8" fill={o.backpack} /><rect x="51.4" y="56" width="3.6" height="36" rx="1.8" fill={o.backpack} /></>}
      </g>

      {/* Сумка через плечо */}
      {(o.bag || o.handbag) && (
        <g>
          <path d="M27 57 L55 99" stroke={o.bag ? "#2d2e33" : o.handbag} strokeWidth="2.2" />
          <rect x="49" y="96" width="15" height="17" rx="3" fill={o.bag ? "#3a3b41" : o.handbag} />
          <rect x="49" y="96" width="15" height="6" rx="2" fill={o.bag ? "#2d2e33" : "#00000022"} />
        </g>
      )}

      {/* Руки */}
      <g className={styles.armL}>
        <rect x="16.5" y="58" width="10" height="44" rx="5" fill={o.top} />
        {o.trim && !o.stripes && <rect x="16.5" y="96" width="10" height="2.4" fill={o.trim} />}
        <circle cx="21.5" cy="105" r="4.8" fill={skinC} />
      </g>
      <g className={styles.armR}>
        <rect x="53.5" y="58" width="10" height="44" rx="5" fill={o.shade} />
        {o.trim && !o.stripes && <rect x="53.5" y="96" width="10" height="2.4" fill={o.trim} />}
        <circle cx="58.5" cy="105" r="4.8" fill={skinC} />
        {o.briefcase && <g><rect x="52" y="108" width="14" height="13" rx="2" fill="#23252b" /><rect x="56" y="105.5" width="6" height="3" rx="1.2" fill="none" stroke="#23252b" strokeWidth="1.3" /></g>}
        {o.bottle && <g><rect x="55.5" y="104" width="6" height="16" rx="2" fill="#3f6b3a" /><rect x="57.2" y="98" width="2.6" height="7" rx="1" fill="#3f6b3a" /><rect x="56" y="109" width="5" height="4" fill="#d9d3c2" opacity=".7" /></g>}
      </g>
      {o.radio && <g><rect x="50" y="56" width="5" height="9" rx="1.2" fill="#111" /><rect x="52.5" y="49" width="1.4" height="8" fill="#111" /></g>}

      {/* Голова */}
      <rect x="36" y="48" width="8" height="10" rx="3" fill={skinC} />
      <g className={styles.head}>
        {o.hoodUp && <path d="M16 40 Q14 8 40 7 Q66 8 64 40 Q63 56 52 58 L28 58 Q17 56 16 40 Z" fill={o.top} />}
        <circle cx="21.5" cy="35" r="3.6" fill={skinC} />
        <circle cx="58.5" cy="35" r="3.6" fill={skinC} />
        <ellipse cx="40" cy="32" rx="18" ry="19" fill={skinC} />
        <Hair style={hairStyle} color={hairC} />
        <g className={styles.eyes}>
          <ellipse cx="33.5" cy="35.5" rx="1.9" ry="2.6" fill={INK} />
          <ellipse cx="46.5" cy="35.5" rx="1.9" ry="2.6" fill={INK} />
        </g>
        {o.glasses && <g fill="none" stroke="#3b3f48" strokeWidth="1"><circle cx="33.5" cy="35.5" r="4.2" /><circle cx="46.5" cy="35.5" r="4.2" /><path d="M37.7 35.5h4.6" /></g>}
        <circle cx="29" cy="41" r="2.6" fill="#f09a86" opacity=".28" />
        <circle cx="51" cy="41" r="2.6" fill="#f09a86" opacity=".28" />
        <path className={styles.mouth} d="M37.6 43.4 Q40 45.2 42.4 43.4" stroke="#8b4a3a" strokeWidth="1.2" fill="none" strokeLinecap="round" />
        <MoodFace mood={mood} skinC={skinC} />
        {o.hoodUp && <path d="M19 30 Q20 10 40 10 Q60 10 61 30 Q58 16 40 15 Q22 16 19 30 Z" fill={o.shade} />}
        {o.beanie && <path d="M20 26 Q20 7 40 7 Q60 7 60 26 L60 28 L20 28 Z" fill={o.beanie} />}
        <Hat kind={o.hat} cap={o.cap} brim={o.capBrim} />
      </g>
    </g>
  );
}

function Hair({ style, color }) {
  if (style === "messy") return <path d="M21 33 Q17 9 40 9 Q63 9 59 33 Q58 22 52 20 L50 26 L46 19 L42 25 L38 18 L34 25 L30 19 Q24 22 23 34 Z" fill={color} />;
  if (style === "long") return <path d="M21 36 Q19 11 40 11 Q61 11 59 36 Q58 24 50 20 Q46 27 34 26 Q27 26 23 34 Z" fill={color} />;
  if (style === "side") return <path d="M22 31 Q20 11 41 11 Q60 12 58 30 Q55 21 45 20 Q37 20 31 24 Q26 27 24 33 Z" fill={color} />;
  if (style === "grey") return <path d="M22 30 Q22 13 40 13 Q58 13 58 30 Q55 22 47 21 Q40 24 33 22 Q26 23 24 31 Z" fill={color} />;
  return <path d="M22 31 Q19 11 40 11 Q61 11 58 31 Q56 21 48 20 Q44 26 34 25 Q27 24 24 32 Z" fill={color} />;
}

/** Выражение лица поверх базового: нетрезвый (румянец, полуприкрытые глаза), агрессивный (брови), под веществами (тяжёлые веки). */
function MoodFace({ mood, skinC }) {
  if (mood === "drunk") {
    return (
      <g>
        <circle cx="29" cy="41" r="4.2" fill="#e8615a" opacity=".45" />
        <circle cx="51" cy="41" r="4.2" fill="#e8615a" opacity=".45" />
        <rect x="30" y="31" width="7" height="3.4" fill={skinC} />
        <rect x="43" y="31" width="7" height="3.4" fill={skinC} />
        <path d="M36.5 43 Q40 46.5 43.8 42.6" stroke="#8b4a3a" strokeWidth="1.3" fill="none" strokeLinecap="round" />
      </g>
    );
  }
  if (mood === "angry") {
    return (
      <g stroke={INK} strokeWidth="1.5" strokeLinecap="round">
        <path d="M30 30.5 L36.5 33" />
        <path d="M50 30.5 L43.5 33" />
        <path d="M37 45 Q40 42.8 43 45" stroke="#8b4a3a" fill="none" />
      </g>
    );
  }
  if (mood === "high" || mood === "unwell") {
    return (
      <g>
        <rect x="30" y="31.5" width="7" height="3.2" fill={skinC} />
        <rect x="43" y="31.5" width="7" height="3.2" fill={skinC} />
        <path d="M30.5 34.6 h6 M43.5 34.6 h6" stroke="#5a4038" strokeWidth=".8" />
        <path d="M31 38.5 Q33.5 39.5 36 38.5 M44 38.5 Q46.5 39.5 49 38.5" stroke="#9c8f9e" strokeWidth=".9" fill="none" opacity=".7" />
      </g>
    );
  }
  return null;
}

function Hat({ kind, cap, brim }) {
  if (kind === "rail" || kind === "police") {
    return (
      <g>
        <path d="M17 20 Q19 6 40 5.5 Q61 6 63 20 Q63 24 58 24.5 L22 24.5 Q17 24 17 20 Z" fill={kind === "police" ? "#34466e" : "#1c3170"} />
        <rect x="21.5" y="20.5" width="37" height="6" fill="#d6312f" />
        <path d="M22.5 26.5 L57.5 26.5 Q52 32 40 32 Q28 32 22.5 26.5 Z" fill="#15161a" />
        {kind === "rail"
          ? <path d="M40 12.5 l2 2.2 l5 -1.4 l-3.2 3.2 l-3.8 0.6 l-3.8 -0.6 l-3.2 -3.2 l5 1.4 z" fill="#f2f4f8" />
          : <circle cx="40" cy="15" r="2.6" fill="#e6c55a" />}
      </g>
    );
  }
  if (kind === "guard" || cap) {
    const c = cap || "#1d1e22";
    return (
      <g>
        <path d="M20.5 27 Q20.5 9 40 9 Q59.5 9 59.5 27 Z" fill={c} />
        <path d="M20 27 L60 27 Q58 32.5 40 31.5 Q23 31 20 27 Z" fill={kind === "guard" ? "#111214" : brim || "#4b573c"} />
        {kind === "guard" && <path d="M37.5 14.5 h5 v4 l-2.5 2.5 l-2.5 -2.5 z" fill="#aeb4bf" />}
      </g>
    );
  }
  return null;
}

// ---------------------------------------------------------------------------
// Профиль в движении (смотрит вправо)
// ---------------------------------------------------------------------------

function Leg({ o, back }) {
  const c = back ? o.pantsShade : o.pants;
  const k = back ? styles.back : styles.front;
  return (
    <g className={`${styles.thigh} ${k}`}>
      <rect x="34.5" y="98" width="11" height="40" rx="5.5" fill={c} />
      <g className={`${styles.shin} ${k}`}>
        <rect x="35" y="132" width="10" height="34" rx="5" fill={c} />
        {o.trim && !o.stripes && <rect x="35" y="157" width="10" height="2.4" fill={o.trim} />}
        <g className={`${styles.foot} ${k}`}>
          <path d="M34 164 Q34 171 38 171.5 L54 171.5 Q57.5 171.5 56.5 167.5 Q55.5 164.5 48 163.5 L45 160.5 L36 160.5 Z" fill={back ? darken(o.shoes) : o.shoes} />
          {o.sole && <rect x="34" y="170" width="23" height="2.2" rx="1.1" fill={o.sole} />}
        </g>
      </g>
    </g>
  );
}

function Arm({ o, skinC, back }) {
  const c = back ? o.shade : o.top;
  const k = back ? styles.back : styles.front;
  return (
    <g className={`${styles.upper} ${k}`}>
      <rect x="36.5" y="57" width="9" height="32" rx="4.5" fill={c} />
      <g className={`${styles.fore} ${k}`}>
        <rect x="37" y="84" width="8" height="23" rx="4" fill={c} />
        {o.trim && !o.stripes && <rect x="37" y="102" width="8" height="2.2" fill={o.trim} />}
        <circle cx="41" cy="109" r="4.4" fill={skinC} />
        {!back && o.briefcase && <g><rect x="35" y="112" width="15" height="13" rx="2" fill="#23252b" /></g>}
        {!back && o.bottle && <g><rect x="38" y="106" width="6" height="15" rx="2" fill="#3f6b3a" /><rect x="39.7" y="100" width="2.6" height="7" rx="1" fill="#3f6b3a" /></g>}
      </g>
    </g>
  );
}

function Side({ o, skinC, hairC, hairStyle }) {
  const long = hairStyle === "long";
  return (
    <g className={styles.lean}>
      <g className={styles.body}>
        <Arm o={o} skinC={skinC} back />
        <Leg o={o} back />
        {o.backpack && <rect x="20" y="60" width="13" height="36" rx="5" fill={o.backpack} />}
        {long && <path d="M26 30 Q22 58 30 74 L40 72 Q36 50 38 30 Z" fill={hairC} />}
        <Leg o={o} />
        {/* Корпус */}
        {o.coat ? (
          <path d="M30 61 Q30 55 37 55 L46 55 Q53 56 53 64 L55 139 Q55 142 52 142 L29 142 Q26 142 27 139 Z" fill={o.top} />
        ) : (
          <path d="M31 61 Q31 55 37 55 L46 55 Q52 56 52 63 L51 104 Q51 107 48 107 L34 107 Q31 107 31 104 Z" fill={o.top} />
        )}
        <path d="M31 61 Q31 55 37 55 L38 55 L36 106 L34 106 Q31 106 31 103 Z" fill={o.shade} opacity=".55" />
        {!o.hood && o.shirt !== o.top && <path d="M45 55 L52 56 L50 64 Z" fill={o.shirt} />}
        {o.tie && <path d="M49.6 58 L51.4 58 L51 74 L49.6 76 Z" fill={o.tie} />}
        {o.hood && <path d="M33 55 Q31 50 38 49 Q44 50 44 55 Z" fill={o.shade} />}
        {o.vest && <rect x="32" y="63" width="20" height="38" rx="3" fill={o.vest} />}
        {o.badge && !o.vest && !o.coat && <rect x="46" y="66" width="5" height="4" rx="1" fill="#eef2f8" />}
        {o.stethoscope && <path d="M46 56 Q51 68 48 80" fill="none" stroke="#3b3f48" strokeWidth="1.4" />}
        {(o.bag || o.handbag) && (
          <g>
            <path d="M46 56 L36 97" stroke={o.bag ? "#2d2e33" : o.handbag} strokeWidth="2.2" />
            <rect x="29" y="94" width="15" height="17" rx="3" fill={o.bag ? "#3a3b41" : o.handbag} />
          </g>
        )}
        {o.radio && <g><rect x="44" y="56" width="4.5" height="8" rx="1" fill="#111" /><rect x="46" y="49" width="1.3" height="8" fill="#111" /></g>}
        <Arm o={o} skinC={skinC} />
        {/* Голова */}
        <rect x="38" y="47" width="8.5" height="11" rx="3" fill={skinC} />
        <g className={styles.headSide}>
          <ellipse cx="43" cy="32" rx="17.5" ry="19" fill={skinC} />
          <path d="M60 35 Q62.8 37.5 60 40" fill={skinC} />
          <SideHair style={hairStyle} color={hairC} />
          <circle cx="37" cy="36" r="3.3" fill={skinC} />
          <path d="M36 34.5 Q38.2 36 36 38" stroke="#d99f84" strokeWidth=".9" fill="none" />
          <ellipse cx="53.2" cy="34.5" rx="1.8" ry="2.5" fill={INK} className={styles.eyes} />
          {o.glasses && <circle cx="53.2" cy="34.5" r="4" fill="none" stroke="#3b3f48" strokeWidth="1" />}
          <circle cx="54" cy="41" r="2.4" fill="#f09a86" opacity=".28" />
          <path d="M55 45 Q57.5 46 59 44.6" stroke="#8b4a3a" strokeWidth="1.1" fill="none" strokeLinecap="round" />
          {o.hoodUp && <path d="M24 38 Q20 9 44 8 Q64 9 63 30 Q56 16 44 16 Q30 16 28 40 Q27 50 24 38 Z" fill={o.top} />}
          {o.beanie && <path d="M25 27 Q24 8 43 8 Q60 8 61 25 L61 27 Z" fill={o.beanie} />}
          <SideHat kind={o.hat} cap={o.cap} brim={o.capBrim} />
        </g>
      </g>
    </g>
  );
}

function SideHair({ style, color }) {
  if (style === "messy") return <path d="M25 36 Q21 10 43 9 Q62 9 62 25 L57 21 L55 27 L51 20 L48 26 L44 20 Q37 26 34 38 Q30 42 26 40 Z" fill={color} />;
  if (style === "long") return <path d="M25 36 Q23 11 43 11 Q60 11 61 25 Q55 20 48 21 Q45 26 40 25 Q35 30 34 40 Q30 44 26 42 Z" fill={color} />;
  return <path d="M25.5 35 Q23 11 43 11 Q60 11 61 25 Q55 20 48 21 Q45 26 40 25 Q36 29 34 36 Q30 41 26.5 40 Z" fill={color} />;
}

function SideHat({ kind, cap, brim }) {
  if (kind === "rail" || kind === "police") {
    return (
      <g>
        <path d="M24 21 Q25 7 44 6.5 Q61 7 62 20 L62 25 L24 25 Z" fill={kind === "police" ? "#34466e" : "#1c3170"} />
        <rect x="24.5" y="20.5" width="37.5" height="6" fill="#d6312f" />
        <path d="M59 26.5 L70 27.5 Q67.5 31.5 59 30.5 Z" fill="#15161a" />
        {kind === "rail" ? <path d="M55 13.5 l3 1 l-3 1.8 l-3 -1.8 z" fill="#f2f4f8" /> : <circle cx="56" cy="15" r="2.2" fill="#e6c55a" />}
      </g>
    );
  }
  if (kind === "guard" || cap) {
    return (
      <g>
        <path d="M25 27 Q25 9.5 43 9.5 Q60 9.5 61 25 Z" fill={cap || "#1d1e22"} />
        <path d="M58 24 L71 26 Q69 30 58 28.5 Z" fill={kind === "guard" ? "#111214" : brim || "#4b573c"} />
      </g>
    );
  }
  return null;
}

function darken(hex) {
  const n = parseInt(hex.slice(1), 16);
  const f = (v) => Math.max(0, Math.round(v * 0.8));
  return `#${[(n >> 16) & 255, (n >> 8) & 255, n & 255].map(f).map((v) => v.toString(16).padStart(2, "0")).join("")}`;
}

// ---------------------------------------------------------------------------
// Пассажир в кресле
// ---------------------------------------------------------------------------

/** Пассажир в кресле (вид спереди). variant — детерминированная внешность. mood: calm|angry|unwell. */
export function SeatedPerson({ variant = 0, size = 96, kid = false, phone = false, mood = "calm", className }) {
  const o = lookOf(variant);
  const skinC = SKIN[(o.skinTone ?? variant * 3 + 1) % SKIN.length];
  const hairC = o.hair === "grey" ? HAIR[4] : HAIR[(variant * 5 + 2) % 4];
  const scale = kid ? 0.72 : 1;
  const long = o.hair === "long";
  return (
    <svg
      className={[styles.seated, mood !== "calm" ? styles[mood] : null, className].filter(Boolean).join(" ")}
      width={size * 0.7 * scale}
      height={size * scale}
      viewBox="0 0 70 100"
      aria-hidden="true"
      style={{ "--breath-delay": `${(variant % 7) * -0.6}s` }}
    >
      <g className={styles.torsoSeat}>
        {long && <path d="M20 22 Q18 44 22 56 L48 56 Q52 44 50 22 Z" fill={hairC} />}
        {o.hoodUp && <path d="M17 26 Q16 2 35 2 Q54 2 53 26 Q53 38 45 40 L25 40 Q17 38 17 26 Z" fill={o.top} />}
        {/* Ноги к зрителю */}
        <rect x="18" y="71" width="15" height="12" rx="5" fill={o.pants} />
        <rect x="37" y="71" width="15" height="12" rx="5" fill={o.pantsShade} />
        <rect x="19.5" y="81" width="12" height="15" rx="4.5" fill={o.pants} />
        <rect x="38.5" y="81" width="12" height="15" rx="4.5" fill={o.pantsShade} />
        <rect x="17" y="93.5" width="15" height="5.5" rx="2.7" fill={o.shoes} />
        <rect x="38" y="93.5" width="15" height="5.5" rx="2.7" fill={o.shoes} />
        {/* Корпус */}
        <path d="M16 50 Q16 38 26 38 L44 38 Q54 38 54 50 L53 76 L17 76 Z" fill={o.top} />
        <path d="M44 38 Q54 38 54 50 L53 76 L46 76 Z" fill={o.shade} opacity=".7" />
        {o.innerTop && <path d="M28 38 L42 38 L41 76 L29 76 Z" fill={o.innerTop} />}
        {o.hood ? <path d="M25 38 Q35 47 45 38 Q43 34 35 34 Q27 34 25 38 Z" fill={o.innerTop ? "#b8bcc4" : o.shade} />
          : o.innerTop ? null : <path d="M29.5 38 L35 50 L40.5 38 Z" fill={o.shirt || o.top} />}
        {o.trackStripes && <><rect x="20" y="72" width="1.4" height="24" fill="#e9eaee" /><rect x="48.6" y="72" width="1.4" height="24" fill="#e9eaee" /></>}
        {o.tie && <path d="M34 40 L36 40 L36.8 53 L35 56 L33.2 53 Z" fill={o.tie} />}
        {o.headphones && <path d="M25 40 Q35 47 45 40" fill="none" stroke="#1b1c21" strokeWidth="2.6" strokeLinecap="round" />}
        {/* Руки */}
        <rect x="10" y="44" width="9" height="28" rx="4.5" fill={o.top} />
        <rect x="51" y="44" width="9" height="28" rx="4.5" fill={o.shade} />
        <circle cx="16" cy="72" r="4" fill={skinC} />
        <circle cx="54" cy="72" r="4" fill={skinC} />
        {o.backpack && <rect x="21" y="60" width="28" height="18" rx="5" fill={o.backpack} />}
        {o.handbag && <rect x="42" y="62" width="14" height="12" rx="3" fill={o.handbag} />}
        {o.briefcase && <rect x="22" y="64" width="26" height="12" rx="2" fill="#23252b" />}
        {phone && <rect className={styles.phone} x="30" y="62" width="10" height="14" rx="2" fill="#1c1c1e" />}
        {o.bottle && <g><rect x="50" y="60" width="6.5" height="15" rx="2" fill="#3f6b3a" /><rect x="51.9" y="54" width="2.7" height="7" rx="1" fill="#3f6b3a" /></g>}
        <rect x="31.5" y="31" width="7" height="8" rx="3" fill={skinC} />
        {/* Голова */}
        <g className={styles.seatHead}>
          <circle cx="21.5" cy="23" r="2.8" fill={skinC} />
          <circle cx="48.5" cy="23" r="2.8" fill={skinC} />
          <ellipse cx="35" cy="21" rx="13.5" ry="14" fill={skinC} />
          <SeatHair style={o.hair} color={hairC} />
          {o.hoodUp && <path d="M19 18 Q20 5 35 5 Q50 5 51 18 Q48 9 35 9 Q22 9 19 18 Z" fill={o.shade} />}
          {o.beanie && <path d="M21 16 Q21 3 35 3 Q49 3 49 16 L49 18 L21 18 Z" fill={o.beanie} />}
          {o.cap && <><path d="M21 17 Q21 5 35 5 Q49 5 49 17 Z" fill={o.cap} /><path d="M21 17 L49 17 Q47 21 35 20.5 Q23 20 21 17 Z" fill={o.capBrim || "#4b573c"} /></>}
          {mood === "drunk" ? (
            <g>
              <path d="M28.5 23.8 Q30.5 22.4 32.5 23.8" stroke={INK} strokeWidth="1.3" fill="none" strokeLinecap="round" />
              <path d="M37.5 23.8 Q39.5 22.4 41.5 23.8" stroke={INK} strokeWidth="1.3" fill="none" strokeLinecap="round" />
              <path d="M31.5 29 Q35 32 38.8 28.4" stroke="#8b4a3a" strokeWidth="1.2" fill="none" strokeLinecap="round" />
              <circle cx="27" cy="27.5" r="3.2" fill="#e8615a" opacity=".5" />
              <circle cx="43" cy="27.5" r="3.2" fill="#e8615a" opacity=".5" />
              <circle cx="35" cy="25.5" r="1.4" fill="#e8615a" opacity=".45" />
            </g>
          ) : mood === "high" ? (
            <g>
              <ellipse cx="30.5" cy="24.2" rx="1.5" ry="1.2" fill={INK} />
              <ellipse cx="39.5" cy="24.2" rx="1.5" ry="1.2" fill={INK} />
              <path d="M28.5 23.3 h4 M37.5 23.3 h4" stroke="#5a4038" strokeWidth="1" />
              <path d="M28.8 26.6 Q30.5 27.4 32.2 26.6 M37.8 26.6 Q39.5 27.4 41.2 26.6" stroke="#9c8f9e" strokeWidth=".8" fill="none" />
              <path d="M33 30.2 h4" stroke="#8b4a3a" strokeWidth="1.1" strokeLinecap="round" />
            </g>
          ) : mood === "angry" ? (
            <g>
              <path d="M28 20 L32.5 22" stroke={INK} strokeWidth="1.3" strokeLinecap="round" />
              <path d="M42 20 L37.5 22" stroke={INK} strokeWidth="1.3" strokeLinecap="round" />
              <ellipse cx="30.5" cy="24.5" rx="1.4" ry="1.8" fill={INK} />
              <ellipse cx="39.5" cy="24.5" rx="1.4" ry="1.8" fill={INK} />
              <path d="M32 30.5 Q35 28.5 38 30.5" stroke="#8b4a3a" strokeWidth="1.2" fill="none" strokeLinecap="round" />
              <circle cx="27" cy="28" r="2.3" fill="#e0664f" opacity=".45" />
              <circle cx="43" cy="28" r="2.3" fill="#e0664f" opacity=".45" />
            </g>
          ) : (
            <g>
              <ellipse cx="30.5" cy="23.5" rx="1.4" ry="2" fill={INK} />
              <ellipse cx="39.5" cy="23.5" rx="1.4" ry="2" fill={INK} />
              {mood === "unwell"
                ? <path d="M32.5 30 Q35 29 37.5 30" stroke="#8b4a3a" strokeWidth="1.1" fill="none" strokeLinecap="round" />
                : <path d="M32.5 29 Q35 30.6 37.5 29" stroke="#8b4a3a" strokeWidth="1.1" fill="none" strokeLinecap="round" />}
              <circle cx="27" cy="27.5" r="2" fill="#f09a86" opacity=".28" />
              <circle cx="43" cy="27.5" r="2" fill="#f09a86" opacity=".28" />
            </g>
          )}
          {o.glasses && <g fill="none" stroke="#3b3f48" strokeWidth=".9"><circle cx="30.5" cy="23.5" r="3.3" /><circle cx="39.5" cy="23.5" r="3.3" /><path d="M33.8 23.5h2.4" /></g>}
        </g>
      </g>
    </svg>
  );
}

function SeatHair({ style, color }) {
  if (style === "messy") return <path d="M21 23 Q18 4 35 4 Q52 4 49 23 Q48 14 44 12 L42 17 L39 11 L36 16 L33 10 L30 16 L27 12 Q23 15 22.5 24 Z" fill={color} />;
  if (style === "long") return <path d="M21.5 24 Q20 6 35 6 Q50 6 48.5 24 Q47 15 41 13 Q37 18 29 17 Q24 18 23 25 Z" fill={color} />;
  if (style === "side") return <path d="M22 21 Q21 6 36 6 Q49 7 48 20 Q46 13 39 13 Q33 13 28 16 Q24 18 23.5 23 Z" fill={color} />;
  if (style === "grey") return <path d="M22 20 Q22 8 35 8 Q48 8 48 20 Q46 14 40 13 Q35 15 30 14 Q25 15 23.5 21 Z" fill={color} />;
  return <path d="M22 21.5 Q20 6 35 6 Q50 6 48 21.5 Q46 13 40 13 Q37 17 30 16.5 Q25 16 23.5 22 Z" fill={color} />;
}

/** Портрет пассажира для диалога — крупный план головы и плеч. */
export function PassengerBust({ variant = 0, size = 48, talking = false, mood = "calm" }) {
  return (
    <span className={`${styles.bust} ${talking ? styles.bustTalking : ""}`} style={{ width: size, height: size }} aria-hidden="true">
      <SeatedPerson variant={variant} size={size * 2.1} mood={mood} />
    </span>
  );
}

/** Портрет стоящего персонажа (проводник, медработник, охрана, полиция) для диалога. */
export function PersonBust({ outfit = "conductor", size = 48 }) {
  return (
    <span className={`${styles.bust} ${styles.bustPerson}`} style={{ width: size, height: size }} aria-hidden="true">
      <Person outfit={outfit} size={size * 3.1} hair={outfit === "medic" ? 1 : 0} />
    </span>
  );
}
