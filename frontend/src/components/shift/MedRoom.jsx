import { useId, useLayoutEffect, useRef, useState } from "react";
import { Person } from "../characters/People.jsx";
import styles from "./MedRoom.module.css";

/**
 * Помещение заступа на смену: медпункт (предрейсовый осмотр) или нарядная (инструктаж у
 * начальника поезда). Плоская иллюстрация в стиле вагона: сцена 800×700 условных единиц,
 * масштабируется «как cover» от нижней кромки — пол всегда внизу, персонажи растут на больших
 * экранах. Всё, что за пределами 800 по ширине, — продолжение стены и пола.
 *
 * props:
 *  - place: 'medpoint' | 'briefing'
 *  - npc: 'medic' | 'chief' — кто разговаривает с проводником
 *  - heroTalking, npcTalking
 *  - readout: null | { icon: 'thermometer'|'alert'|'stethoscope', text, bad } — показание прибора
 */
const W = 800;
const H = 700;
const PERSON = 320;
const FEET = 684;

export default function MedRoom({ place = "medpoint", npc = "medic", heroTalking = false, npcTalking = false, readout = null }) {
  const rootRef = useRef(null);
  const uid = useId().replace(/:/g, "");
  const [k, setK] = useState(0.5);

  useLayoutEffect(() => {
    const el = rootRef.current;
    if (!el) return undefined;
    function measure() {
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) return;
      // «cover» от нижней кромки, но без чрезмерной обрезки: по высоте видно не меньше 610
      // единиц (головы персонажей), по ширине — не меньше 600 (оба персонажа и стол).
      let s = Math.max(r.width / W, r.height / H);
      s = Math.min(s, r.height / 610, r.width / 600);
      setK(Math.round(s * 1000) / 1000);
    }
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const briefing = place === "briefing";
  return (
    <div ref={rootRef} className={styles.root} data-place={place}>
      <div className={styles.stage} style={{ width: W * k, height: H * k, marginLeft: (-W * k) / 2 }}>
        <svg className={styles.scene} width={W * k} height={H * k} viewBox={`0 0 ${W} ${H}`} aria-hidden="true">
          <defs>
            <pattern id={`tile-${uid}`} width="28" height="28" patternUnits="userSpaceOnUse">
              <rect width="28" height="28" className={styles.tile} />
              <path d="M28 0H0V28" fill="none" className={styles.tileLine} />
            </pattern>
            <pattern id={`floor-${uid}`} width="90" height="115" patternUnits="userSpaceOnUse" patternTransform="translate(0 585)">
              <rect width="90" height="115" className={styles.floor} />
              <path d="M0 0V115M0 38H90" fill="none" className={styles.floorLine} />
            </pattern>
            <radialGradient id={`dim-${uid}`} cx="400" cy="300" r="620" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#0a0f1c" stopOpacity="0" />
              <stop offset="0.38" stopColor="#0a0f1c" stopOpacity="0.12" />
              <stop offset="1" stopColor="#0a0f1c" stopOpacity="0.62" />
            </radialGradient>
            <radialGradient id={`warm-${uid}`} cx="400" cy="300" r="380" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#ffd9a0" stopOpacity="0.28" />
              <stop offset="1" stopColor="#ffd9a0" stopOpacity="0" />
            </radialGradient>
            <linearGradient id={`sky-${uid}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" style={{ stopColor: "var(--mr-sky-1)" }} />
              <stop offset="0.7" style={{ stopColor: "var(--mr-sky-2)" }} />
            </linearGradient>
            <linearGradient id={`glass-${uid}`} x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor="#fff" stopOpacity="0.5" />
              <stop offset="0.5" stopColor="#fff" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Оболочка комнаты: потолок, стена, пол — с запасом за края сцены */}
          <rect x="-1200" y="-800" width="3200" height="842" className={styles.ceil} />
          <rect x="-1200" y="40" width="3200" height="548" className={styles.wall} />
          <rect x="-1200" y="40" width="3200" height="8" className={styles.cornice} />
          {briefing
            ? <rect x="-1200" y="430" width="3200" height="155" className={styles.wainscot} />
            : <rect x="-1200" y="430" width="3200" height="155" fill={`url(#tile-${uid})`} />}
          <rect x="-1200" y="426" width="3200" height="5" className={styles.rail} />
          <rect x="-1200" y="585" width="3200" height="500" fill={`url(#floor-${uid})`} />
          <rect x="-1200" y="578" width="3200" height="8" className={styles.baseboard} />
          <rect x="-1200" y="586" width="3200" height="10" className={styles.floorShade} />

          {/* Потолочный светильник */}
          <rect x="300" y="40" width="200" height="10" rx="3" className={styles.lampBody} />
          <rect x="310" y="48" width="180" height="4" rx="2" className={styles.lampLight} />

          {briefing ? <Briefing uid={uid} readout={readout} /> : <Medpoint uid={uid} readout={readout} />}

          {/* Тёмная тема: приглушённый свет, тёплое пятно от светильника */}
          <rect x="-1200" y="-800" width="3200" height="1900" fill={`url(#dim-${uid})`} className={styles.dim} />
          <rect x="-1200" y="-800" width="3200" height="1900" fill={`url(#warm-${uid})`} className={styles.warm} />
        </svg>

        <div className={styles.hero} style={{ left: (200 - (PERSON * 80) / 360) * k, top: (FEET - PERSON) * k }}>
          <Person outfit="conductor" facing="right" size={PERSON * k} talking={heroTalking} />
        </div>
        <div className={styles.npc} key={npc} style={{ left: (600 - (PERSON * 80) / 360) * k, top: (FEET - PERSON) * k }}>
          <Person outfit={npc} facing="left" hair={npc === "medic" ? 1 : 2} skin={npc === "medic" ? 4 : 1} size={PERSON * k} talking={npcTalking} />
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Общие детали
// ---------------------------------------------------------------------------

function useNow() {
  const [now] = useState(() => new Date());
  return now;
}

/** Настенные часы: стрелки по текущему времени, секундная идёт (кроме «меньше движения»). */
function Clock({ x, y, r = 30 }) {
  const now = useNow();
  const sec = now.getSeconds();
  const min = now.getMinutes() + sec / 60;
  const hr = (now.getHours() % 12) + min / 60;
  return (
    <g transform={`translate(${x} ${y})`}>
      <circle r={r + 3} className={styles.clockRim} />
      <circle r={r} className={styles.clockFace} />
      {Array.from({ length: 12 }, (_, i) => (
        <rect key={i} x="-1.2" y={-r + 3} width="2.4" height={i % 3 ? 4 : 7} rx="1" className={styles.clockTick} transform={`rotate(${i * 30})`} />
      ))}
      <rect x="-2" y={-r * 0.5} width="4" height={r * 0.58} rx="2" className={styles.clockHand} transform={`rotate(${hr * 30})`} />
      <rect x="-1.5" y={-r * 0.78} width="3" height={r * 0.86} rx="1.5" className={styles.clockHand} transform={`rotate(${min * 6})`} />
      <g transform={`rotate(${sec * 6})`}>
        <g className={styles.second}>
          <rect x="-0.8" y={-r * 0.84} width="1.6" height={r} className={styles.clockSec} />
          <rect x="-0.8" y={r * 0.16} width="1.6" height={r * 0.68} fill="none" />
        </g>
      </g>
      <circle r="2.6" className={styles.clockSecDot} />
    </g>
  );
}

function Plate({ x, y, w, text }) {
  return (
    <g>
      <rect x={x - w / 2} y={y} width={w} height="34" rx="8" className={styles.plate} />
      <text x={x} y={y + 23} textAnchor="middle" className={styles.plateText}>{text}</text>
    </g>
  );
}

function ReadoutIcon({ icon, x, y, color }) {
  if (icon === "thermometer") {
    return (
      <g transform={`translate(${x} ${y})`} fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round">
        <path d="M-3 4V-9a3 3 0 0 1 6 0V4a6 6 0 1 1-6 0Z" />
        <path d="M0 -2V6" />
      </g>
    );
  }
  if (icon === "alert") {
    return (
      <g transform={`translate(${x} ${y})`} fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M0 -10L10 8H-10Z" />
        <path d="M0 -3V2M0 5.5V5.6" />
      </g>
    );
  }
  return (
    <g transform={`translate(${x} ${y})`} fill="none" stroke={color} strokeWidth="2.4" strokeLinecap="round">
      <path d="M-7 -10V-2a6 6 0 0 0 12 0V-10" />
      <path d="M-1 4V6a6 6 0 0 0 12 0V2" />
      <circle cx="11" cy="0" r="2.4" />
    </g>
  );
}

/** Окно: небо, перрон, поезд за стеклом. kind: 'platform' — вагон у платформы, 'nose' — головной вагон. */
function Window({ x, y, w, h, kind, uid }) {
  const clip = `win-${kind}-${uid}`;
  const py = y + h * 0.72;
  return (
    <g>
      <defs><clipPath id={clip}><rect x={x} y={y} width={w} height={h} /></clipPath></defs>
      <rect x={x - 8} y={y - 8} width={w + 16} height={h + 16} rx="6" className={styles.winFrame} />
      <g clipPath={`url(#${clip})`}>
        <rect x={x} y={y} width={w} height={h} fill={`url(#sky-${uid})`} />
        {/* Навес вокзала */}
        <path d={`M${x - 10} ${y + 20} L${x + w + 10} ${y + 8} L${x + w + 10} ${y + 26} L${x - 10} ${y + 36} Z`} className={styles.canopy} />
        {[0.12, 0.42, 0.72].map((k) => <rect key={k} x={x + w * k} y={y + 28} width="5" height={h} className={styles.column} />)}
        {kind === "platform" ? (
          <g>
            {/* Вагон у платформы: белый кузов, синяя полоса, ряд окон */}
            <rect x={x - 20} y={py - 74} width={w + 40} height="76" rx="10" className={styles.train} />
            <rect x={x - 20} y={py - 30} width={w + 40} height="8" className={styles.trainStripe} />
            {Array.from({ length: 6 }, (_, i) => (
              <rect key={i} x={x - 6 + i * 44} y={py - 62} width="34" height="22" rx="5" className={styles.trainWin} />
            ))}
            <rect x={x + w * 0.62} y={py - 66} width="26" height="62" rx="4" className={styles.trainDoor} />
          </g>
        ) : (
          <g>
            {/* Головной вагон скоростного поезда — обобщённый силуэт */}
            <path d={`M${x - 20} ${py - 66} H${x + w * 0.5} C${x + w * 0.78} ${py - 66} ${x + w * 0.96} ${py - 36} ${x + w + 4} ${py - 6} V${py + 2} H${x - 20} Z`} className={styles.train} />
            <path d={`M${x + w * 0.52} ${py - 62} C${x + w * 0.7} ${py - 60} ${x + w * 0.8} ${py - 50} ${x + w * 0.86} ${py - 38} H${x + w * 0.56} Z`} className={styles.trainCab} />
            <path d={`M${x - 20} ${py - 26} H${x + w * 0.9} L${x + w * 0.96} ${py - 18} H${x - 20} Z`} className={styles.trainStripe} />
            {Array.from({ length: 3 }, (_, i) => (
              <rect key={i} x={x - 4 + i * 38} y={py - 56} width="28" height="18" rx="4" className={styles.trainWin} />
            ))}
            <circle cx={x + w * 0.95} cy={py - 10} r="3" className={styles.headlight} />
          </g>
        )}
        <rect x={x} y={py} width={w} height={h} className={styles.platform} />
        <rect x={x} y={py} width={w} height="4" className={styles.platformEdge} />
        <rect x={x} y={y} width={w} height={h} fill={`url(#glass-${uid})`} opacity="0.5" />
      </g>
      <rect x={x + w / 2 - 3} y={y} width="6" height={h} className={styles.winFrame} />
      <rect x={x - 14} y={y + h + 6} width={w + 28} height="10" rx="3" className={styles.sill} />
    </g>
  );
}

function Plant({ x, y, s = 1 }) {
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <path d="M0 -14C-16 -40 -30 -46 -34 -60C-18 -58 -6 -44 0 -24Z" fill="#5f9f6e" />
      <path d="M0 -14C12 -44 24 -54 34 -64C36 -46 20 -30 2 -18Z" fill="#4c8a5c" />
      <path d="M-2 -16C-6 -46 -2 -64 6 -78C14 -60 10 -40 2 -16Z" fill="#6fb07c" />
      <path d="M-16 -16H16L12 12H-12Z" className={styles.pot} />
      <rect x="-18" y="-18" width="36" height="6" rx="2" className={styles.potRim} />
    </g>
  );
}

// ---------------------------------------------------------------------------
// Медпункт
// ---------------------------------------------------------------------------

function Medpoint({ uid, readout }) {
  const bad = !!(readout && readout.bad);
  const text = readout ? String(readout.text) : "0,00 мг/л";
  const cut = text.search(/\s/);
  const value = cut > 0 ? text.slice(0, cut) : text;
  const unit = cut > 0 ? text.slice(cut + 1).trim() : "";
  return (
    <g>
      <Plate x={400} y={82} w={330} text="Медпункт · Ленинградский вокзал" />
      <Clock x={612} y={112} r={28} />

      {/* Шкаф со стеклянными дверцами */}
      <g>
        <rect x="6" y="258" width="120" height="324" rx="6" className={styles.cabinet} />
        <rect x="14" y="266" width="104" height="186" rx="3" className={styles.cabGlass} />
        {[322, 386].map((sy) => <rect key={sy} x="14" y={sy} width="104" height="4" className={styles.cabShelf} />)}
        {/* Полки: коробки и флаконы */}
        <rect x="20" y="292" width="26" height="30" rx="2" fill="#e9eef6" />
        <rect x="20" y="300" width="26" height="6" fill="#4b86d6" />
        <rect x="50" y="300" width="20" height="22" rx="2" fill="#f4e2c6" />
        <rect x="74" y="284" width="16" height="38" rx="3" fill="#b8d6ef" />
        <rect x="77" y="279" width="10" height="7" rx="2" fill="#ffffff" />
        <rect x="94" y="296" width="18" height="26" rx="2" fill="#ffffff" />
        <path d="M100 303h6v4h4v6h-4v4h-6v-4h-4v-6h4Z" fill="#d93025" />
        <rect x="20" y="360" width="12" height="26" rx="3" fill="#8a5a3a" />
        <rect x="22" y="355" width="8" height="6" rx="1" fill="#f2f2f2" />
        <rect x="36" y="364" width="12" height="22" rx="3" fill="#d6e7f5" />
        <rect x="52" y="352" width="32" height="34" rx="2" fill="#ffffff" />
        <rect x="52" y="362" width="32" height="6" fill="#2aa7e8" />
        <rect x="88" y="366" width="24" height="20" rx="2" fill="#f0d9d6" />
        <rect x="20" y="420" width="40" height="32" rx="2" fill="#e8edf4" />
        <rect x="20" y="430" width="40" height="5" fill="#d93025" />
        <rect x="64" y="428" width="18" height="24" rx="4" fill="#9fc9e8" />
        <rect x="86" y="424" width="26" height="28" rx="2" fill="#f7f1e4" />
        <rect x="14" y="266" width="104" height="186" rx="3" fill={`url(#glass-${uid})`} opacity="0.7" />
        <rect x="64.5" y="266" width="3" height="186" className={styles.cabinet} />
        <rect x="14" y="460" width="50" height="116" rx="3" className={styles.cabDoor} />
        <rect x="68" y="460" width="50" height="116" rx="3" className={styles.cabDoor} />
        <rect x="56" y="508" width="4" height="18" rx="2" className={styles.handle} />
        <rect x="72" y="508" width="4" height="18" rx="2" className={styles.handle} />
      </g>

      {/* Таблица для проверки зрения */}
      <g>
        <rect x="166" y="158" width="96" height="150" rx="4" className={styles.paper} />
        {[
          ["Ш Б", 20, 188],
          ["М Н К", 15, 214],
          ["Ы М Б Ш", 12, 236],
          ["Б Ы Н К М", 10, 254],
          ["И Н Ш М К", 8, 270],
          ["Н Ш Ы И К Б", 7, 284],
          ["Ш И Н Б К Ы М", 6, 296]
        ].map(([t, fs, ty]) => <text key={t} x="214" y={ty} textAnchor="middle" fontSize={fs} className={styles.chart}>{t}</text>)}
      </g>

      {/* Кушетка с одноразовой простынёй */}
      <g>
        <rect x="136" y="458" width="18" height="70" rx="6" className={styles.couchHead} />
        <rect x="140" y="500" width="170" height="26" rx="8" className={styles.couch} />
        <rect x="150" y="520" width="150" height="6" className={styles.couchShade} />
        <rect x="186" y="496" width="100" height="10" rx="3" className={styles.sheet} />
        <rect x="150" y="484" width="44" height="18" rx="9" className={styles.pillow} />
        <circle cx="300" cy="494" r="10" className={styles.sheet} />
        <circle cx="300" cy="494" r="3.5" className={styles.couchShade} />
        <rect x="150" y="526" width="8" height="56" className={styles.couchLeg} />
        <rect x="292" y="526" width="8" height="56" className={styles.couchLeg} />
      </g>

      <Window x={326} y={160} w={160} h={196} kind="platform" uid={uid} />
      <Plant x={456} y={360} s={0.62} />

      {/* Плакат «Предрейсовый осмотр» */}
      <g>
        <rect x="532" y="178" width="118" height="156" rx="6" className={styles.poster} />
        <rect x="532" y="178" width="118" height="38" rx="6" className={styles.posterHead} />
        <rect x="532" y="206" width="118" height="10" className={styles.posterHead} />
        <text x="591" y="194" textAnchor="middle" className={styles.posterTitle}>Предрейсовый</text>
        <text x="591" y="208" textAnchor="middle" className={styles.posterTitle}>осмотр</text>
        {[["Давление", "stethoscope"], ["Температура", "thermometer"], ["Алкотест", "alert"], ["Жалобы", "stethoscope"]].map(([t], i) => (
          <g key={t}>
            <circle cx="548" cy={236 + i * 24} r="7" className={styles.posterDot} />
            <text x="548" y={239.5 + i * 24} textAnchor="middle" className={styles.posterNum}>{i + 1}</text>
            <text x="562" y={240 + i * 24} className={styles.posterText}>{t}</text>
          </g>
        ))}
        <rect x="544" y="324" width="94" height="3" rx="1.5" className={styles.posterDot} opacity="0.35" />
      </g>

      {/* Раковина с зеркалом, антисептик */}
      <g>
        <rect x="690" y="286" width="92" height="110" rx="10" className={styles.mirror} />
        <path d="M700 300l24 -8M700 318l40 -14" className={styles.mirrorShine} />
        <rect x="686" y="440" width="100" height="18" rx="9" className={styles.sink} />
        <rect x="730" y="420" width="6" height="22" rx="3" className={styles.tap} />
        <rect x="730" y="420" width="22" height="6" rx="3" className={styles.tap} />
        <rect x="702" y="458" width="68" height="124" rx="4" className={styles.cabDoor} />
        <rect x="733" y="500" width="6" height="20" rx="3" className={styles.handle} />
        <rect x="796" y="386" width="30" height="58" rx="7" className={styles.dispenser} />
        <rect x="802" y="400" width="18" height="12" rx="3" fill="#6fb7e8" />
        <rect x="806" y="444" width="10" height="6" rx="2" className={styles.handle} />
      </g>

      {/* Стол медработника */}
      <g>
        <rect x="302" y="486" width="218" height="14" rx="4" className={styles.deskTop} />
        <rect x="312" y="500" width="10" height="82" className={styles.deskLeg} />
        <rect x="440" y="500" width="72" height="82" rx="3" className={styles.deskDrawer} />
        <rect x="468" y="516" width="16" height="4" rx="2" className={styles.handle} />
        <rect x="468" y="548" width="16" height="4" rx="2" className={styles.handle} />
      </g>

      {/* Монитор с журналом осмотров */}
      <g>
        <rect x="468" y="470" width="26" height="6" rx="2" className={styles.devBody} />
        <rect x="477" y="450" width="8" height="22" className={styles.devBody} />
        <rect x="430" y="382" width="100" height="72" rx="6" className={styles.monitor} />
        <rect x="436" y="388" width="88" height="58" rx="3" className={styles.screen} />
        <rect x="442" y="394" width="40" height="5" rx="2" className={styles.screenLine} />
        <rect x="442" y="404" width="72" height="4" rx="2" className={styles.screenDim} />
        <rect x="442" y="412" width="60" height="4" rx="2" className={styles.screenDim} />
        <rect x="442" y="420" width="66" height="4" rx="2" className={styles.screenDim} />
        <rect x="442" y="430" width="30" height="4" rx="2" className={styles.screenDim} />
        <rect x="474" y="429" width="3" height="7" className={styles.cursor} />
      </g>

      {/* Терминал предрейсового осмотра: алкотестер с дисплеем */}
      <g>
        <rect x="320" y="400" width="108" height="86" rx="10" className={styles.devBody} />
        <rect x="326" y="406" width="96" height="46" rx="6" className={styles.display} data-bad={bad || undefined} />
        {readout && <ReadoutIcon icon={readout.icon} x={339} y={429} color={bad ? "#ff6b5f" : "#7fe0a6"} />}
        <text x={readout ? 384 : 374} y="437" textAnchor="middle" className={styles.displayText} data-bad={bad || undefined}>
          {value}{unit && <tspan className={styles.displayUnit} dx="4">{unit}</tspan>}
        </text>
        <circle cx="336" cy="467" r="5" className={styles.devBtn} />
        <circle cx="352" cy="467" r="5" className={styles.devBtnAlt} />
        <rect x="366" y="462" width="44" height="10" rx="5" className={styles.devSlot} />
        {/* Мундштук на гибкой трубке */}
        <path d="M428 440C446 440 446 470 432 478" className={styles.hose} />
      </g>

      {/* Тонометр: манжета и груша */}
      <g>
        <rect x="436" y="472" width="30" height="14" rx="7" className={styles.cuff} />
        <path d="M466 479C478 479 482 472 494 474" className={styles.hose} />
        <circle cx="503" cy="474" r="9" className={styles.gauge} />
        <path d="M503 474l4 -5" stroke="#1d1f24" strokeWidth="1.6" strokeLinecap="round" />
        <ellipse cx="452" cy="469" rx="8" ry="5" fill="#2e3a52" />
      </g>

      {/* Термометр */}
      <g transform="rotate(-8 336 480)">
        <rect x="316" y="477" width="42" height="7" rx="3.5" className={styles.thermo} />
        <rect x="346" y="478" width="10" height="5" rx="2" className={styles.thermoTip} />
      </g>

    </g>
  );
}

// ---------------------------------------------------------------------------
// Нарядная
// ---------------------------------------------------------------------------

function Briefing({ uid, readout }) {
  const bad = !!(readout && readout.bad);
  return (
    <g>
      <Plate x={400} y={82} w={330} text="Нарядная · Ленинградский вокзал" />
      <Clock x={186} y={112} r={28} />

      {/* Шкафчики бригады */}
      <g>
        {[0, 1, 2].map((i) => (
          <g key={i}>
            <rect x={6 + i * 42} y="250" width="40" height="330" rx="4" className={styles.locker} />
            {[0, 1, 2, 3].map((k) => <rect key={k} x={14 + i * 42} y={266 + k * 7} width="24" height="3" rx="1.5" className={styles.lockerVent} />)}
            <rect x={36 + i * 42} y="380" width="4" height="22" rx="2" className={styles.handle} />
            <rect x={14 + i * 42} y="304" width="18" height="12" rx="2" className={styles.lockerTag} />
            <text x={23 + i * 42} y="313.5" textAnchor="middle" className={styles.lockerNum}>{i + 11}</text>
          </g>
        ))}
      </g>

      <Window x={150} y={170} w={146} h={180} kind="nose" uid={uid} />

      {/* Схема маршрута */}
      <g>
        <rect x="316" y="150" width="200" height="190" rx="8" className={styles.map} />
        <text x="330" y="172" className={styles.mapTitle}>Маршрут</text>
        <path d="M326 190L338 196" className={styles.mapWater} />
        <path d="M344 202C350 222 352 230 360 240S380 262 392 278S410 300 420 316" className={styles.mapLine} />
        {[[344, 202, "Санкт-Петербург"], [360, 240, "Великий Новгород"], [392, 278, "Тверь"], [420, 316, "Москва"]].map(([cx, cy, t]) => (
          <g key={t}>
            <circle cx={cx} cy={cy} r="6" className={styles.mapStop} />
            <circle cx={cx} cy={cy} r="2.4" className={styles.mapLineDot} />
            <text x={cx + 12} y={cy + 1} className={styles.mapLabel}>{t}</text>
          </g>
        ))}
      </g>

      {/* Табло: номер поезда и отправление */}
      <g>
        <rect x="534" y="170" width="152" height="130" rx="8" className={styles.board} />
        <text x="550" y="194" className={styles.boardDim}>Поезд</text>
        <text x="550" y="220" className={styles.boardBig}>№ 752</text>
        <text x="550" y="242" className={styles.boardDim}>Москва → С.-Петербург</text>
        <rect x="550" y="252" width="122" height="1" className={styles.boardRule} />
        <text x="550" y="274" className={styles.boardDim}>Отпр.</text>
        <text x="672" y="276" textAnchor="end" className={styles.boardTime}>07:40</text>
        <text x="550" y="290" className={styles.boardDim}>Путь 3</text>
      </g>

      {/* Вешалка с пальто и фуражкой */}
      <g>
        <rect x="738" y="300" width="6" height="282" rx="3" className={styles.rack} />
        <rect x="716" y="578" width="50" height="6" rx="3" className={styles.rack} />
        <path d="M722 300h44" className={styles.rackArm} />
        <path d="M722 316C712 330 708 380 712 450H770C774 380 770 330 760 316Z" fill="#1c3170" />
        <path d="M741 316V450" stroke="#132457" strokeWidth="2" />
        <circle cx="735" cy="350" r="2.4" fill="#d6b35a" />
        <circle cx="735" cy="372" r="2.4" fill="#d6b35a" />
        <path d="M720 294C722 282 760 282 764 294Z" fill="#1c3170" />
        <rect x="716" y="292" width="52" height="6" rx="3" fill="#132457" />
      </g>
      <Plant x={690} y={580} s={0.9} />

      {/* Стол с документами и планшетом */}
      <g>
        <rect x="300" y="486" width="220" height="14" rx="4" className={styles.deskTop} />
        <rect x="312" y="500" width="10" height="82" className={styles.deskLeg} />
        <rect x="498" y="500" width="10" height="82" className={styles.deskLeg} />
        {/* Стопка бумаг и папка */}
        <rect x="318" y="476" width="64" height="10" rx="2" fill="#2d5bb0" transform="rotate(-2 350 480)" />
        <rect x="322" y="470" width="58" height="8" rx="1" fill="#f6f7f9" />
        <rect x="324" y="465" width="58" height="6" rx="1" fill="#eceef2" transform="rotate(3 352 468)" />
        {/* Планшет на подставке */}
        <path d="M396 486L404 420H474L470 486Z" className={styles.tablet} />
        <path d="M404 426H468L464 478H400Z" className={styles.tabletScreen} data-bad={bad || undefined} />
        {readout ? (
          <text x="434" y="458" textAnchor="middle" className={styles.tabletText} data-bad={bad || undefined}>{readout.text}</text>
        ) : (
          <g>
            <rect x="410" y="434" width="30" height="4" rx="2" className={styles.screenLine} />
            <rect x="410" y="444" width="48" height="3" rx="1.5" className={styles.screenDim} />
            <rect x="410" y="451" width="40" height="3" rx="1.5" className={styles.screenDim} />
            <rect x="409" y="458" width="44" height="3" rx="1.5" className={styles.screenDim} />
            <rect x="408" y="467" width="18" height="6" rx="3" className={styles.screenLine} />
          </g>
        )}
        {/* Кружка */}
        <path d="M484 464H504V484C504 486 502 488 500 488H488C486 488 484 486 484 484Z" fill="#ffffff" stroke="#d5dbe4" strokeWidth="1.5" />
        <path d="M504 470C512 470 512 480 504 480" fill="none" stroke="#d5dbe4" strokeWidth="3" />
        <rect x="484" y="472" width="20" height="4" fill="#0b3d91" />
      </g>
    </g>
  );
}
