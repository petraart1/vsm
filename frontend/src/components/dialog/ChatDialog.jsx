import { useEffect, useRef, useState } from "react";
import Icon from "../ui/Icon.jsx";
import Timer from "../ui/Timer.jsx";
import DeltaBadges from "../ui/DeltaBadges.jsx";
import { PassengerBust, PersonBust } from "../characters/People.jsx";
import { getSettings } from "../../settings.js";
import styles from "./ChatDialog.module.css";

/**
 * Диалог как переписка в iMessage: реплики собеседника слева (с портретом), ответы проводника
 * справа синими пузырями, реакции и изменения шкал — служебной строкой по центру.
 * Компонент только рисует; логику ведёт useScenarioDialog (backend) или локальный скрипт (заступ).
 *
 * props:
 *  - speaker: { kind: 'passenger'|'person', variant, outfit, name, role }
 *  - messages: [{ id, from: 'npc'|'me'|'note', text, deltas, tone }]
 *  - choices: [{ id, text }] | null — показываются, когда ждём ответа
 *  - timer: { key, seconds, deadlineAt, onExpire } | null
 *  - busy: собеседник «печатает»
 *  - footer: узел под перепиской (например, кнопка «Продолжить»)
 *  - scales: { safety, loyalty } | null — мини-шкалы в шапке
 *  - sheet: true — нижняя шторка поверх сцены; false — полноэкранный режим
 *  - onClose: крестик в шапке
 */
export default function ChatDialog({ speaker, messages, choices, onChoose, timer, busy, footer, scales, sheet = true, onClose, title, className }) {
  const scrollRef = useRef(null);
  const [voice, setVoice] = useState(() => readVoicePref());
  const [listening, setListening] = useState(false);
  const spokenRef = useRef(new Set());

  // Автопрокрутка к последнему сообщению — плавно, как в Сообщениях.
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
  }, [messages.length, busy, choices]);

  // Озвучка реплик собеседника (Web Speech API, голос ru-RU) — опционально, по кнопке.
  useEffect(() => {
    if (!voice || !("speechSynthesis" in window)) return;
    messages.forEach((m) => {
      if (m.from !== "npc" || spokenRef.current.has(m.id)) return;
      spokenRef.current.add(m.id);
      const u = new SpeechSynthesisUtterance(m.text);
      u.lang = "ru-RU";
      u.rate = 1.05;
      window.speechSynthesis.speak(u);
    });
  }, [messages, voice]);

  useEffect(() => () => { try { window.speechSynthesis && window.speechSynthesis.cancel(); } catch (e) { /* нет API */ } }, []);

  function toggleVoice() {
    const next = !voice;
    setVoice(next);
    try { localStorage.setItem("reactlab.voice", next ? "1" : "0"); } catch (e) { /* приватный режим */ }
    if (!next) try { window.speechSynthesis.cancel(); } catch (e) { /* нет API */ }
    else messages.forEach((m) => spokenRef.current.add(m.id)); // не зачитывать историю
  }

  // Ответ голосом: распознаём фразу и выбираем самый близкий по словам вариант.
  const Recognition = typeof window !== "undefined" ? (window.SpeechRecognition || window.webkitSpeechRecognition) : null;
  function listen() {
    if (!Recognition || !choices || listening) return;
    const rec = new Recognition();
    rec.lang = "ru-RU";
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    setListening(true);
    rec.onresult = (ev) => {
      const said = ev.results[0][0].transcript;
      const best = closestChoice(said, choices);
      if (best) onChoose(best.id);
    };
    rec.onend = () => setListening(false);
    rec.onerror = () => setListening(false);
    rec.start();
  }

  const lastNpc = [...messages].reverse().find((m) => m.from === "npc");
  const talking = busy || (lastNpc && messages[messages.length - 1] === lastNpc);

  return (
    <div className={[sheet ? styles.sheet : styles.full, className].filter(Boolean).join(" ")} role="dialog" aria-label={title || speaker.name}>
      {sheet && <span className={styles.grabber} aria-hidden="true" />}
      <header className={styles.head}>
        <Portrait speaker={speaker} talking={talking} size={44} />
        <div className={styles.who}>
          <p className={styles.name}>{speaker.name}</p>
          <p className={styles.role}>{speaker.role}</p>
        </div>
        {scales && (
          <div className={styles.meters} aria-label="Шкалы">
            <Meter value={scales.safety} tone="safety" label="Безопасность" />
            <Meter value={scales.loyalty} tone="loyalty" label="Лояльность" />
          </div>
        )}
        {"speechSynthesis" in (typeof window !== "undefined" ? window : {}) && (
          <button type="button" className={styles.round} data-on={voice || undefined} onClick={toggleVoice} aria-label={voice ? "Выключить озвучку" : "Включить озвучку"}>
            <Icon name="speaker" size={16} />
          </button>
        )}
        {onClose && (
          <button type="button" className={styles.round} onClick={onClose} aria-label="Закрыть">
            <Icon name="x" size={16} />
          </button>
        )}
      </header>

      <div className={styles.scroll} ref={scrollRef}>
        {messages.map((m) => <Bubble key={m.id} m={m} speaker={speaker} />)}
        {busy && (
          <div className={`${styles.row} ${styles.npcRow}`}>
            <span className={styles.typing} aria-label="Собеседник отвечает"><i /><i /><i /></span>
          </div>
        )}
      </div>

      {(choices || footer) && (
        <div className={styles.dock}>
          {timer && (
            <div className={styles.timerRow}>
              <Timer key={timer.key} timerSeconds={timer.seconds} deadlineAt={timer.deadlineAt} onExpire={timer.onExpire} remainingOverride={timer.remaining} />
            </div>
          )}
          {choices && (
            <ol className={styles.choices} aria-label="Варианты ответа">
              {choices.map((c, i) => (
                <li key={c.id} style={{ "--i": i }}>
                  <button type="button" className={styles.choice} onClick={() => onChoose(c.id)}>
                    <span>{c.text}</span>
                    <Icon name="chevronRight" size={16} />
                  </button>
                </li>
              ))}
            </ol>
          )}
          {choices && Recognition && getSettings().voiceReplies && (
            <button type="button" className={styles.mic} data-on={listening || undefined} onClick={listen}>
              <Icon name="mic" size={16} />
              {listening ? "Слушаю…" : "Ответить голосом"}
            </button>
          )}
          {footer}
        </div>
      )}
    </div>
  );
}

