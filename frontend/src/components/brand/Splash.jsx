import { useEffect, useState } from "react";
import { LogoMark } from "./Logo.jsx";
import { prefersReducedMotion } from "../motion/Motion.jsx";
import styles from "./Splash.module.css";

const SEEN_KEY = "reactlab.splashSeen.v1";
const TOTAL_MS = 2600;
const EXIT_MS = 700;

function alreadySeen() {
  try { return sessionStorage.getItem(SEEN_KEY) === "1"; } catch (e) { return false; }
}

function markSeen() {
  try { sessionStorage.setItem(SEEN_KEY, "1"); } catch (e) { /* приватный режим */ }
}

/**
 * Заставка при первом открытии за сессию: знак прорисовывается, вордмарк выезжает из-за знака,
 * затем появляется слоган и заставка уходит вверх. Пропускается кликом, клавишей и при
 * prefers-reduced-motion.
 */
export default function Splash() {
  const [phase, setPhase] = useState(() => (alreadySeen() || prefersReducedMotion() ? "done" : "show"));

  useEffect(() => {
    if (phase !== "show") return undefined;
    markSeen();
    const toExit = window.setTimeout(() => setPhase("exit"), TOTAL_MS);
    function skip() { setPhase("exit"); }
    window.addEventListener("keydown", skip, { once: true });
    return () => {
      window.clearTimeout(toExit);
      window.removeEventListener("keydown", skip);
    };
  }, [phase]);

  useEffect(() => {
    if (phase !== "exit") return undefined;
    const t = window.setTimeout(() => setPhase("done"), EXIT_MS);
    return () => window.clearTimeout(t);
  }, [phase]);

  if (phase === "done") return null;

  return (
    <div className={styles.splash} data-phase={phase} onClick={() => setPhase("exit")} aria-hidden="true">
      <div className={styles.lockup}>
        <LogoMark size={64} draw className={styles.mark} />
        <span className={styles.wordClip}>
          <span className={styles.word}>
            <span className={styles.react}>React</span><span className={styles.lab}>Lab</span>
          </span>
        </span>
      </div>
      <p className={styles.tagline}>Тренируй решения. Развивайся.</p>
    </div>
  );
}
