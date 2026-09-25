import { useEffect, useRef, useState } from "react";

export function prefersReducedMotion() {
  try {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  } catch (e) {
    return false;
  }
}

/**
 * Заголовок, проявляющийся по словам из размытия. Для скринридера — цельная строка
 * (aria-label), визуальные слова скрыты от дерева доступности.
 * props: as (тег), text, className, delay (мс до первого слова), maxDuration (мс — длинный
 * текст укладывается в это время, шаг между словами уменьшается).
 */
export function SplitText({ as: As = "h1", text, className, delay = 0, maxDuration = 1200, ...rest }) {
  const words = String(text || "").split(/(\s+)/).filter((w) => w.length > 0);
  const count = words.filter((w) => !/^\s+$/.test(w)).length || 1;
  const step = Math.max(12, Math.min(55, Math.round(maxDuration / count)));
  let index = 0;
  return (
    <As className={className} aria-label={text} style={{ "--word-base": `${delay}ms`, "--word-step": `${step}ms` }} {...rest}>
      {words.map((w, i) => {
        if (/^\s+$/.test(w)) return " ";
        const node = (
          <span key={i} className="word" aria-hidden="true" style={{ "--i": index }}>{w}</span>
        );
        index += 1;
        return node;
      })}
    </As>
  );
}

function easeOutExpo(t) {
  return t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);
}

/**
 * Число, досчитывающееся от 0 до value при появлении (и от старого к новому при смене).
 * props: value, duration (мс), delay (мс), format (fn числа → строка).
 */
export function CountUp({ value, duration = 1100, delay = 0, format, className }) {
  const target = typeof value === "number" && isFinite(value) ? value : 0;
  const [shown, setShown] = useState(prefersReducedMotion() ? target : 0);
  const fromRef = useRef(prefersReducedMotion() ? target : 0);

  useEffect(() => {
    if (prefersReducedMotion()) {
      setShown(target);
      return undefined;
    }
    const from = fromRef.current;
    let raf = 0;
    let start = 0;
    const timer = window.setTimeout(() => {
      function tick(now) {
        if (!start) start = now;
        const p = Math.min(1, (now - start) / duration);
        const v = from + (target - from) * easeOutExpo(p);
        setShown(v);
        if (p < 1) raf = requestAnimationFrame(tick);
        else fromRef.current = target;
      }
      raf = requestAnimationFrame(tick);
    }, delay);
    return () => {
      window.clearTimeout(timer);
      cancelAnimationFrame(raf);
      fromRef.current = target;
    };
  }, [target, duration, delay]);

  const rounded = Math.round(shown);
  const text = format ? format(rounded) : String(rounded);
  return <span className={className} style={{ fontVariantNumeric: "tabular-nums" }}>{text}</span>;
}

/** Обёртка для одного блока каскада: <Reveal i={2}>…</Reveal>. */
export function Reveal({ as: As = "div", i = 0, className, style, children, ...rest }) {
  const cls = ["rv", className].filter(Boolean).join(" ");
  return (
    <As className={cls} style={{ ...style, "--i": i }} {...rest}>
      {children}
    </As>
  );
}
