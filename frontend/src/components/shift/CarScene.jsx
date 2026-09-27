import { useEffect, useLayoutEffect, useRef, useState } from "react";
import Icon from "../ui/Icon.jsx";
import { Person, SeatedPerson } from "../characters/People.jsx";
import { VESTIBULE, seatX, worldWidth } from "../../shift/shiftModel.js";
import styles from "./CarScene.module.css";

/**
 * Вагон в разрезе («кукольный домик», как в The Sims): стена с окнами, за окнами бежит пейзаж,
 * в креслах сидят пассажиры, по проходу ходит проводник.
 *
 * Управление: тап по полу — идти в точку; тап по пассажиру/точке осмотра — подойти и
 * взаимодействовать; удержание ◀ ▶ или стрелки клавиатуры — идти; Enter/пробел — действие рядом.
 * Позиция и камера живут в ref и обновляются в requestAnimationFrame — без ре-рендеров на кадр.
 *
 * props:
 *  - cls: класс вагона (CAR_CLASSES[...]), passengers: [{ seat, variant, kid, phone }]
 *  - hotspots: [{ key, x, icon, title, state: 'idle'|'ok'|'fault' }]
 *  - signals: [{ key, seat, urgent, remaining, total }]
 *  - moving: поезд идёт (пейзаж бежит), stationName: платформа за окном на стоянке
 *  - disabled: ввод выключен (открыт диалог), onInteract({ type: 'hotspot'|'signal', key })
 */
const H = 300;
const SPEED = 140; // px/с мира
const REACH = 58;