function Bubble({ m, speaker }) {
  if (m.from === "note") {
    return (
      <div className={styles.note} data-tone={m.tone}>
        {m.text && <p>{m.text}</p>}
        {m.deltas && <DeltaBadges deltas={m.deltas} className={styles.deltas} />}
      </div>
    );
  }
  if (m.from === "me") {
    return (
      <div className={`${styles.row} ${styles.meRow}`}>
        <p className={`${styles.bubble} ${styles.me}`}>{m.text}</p>
      </div>
    );
  }
  return (
    <div className={`${styles.row} ${styles.npcRow}`}>
      {m.speaker ? <Portrait speaker={m.speaker} size={28} /> : null}
      <div>
        {m.speaker && <p className={styles.bubbleName}>{m.speaker.name}</p>}
        {m.context && <p className={styles.context}>{m.context}</p>}
        <p className={`${styles.bubble} ${styles.npc}`}>{m.text}</p>
      </div>
    </div>
  );
}

function Portrait({ speaker, talking, size }) {
  if (speaker.kind === "passenger") return <PassengerBust variant={speaker.variant} size={size} talking={talking} mood={speaker.mood} />;
  return <PersonBust outfit={speaker.outfit} size={size} />;
}

function Meter({ value, tone, label }) {
  const v = Math.max(0, Math.min(100, value ?? 0));
  return (
    <span className={styles.meter} data-tone={tone} title={`${label}: ${v}`}>
      <svg viewBox="0 0 36 36" width="30" height="30" aria-hidden="true">
        <circle cx="18" cy="18" r="15" className={styles.meterTrack} />
        <circle cx="18" cy="18" r="15" className={styles.meterFill} style={{ strokeDashoffset: 94.25 * (1 - v / 100) }} />
      </svg>
      <span className={styles.meterNum}>{v}</span>
    </span>
  );
}

function readVoicePref() {
  try { return localStorage.getItem("reactlab.voice") === "1"; } catch (e) { return false; }
}

function words(s) {
  return String(s).toLowerCase().replace(/[^a-zа-яё0-9\s]/gi, " ").split(/\s+/).filter((w) => w.length > 2).map((w) => w.slice(0, 5));
}

/** Выбор варианта по голосу: максимальное пересечение основ слов (первые 5 букв). */
export function closestChoice(said, choices) {
  const heard = new Set(words(said));
  let best = null;
  let score = 0;
  choices.forEach((c) => {
    const w = words(c.text);
    const hit = w.filter((x) => heard.has(x)).length / Math.max(1, w.length);
    if (hit > score) { score = hit; best = c; }
  });
  return score >= 0.2 ? best : null;
}
