import { useEffect, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import PageHeader from "../components/ui/PageHeader.jsx";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import Avatar from "../components/ui/Avatar.jsx";
import VerifiedBadge from "../components/ui/VerifiedBadge.jsx";
import { useAccount, initialsOf } from "../account.js";
import { getSettings, setSetting } from "../settings.js";
import { getThemeChoice, setTheme, setSystemTheme } from "../appearance.js";
import { FINISH_OPTIONS, readFinish, writeFinish } from "../components/awards/awards.js";
import styles from "./Settings.module.css";

/** Настройки пользователя: учётная запись и подтверждение, оформление, тренажёр, данные. */
export default function Settings() {
  const account = useAccount();
  const [s, setS] = useState(getSettings);
  const [theme, setThemeChoice] = useState(getThemeChoice);
  const [finish, setFinish] = useState(readFinish);
  const [confirmReset, setConfirmReset] = useState(false);
  useEffect(() => { api.refreshAccount(); }, []);

  const set = (k) => (v) => setS(setSetting(k, v));
  function pickTheme(v) {
    setThemeChoice(v);
    if (v === "system") setSystemTheme();
    else setTheme(v);
  }
  function resetLocal() {
    try {
      Object.keys(localStorage).filter((k) => k.startsWith("reactlab.") || k.startsWith("vsm.completed")).forEach((k) => localStorage.removeItem(k));
    } catch (e) { /* приватный режим */ }
    setConfirmReset(false);
    navigate("/today");
    window.location.reload();
  }

  return (
    <div className={styles.page}>
      <PageHeader title="Настройки" />
      <div className={styles.columns}>
        <div className={styles.col}>
          <h2 className={styles.label}>Учётная запись</h2>
          <section className={styles.group}>
            {account ? (
              <>
                <div className={styles.account}>
                  <Avatar initials={initialsOf(account.displayName || account.login)} size={56} tone="solid" />
                  <div className={styles.accountText}>
                    <p className={styles.name}>{account.displayName || account.login} {account.verified && <VerifiedBadge size={18} />}</p>
                    <p className={styles.meta}>{account.login} · {account.email}{account.role === "ADMIN" ? " · администратор" : ""}</p>
                  </div>
                </div>
                {account.verified ? (
                  <div className={styles.row}><span className={styles.rowIcon} data-tone="blue"><Icon name="shield" size={16} /></span><span className={styles.rowText}><b>Личность подтверждена</b><span>Полные очки, официальные награды и учёт в решении для HR</span></span></div>
                ) : (
                  <a className={styles.row} href={api.USE_MOCKS ? "#/auth/esia-demo" : api.esiaAuthorizeUrl()}>
                    <span className={styles.rowIcon} data-tone="blue"><Icon name="shield" size={16} /></span>
                    <span className={styles.rowText}><b>Подтвердить через Госуслуги</b><span>Сейчас очки начисляются с коэффициентом 0,5, часть наград недоступна</span></span>
                    <Icon name="chevronRight" size={16} className={styles.chev} />
                  </a>
                )}
                {account.role === "ADMIN" && (
                  <a className={styles.row} href="#/admin"><span className={styles.rowIcon}><Icon name="clipboard" size={16} /></span><span className={styles.rowText}><b>Администрирование</b><span>Статистика, сценарии, события и награды</span></span><Icon name="chevronRight" size={16} className={styles.chev} /></a>
                )}
                <button type="button" className={`${styles.row} ${styles.danger}`} onClick={() => { api.logout(); navigate("/today"); }}>Выйти</button>
              </>
            ) : (
              <>
                <div className={styles.row}><span className={styles.rowIcon}><Icon name="user" size={16} /></span><span className={styles.rowText}><b>Анонимный режим</b><span>Прогресс хранится только на этом устройстве</span></span></div>
                <div className={styles.actions}>
                  <Button onClick={() => navigate("/login?next=/settings")}>Войти</Button>
                  <Button variant="secondary" onClick={() => navigate("/register?next=/settings")}>Регистрация</Button>
                </div>
              </>
            )}
          </section>

          <h2 className={styles.label}>Оформление</h2>
          <section className={styles.group}>
            <Segment label="Тема" value={theme} onChange={pickTheme} options={[["light", "Светлая"], ["dark", "Тёмная"], ["system", "Как в системе"]]} />
            <Segment label="Отделка медалей" value={finish} onChange={(v) => { setFinish(v); writeFinish(v); }} options={FINISH_OPTIONS.map((o) => [o.key, o.label])} />
            <Toggle label="Меньше анимации" note="Отключает движение персонажей и переходы" value={s.reduceMotion} onChange={set("reduceMotion")} />
          </section>
        </div>

        <div className={styles.col}>
          <h2 className={styles.label}>Тренажёр</h2>
          <section className={styles.group}>
            <Segment label="Скорость рейса по умолчанию" value={String(s.tripSpeed)} onChange={(v) => set("tripSpeed")(Number(v))} options={[["1", "1×"], ["2", "2×"], ["4", "4×"]]} />
            <Toggle label="Озвучивать реплики" note="Голос для реплик пассажиров в диалогах" value={readVoice()} onChange={(v) => { try { localStorage.setItem("reactlab.voice", v ? "1" : "0"); } catch (e) { /* ignore */ } setS({ ...s }); }} />
            <Toggle label="Ответ голосом" note="Кнопка микрофона под вариантами ответа" value={s.voiceReplies} onChange={set("voiceReplies")} />
            <Toggle label="Вибрация при вызове" note="На телефонах с поддержкой вибрации" value={s.haptics} onChange={set("haptics")} />
            <Toggle label="Окно награды за вход" note="Показывать серию входов раз в сутки" value={s.dailyReward} onChange={set("dailyReward")} />
          </section>

          <h2 className={styles.label}>Данные</h2>
          <section className={styles.group}>
            <div className={styles.row}><span className={styles.rowIcon}><Icon name="clipboard" size={16} /></span><span className={styles.rowText}><b>Хранение</b><span>История смен, серия входов и витрина — на этом устройстве; прохождения и очки — на сервере</span></span></div>
            {confirmReset ? (
              <div className={styles.confirm}>
                <p>Удалить локальные данные: историю смен, серию входов и витрину? Прогресс на сервере останется.</p>
                <div className={styles.actions}>
                  <Button variant="secondary" onClick={() => setConfirmReset(false)}>Отмена</Button>
                  <Button onClick={resetLocal}>Удалить</Button>
                </div>
              </div>
            ) : (
              <button type="button" className={`${styles.row} ${styles.danger}`} onClick={() => setConfirmReset(true)}>Очистить данные на устройстве</button>
            )}
          </section>
        </div>
      </div>
      <p className={styles.version}>ReactLab Тренажёр проводника ВСМ</p>
    </div>
  );
}

function readVoice() {
  try { return localStorage.getItem("reactlab.voice") === "1"; } catch (e) { return false; }
}

function Segment({ label, value, onChange, options }) {
  return (
    <div className={styles.segRow}>
      <span className={styles.segLabel}>{label}</span>
      <div className={styles.segmented} role="radiogroup" aria-label={label}>
        {options.map(([k, text]) => (
          <button key={k} type="button" role="radio" aria-checked={value === k} data-on={value === k || undefined} onClick={() => onChange(k)}>{text}</button>
        ))}
      </div>
    </div>
  );
}

function Toggle({ label, note, value, onChange }) {
  return (
    <label className={styles.toggleRow}>
      <span className={styles.rowText}><b>{label}</b>{note && <span>{note}</span>}</span>
      <input type="checkbox" role="switch" className={styles.switch} checked={!!value} onChange={(e) => onChange(e.target.checked)} />
    </label>
  );
}