export default function CarScene({ cls, passengers, hotspots = [], signals = [], moods = {}, visitors = [], walkers = [], onWalkerArrive, speed = 1, moving = false, stationName, disabled = false, onInteract, focusSeat = null }) {
  const W = worldWidth(cls);
  const sigX = (sg) => (sg.x === "vestibule" ? W - VESTIBULE * 0.62 : typeof sg.x === "number" ? sg.x : seatX(cls, sg.seat));
  const viewRef = useRef(null);
  const worldRef = useRef(null);
  const heroRef = useRef(null);
  const sim = useRef({ x: VESTIBULE * 0.55, target: null, dir: 0, action: null, cam: 0, scale: 1, viewW: 390, last: 0 });
  const [pose, setPose] = useState({ walking: false, facing: "right" });
  const [near, setNear] = useState(null);
  const [edges, setEdges] = useState({ left: null, right: null });
  const propsRef = useRef({});
  propsRef.current = { hotspots, signals, disabled, onInteract };

  // Масштаб сцены под высоту контейнера.
  useLayoutEffect(() => {
    const el = viewRef.current;
    function measure() {
      const r = el.getBoundingClientRect();
      // Масштаб: видно ~300 px мира по ширине (3–4 кресла на телефоне), но не выше контейнера.
      const scale = Math.max(0.72, Math.min(1.7, r.height / H, r.width / 300));
      const oy = Math.max(0, (r.height - H * scale) * 0.42);
      sim.current.scale = scale;
      sim.current.oy = oy;
      sim.current.viewW = r.width / scale;
      el.style.setProperty("--s", scale.toFixed(3));
      el.style.setProperty("--oy", `${oy.toFixed(1)}px`);
      place();
    }
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [W]);

  function place() {
    const st = sim.current;
    const maxCam = W - st.viewW;
    const want = st.x - st.viewW / 2;
    const cam = maxCam <= 0 ? maxCam / 2 : Math.max(0, Math.min(maxCam, want));
    st.cam += (cam - st.cam) * (st.last ? 0.14 : 1);
    if (worldRef.current) worldRef.current.style.transform = `translate3d(${(-st.cam * st.scale).toFixed(2)}px,${(st.oy || 0).toFixed(1)}px,0) scale(${st.scale})`;
    if (heroRef.current) heroRef.current.style.transform = `translate3d(${(st.x - 33).toFixed(2)}px,0,0)`;
  }

  function interactables() {
    const { hotspots: hs, signals: sg } = propsRef.current;
    return [
      ...hs.map((h) => ({ type: "hotspot", key: h.key, x: h.x, label: h.state === "idle" ? `Проверить: ${h.title.toLowerCase()}` : null })),
      ...sg.map((s) => ({ type: "signal", key: s.key, x: sigX(s), label: s.urgent ? "Срочно: подойти к пассажиру" : "Подойти к пассажиру" }))
    ].filter((i) => i.label);
  }

  useEffect(() => {
    let raf = 0;
    let walkingShown = false;
    let facingShown = "right";
    let nearShown = null;
    let edgeShown = "";
    function frame(now) {
      const st = sim.current;
      const dt = st.last ? Math.min(0.05, (now - st.last) / 1000) : 0;
      st.last = now;
      let v = 0;
      if (!propsRef.current.disabled) {
        if (st.dir) { v = st.dir * SPEED; st.target = null; st.action = null; }
        else if (st.target !== null) {
          const d = st.target - st.x;
          if (Math.abs(d) < 2) {
            st.target = null;
            if (st.action) { const a = st.action; st.action = null; propsRef.current.onInteract && propsRef.current.onInteract(a); }
          } else v = Math.sign(d) * Math.min(SPEED, Math.abs(d) / dt || SPEED);
        }
      }
      st.x = Math.max(24, Math.min(W - 24, st.x + v * dt));
      place();
      const walking = Math.abs(v) > 1;
      const facing = v > 1 ? "right" : v < -1 ? "left" : facingShown;
      if (walking !== walkingShown || facing !== facingShown) {
        walkingShown = walking; facingShown = facing;
        setPose({ walking, facing });
      }
      const n = interactables().filter((i) => Math.abs(i.x - st.x) < REACH).sort((a, b) => Math.abs(a.x - st.x) - Math.abs(b.x - st.x))[0] || null;
      const nk = n ? `${n.type}:${n.key}:${n.label}` : null;
      if (nk !== nearShown) { nearShown = nk; setNear(n); }
      // Вызовы за краем экрана — стрелки по бокам, чтобы не искать пассажира вслепую.
      const e = { left: null, right: null };
      propsRef.current.signals.forEach((sg) => {
        const sx = sigX(sg);
        const side = sx < st.cam + 10 ? "left" : sx > st.cam + st.viewW - 10 ? "right" : null;
        if (side && (!e[side] || sg.urgent)) e[side] = { key: sg.key, urgent: sg.urgent, x: sx };
      });
      const ek = `${e.left ? e.left.key + e.left.urgent : ""}|${e.right ? e.right.key + e.right.urgent : ""}`;
      if (ek !== edgeShown) { edgeShown = ek; setEdges(e); }
      raf = requestAnimationFrame(frame);
    }
    raf = requestAnimationFrame(frame);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [W]);

  // Клавиатура
  useEffect(() => {
    function down(e) {
      if (propsRef.current.disabled) return;
      if (e.key === "ArrowLeft") { sim.current.dir = -1; e.preventDefault(); }
      else if (e.key === "ArrowRight") { sim.current.dir = 1; e.preventDefault(); }
      else if ((e.key === "Enter" || e.key === " ") && near) { e.preventDefault(); act(near); }
    }
    function up(e) {
      if (e.key === "ArrowLeft" || e.key === "ArrowRight") sim.current.dir = 0;
    }
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    return () => { window.removeEventListener("keydown", down); window.removeEventListener("keyup", up); };
  });

  function worldXFromEvent(e) {
    const r = viewRef.current.getBoundingClientRect();
    return (e.clientX - r.left) / sim.current.scale + sim.current.cam;
  }

  function goTo(x, action) {
    const st = sim.current;
    // Встаём рядом с объектом, а не в него: со стороны, откуда идём.
    const side = x > st.x ? -26 : 26;
    st.target = action ? Math.max(24, Math.min(W - 24, x + side)) : x;
    st.action = action || null;
    if (action && Math.abs(st.target - st.x) < 2) { st.target = st.x + 0.1 * Math.sign(side || 1); }
  }

  function act(item) {
    if (Math.abs(item.x - sim.current.x) < REACH) propsRef.current.onInteract && propsRef.current.onInteract({ type: item.type, key: item.key });
    else goTo(item.x, { type: item.type, key: item.key });
  }

  function onFloor(e) {
    if (disabled) return;
    goTo(worldXFromEvent(e));
  }

  const hold = (dir) => ({
    onPointerDown: (e) => { e.preventDefault(); if (!disabled) sim.current.dir = dir; },
    onPointerUp: () => { sim.current.dir = 0; },
    onPointerLeave: () => { sim.current.dir = 0; },
    onPointerCancel: () => { sim.current.dir = 0; }
  });

  const occupied = new Map(passengers.map((p) => [p.seat, p]));
  const seats = [];
  for (let i = 0; i < cls.seats; i += 1) seats.push(i);
  const winW = Math.min(cls.spacing - 22, 78);

  // Стена с вырезами окон (evenodd): за вырезами виден пейзаж.
  let wallPath = `M0 0H${W}V${H}H0Z`;
  seats.forEach((i) => {
    const cx = seatX(cls, i);
    wallPath += roundRect(cx - winW / 2, 52, winW, 72, 14);
  });
  // Окна в дверях тамбуров
  wallPath += roundRect(VESTIBULE * 0.5 - 16, 60, 32, 70, 8) + roundRect(W - VESTIBULE * 0.5 - 16, 60, 32, 70, 8);

  return (
    <div className={styles.view} ref={viewRef} data-class={cls.key}>
      <Landscape moving={moving} stationName={stationName} speed={speed} />

      <div className={styles.world} ref={worldRef} style={{ width: W, height: H }} onPointerDown={onFloor}>
        <svg className={styles.shell} width={W} height={H} viewBox={`0 0 ${W} ${H}`} aria-hidden="true">
          <path d={wallPath} fillRule="evenodd" className={styles.wall} />
          <rect x="0" y="0" width={W} height="20" className={styles.ceiling} />
          <rect x="0" y="18" width={W} height="3" className={styles.ceilingLight} />
          <rect x={VESTIBULE} y="36" width={W - VESTIBULE * 2} height="5" rx="2.5" className={styles.rack} />
          <rect x="0" y="146" width={W} height="96" className={styles.lowerWall} />
          <rect x="0" y="240" width={W} height="60" className={styles.floor} />
          <rect x={VESTIBULE} y="262" width={W - VESTIBULE * 2} height="16" className={styles.carpet} style={{ fill: cls.seat }} />
          {seats.map((i) => {
            const cx = seatX(cls, i);
            return <rect key={`f${i}`} x={cx - winW / 2 - 3} y="49" width={winW + 6} height="78" rx="16" className={styles.frame} />;
          })}
          {/* Тамбуры: перегородки и двери */}
          {[VESTIBULE, W - VESTIBULE].map((x, k) => (
            <g key={`v${k}`}>
              <rect x={x - 5} y="20" width="10" height="222" className={styles.partition} />
              <rect x={k ? x + 18 : x - 60} y="54" width="42" height="186" rx="6" className={styles.door} />
              <rect x={k ? x + 26 : x - 52} y="64" width="26" height="60" rx="5" className={styles.doorGlass} />
            </g>
          ))}
          <text x={VESTIBULE * 0.5} y="160" textAnchor="middle" className={styles.sign}>Тамбур</text>
          <text x={W - VESTIBULE * 0.5} y="160" textAnchor="middle" className={styles.sign}>Вагон {cls.car}</text>
          {seats.map((i) => <Seat key={`s${i}`} cls={cls} x={seatX(cls, i)} />)}
        </svg>

        {passengers.map((p) => (
          <div key={`p${p.seat}`} className={styles.passenger} data-focus={focusSeat === p.seat || undefined} style={{ left: seatX(cls, p.seat) - 35 * (p.kid ? 0.72 : 1), top: p.kid ? 170 : 142 }}>
            <SeatedPerson variant={p.variant} kid={p.kid} phone={p.phone && !moods[p.seat]} mood={moods[p.seat] || "calm"} size={100} />
          </div>
        ))}

        {cls.tables && seats.map((i) => (
          <svg key={`t${i}`} className={styles.table} style={{ left: seatX(cls, i) + cls.spacing / 2 - 16, top: 196 }} width="32" height="46" viewBox="0 0 32 46" aria-hidden="true">
            <rect x="0" y="0" width="32" height="5" rx="2.5" className={styles.tableTop} />
            <rect x="14" y="5" width="4" height="38" className={styles.tableLeg} />
            <rect x="7" y="42" width="18" height="4" rx="2" className={styles.tableLeg} />
            {cls.lamps && <circle cx="24" cy="-2" r="0" />}
          </svg>
        ))}
        {cls.lamps && seats.map((i) => (
          <span key={`l${i}`} className={styles.lamp} style={{ left: seatX(cls, i) + cls.spacing / 2 - 5, top: 58 }} aria-hidden="true" />
        ))}

        {hotspots.map((h) => (
          <button
            key={h.key}
            type="button"
            className={styles.hotspot}
            data-state={h.state}
            style={{ left: h.x - 20, top: 88 }}
            onPointerDown={(e) => e.stopPropagation()}
            onClick={() => !disabled && act({ type: "hotspot", key: h.key, x: h.x })}
            aria-label={h.title}
          >
            <Icon name={h.state === "ok" ? "check" : h.state === "fault" ? "alert" : h.icon} size={18} />
          </button>
        ))}

        {signals.map((s) => {
          const x = sigX(s);
          const p = Math.max(0, Math.min(1, s.remaining / s.total));
          return (
            <button
              key={s.key}
              type="button"
              className={styles.signal}
              data-urgent={s.urgent || undefined}
              style={{ left: x - 22, top: 86 }}
              onPointerDown={(e) => e.stopPropagation()}
              onClick={() => !disabled && act({ type: "signal", key: s.key, x })}
              aria-label={s.urgent ? "Срочный вызов пассажира" : "Пассажир зовёт проводника"}
            >
              <svg viewBox="0 0 44 44" width="44" height="44" aria-hidden="true">
                <circle cx="22" cy="22" r="19" className={styles.ringTrack} />
                <circle cx="22" cy="22" r="19" className={styles.ring} style={{ strokeDashoffset: 119.4 * (1 - p) }} />
              </svg>
              <span className={styles.signalIcon}>{s.urgent ? "!" : <Icon name="hand" size={18} />}</span>
            </button>
          );
        })}

        {visitors.map((v) => (
          <Visitor key={v.key} role={v.role} from={seatX(cls, v.seat) > W / 2 ? W - 40 : 40} to={seatX(cls, v.seat) + v.offset} faceRight={v.offset < 0} />
        ))}
        {walkers.map((w) => (
          <Visitor key={w.key} role={w.role} variant={w.variant} mood={w.mood} from={w.from} to={w.to} delay={w.delay} vanish={w.vanish} faceRight={w.faceRight} onArrive={() => onWalkerArrive && onWalkerArrive(w)} />
        ))}

        <div className={styles.hero} ref={heroRef} style={{ top: 138 }}>
          <Person outfit="conductor" walking={pose.walking} facing={pose.facing} size={150} />
        </div>
      </div>

      {["left", "right"].map((side) => edges[side] && (
        <button
          key={side}
          type="button"
          className={styles.edge}
          data-side={side}
          data-urgent={edges[side].urgent || undefined}
          onClick={() => !disabled && act({ type: "signal", key: edges[side].key, x: edges[side].x })}
          aria-label={edges[side].urgent ? "Срочный вызов за пределами экрана" : "Вызов пассажира за пределами экрана"}
        >
          <Icon name={side === "left" ? "chevronLeft" : "chevronRight"} size={16} />
          {edges[side].urgent ? <b>!</b> : <Icon name="hand" size={16} />}
        </button>
      ))}

      <div className={styles.controls}>
        <button type="button" className={styles.pad} {...hold(-1)} aria-label="Идти влево"><Icon name="chevronLeft" size={22} /></button>
        {near && !disabled ? (
          <button type="button" className={styles.action} onClick={() => act(near)}>{near.label}</button>
        ) : <span className={styles.hint}>{disabled ? "" : "Тапните, куда идти"}</span>}
        <button type="button" className={styles.pad} {...hold(1)} aria-label="Идти вправо"><Icon name="chevronRight" size={22} /></button>
      </div>
    </div>
  );
}

/** Сотрудник, который входит из тамбура и подходит к месту (наряд полиции, охрана). */
function Visitor({ role, variant = 0, mood, from, to, faceRight, delay = 0, vanish = false, onArrive }) {
  const [x, setX] = useState(from);
  const [walking, setWalking] = useState(false);
  const [gone, setGone] = useState(false);
  const arriveRef = useRef(onArrive);
  arriveRef.current = onArrive;
  const pace = mood === "drunk" ? 70 : 110;
  useEffect(() => {
    const dur = (Math.abs(to - from) / pace) * 1000;
    const t = window.setTimeout(() => { setWalking(true); setX(to); }, 60 + delay);
    const t2 = window.setTimeout(() => {
      setWalking(false);
      if (vanish) setGone(true);
      window.setTimeout(() => arriveRef.current && arriveRef.current(), vanish ? 400 : 0);
    }, dur + 80 + delay);
    return () => { window.clearTimeout(t); window.clearTimeout(t2); };
  }, [from, to, delay, vanish, pace]);
  const dur = Math.abs(to - from) / pace;
  return (
    <div className={styles.visitor} data-gone={gone || undefined} style={{ transform: `translate3d(${x - 33}px,0,0)`, transitionDuration: gone ? "400ms" : `${dur}s`, top: 138, opacity: delay && !walking && x === from ? 0 : undefined }}>
      <Person outfit={role} variant={variant} mood={mood} walking={walking} facing={walking ? (to > from ? "right" : "left") : (faceRight ? "right" : "left")} size={150} hair={role === "passenger" ? variant % 4 : 2} />
    </div>
  );
}

function Seat({ cls, x }) {
  const w = cls.key === "FIRST" ? 66 : cls.key === "BUSINESS" ? 60 : 54;
  return (
    <g>
      <rect x={x - w / 2} y="118" width={w} height="104" rx="14" style={{ fill: cls.seat }} />
      <rect x={x - w / 2 + 6} y="122" width={w - 12} height="18" rx="8" style={{ fill: cls.headrest }} />
      <rect x={x - w / 2 - 4} y="190" width="10" height="36" rx="5" style={{ fill: cls.seatDark }} />
      <rect x={x + w / 2 - 6} y="190" width="10" height="36" rx="5" style={{ fill: cls.seatDark }} />
      <rect x={x - w / 2 + 2} y="206" width={w - 4} height="20" rx="8" style={{ fill: cls.seatDark }} />
      <rect x={x - 3} y="226" width="6" height="14" style={{ fill: "#8791a3" }} />
    </g>
  );
}

function roundRect(x, y, w, h, r) {
  return `M${x + r} ${y}H${x + w - r}Q${x + w} ${y} ${x + w} ${y + r}V${y + h - r}Q${x + w} ${y + h} ${x + w - r} ${y + h}H${x + r}Q${x} ${y + h} ${x} ${y + h - r}V${y + r}Q${x} ${y} ${x + r} ${y}Z`;
}

/** Пейзаж за окнами: небо, дальние холмы, лес и опоры контактной сети — три слоя параллакса. */
function Landscape({ moving, stationName, speed = 1 }) {
  return (
    <div className={styles.land} data-moving={moving || undefined} aria-hidden="true" style={{ "--speed": speed }}>
      <div className={styles.sky} />
      <div className={`${styles.layer} ${styles.hills}`} />
      <div className={`${styles.layer} ${styles.trees}`} />
      <div className={`${styles.layer} ${styles.poles}`} />
      {!moving && stationName && (
        <div className={styles.platform}>
          <span className={styles.stationSign}>{stationName}</span>
        </div>
      )}
    </div>
  );
}
