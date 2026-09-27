import { useEffect, useRef, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import VerifiedBadge from "../components/ui/VerifiedBadge.jsx";
import GosuslugiMark from "../components/ui/GosuslugiMark.jsx";
import { LogoMark } from "../components/brand/Logo.jsx";
import { Person } from "../components/characters/People.jsx";
import styles from "./Auth.module.css";

/**
 * Вход, регистрация и вход через Госуслуги (демо-ЕСИА на backend).
 * Маршруты: #/login, #/register, #/auth/esia?code=… (возврат с Госуслуг), #/auth/esia-demo (без backend).
 * Анонимная игра остаётся доступной: «Продолжить без входа».
 */
export default function Auth({ route }) {
  const sub = route.screen === "auth" ? route.segments[1] : route.screen;
  let body;
  if (sub === "register") body = <RegisterForm next={route.query.next} />;
  else if (sub === "esia") body = <EsiaReturn code={route.query.code} />;
  else if (sub === "esia-demo") body = <EsiaDemo />;
  else body = <LoginForm next={route.query.next} />;
  return (
    <div className={styles.wrap}>
      <aside className={styles.art} aria-hidden="true">
        <img src={`${import.meta.env.BASE_URL}backgrounds/express.jpg`} alt="" />
        <div className={styles.shade} />
        <div className={styles.artText}>
          <p className={styles.kicker}>ReactLab · Тренажёр проводника ВСМ</p>
          <p className={styles.artTitle}>Подтверждённый аккаунт — полноценный участник программы</p>
          <ul className={styles.perks}>
            <li><Icon name="check" size={16} />Очки начисляются полностью, без понижающего коэффициента</li>
            <li><Icon name="check" size={16} />Официальные награды: допуск, повышение класса, свидетельства</li>
            <li><Icon name="check" size={16} />Результаты смен учитываются в решении для HR</li>
          </ul>
        </div>
        <div className={styles.crew}>
          <Person outfit="chief" hair={2} size={200} />
          <Person outfit="conductor" size={220} />
        </div>
      </aside>
      <main className={styles.panel}>
        <a href="#/today" className={styles.brand}><LogoMark size={30} /><span><b>React</b>Lab</span></a>
        {body}
      </main>
    </div>
  );
}

function goNext(next) {
  navigate(next && next.startsWith("/") ? next : "/today");
}

function Field({ label, type = "text", value, onChange, autoComplete, hint, error, minLength, required = true, autoFocus }) {
  const [shown, setShown] = useState(false);
  const isPassword = type === "password";
  return (
    <label className={styles.field} data-error={error || undefined}>
      <span className={styles.label}>{label}</span>
      <span className={styles.inputWrap}>
        <input
          className={styles.input}
          type={isPassword && shown ? "text" : type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          autoComplete={autoComplete}
          required={required}
          minLength={minLength}
          autoFocus={autoFocus}
        />
        {isPassword && (
          <button type="button" className={styles.eye} onClick={() => setShown((v) => !v)} aria-label={shown ? "Скрыть пароль" : "Показать пароль"}>
            <Icon name="eye" size={16} />
          </button>
        )}
      </span>
      {(error || hint) && <span className={styles.hint}>{error || hint}</span>}
    </label>
  );
}

function EsiaButton() {
  function go() {
    if (api.USE_MOCKS) navigate("/auth/esia-demo");
    else window.location.href = api.esiaAuthorizeUrl();
  }
  return (
    <button type="button" className={styles.esia} onClick={go}>
      <GosuslugiMark size={38} />
      <span>
        <b>Войти через Госуслуги</b>
        <small>Подтверждает личность · демо-стенд</small>
      </span>
      <Icon name="chevronRight" size={16} />
    </button>
  );
}

function LoginForm({ next }) {
  const [loginName, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    api.login(loginName.trim(), password).then(() => goNext(next), (err) => { setError(err.message); setBusy(false); });
  }
  return (
    <div className={styles.card}>
      <h1 className={styles.title}>Вход</h1>
      <p className={styles.lead}>Войдите, чтобы прогресс, награды и допуск сохранялись в учётной записи.</p>
      <EsiaButton />
      <div className={styles.or}><span>или по логину</span></div>
      <form className={styles.form} onSubmit={submit}>
        <Field label="Логин" value={loginName} onChange={setLogin} autoComplete="username" autoFocus />
        <Field label="Пароль" type="password" value={password} onChange={setPassword} autoComplete="current-password" />
        {error && <p className={styles.error} role="alert">{error}</p>}
        <Button size="lg" type="submit" className={styles.submit} disabled={busy}>{busy ? "Входим…" : "Войти"}</Button>
      </form>
      <p className={styles.switch}>Нет учётной записи? <a href={`#/register${next ? `?next=${encodeURIComponent(next)}` : ""}`}>Зарегистрироваться</a></p>
      <a className={styles.skip} href="#/today">Продолжить без входа</a>
      <p className={styles.fine}>Без входа прогресс хранится только на этом устройстве, а очки начисляются с понижающим коэффициентом.</p>
    </div>
  );
}

function RegisterForm({ next }) {
  const [form, setForm] = useState({ displayName: "", login: "", email: "", password: "" });
  const [agree, setAgree] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (k) => (v) => setForm((f) => ({ ...f, [k]: v }));
  const loginErr = form.login && form.login.trim().length < 3 ? "Не короче 3 символов" : null;
  const passErr = form.password && form.password.length < 8 ? "Не короче 8 символов" : null;
  const emailErr = form.email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email.trim()) ? "Проверьте адрес почты" : null;
  const invalid = !form.login || !form.email || !form.password || loginErr || passErr || emailErr || !agree;
  function submit(e) {
    e.preventDefault();
    if (invalid) return;
    setBusy(true);
    setError(null);
    api.register({ ...form, login: form.login.trim(), email: form.email.trim() }).then(() => goNext(next), (err) => { setError(err.message); setBusy(false); });
  }
  return (
    <div className={styles.card}>
      <h1 className={styles.title}>Регистрация</h1>
      <p className={styles.lead}>Уже пройденные смены и тренировки с этого устройства перейдут в новую учётную запись.</p>
      <form className={styles.form} onSubmit={submit}>
        <Field label="Имя и фамилия" value={form.displayName} onChange={set("displayName")} autoComplete="name" required={false} hint="Так вас увидят коллеги в рейтинге" />
        <Field label="Логин" value={form.login} onChange={set("login")} autoComplete="username" error={loginErr} />
        <Field label="Почта" type="email" value={form.email} onChange={set("email")} autoComplete="email" error={emailErr} />
        <Field label="Пароль" type="password" value={form.password} onChange={set("password")} autoComplete="new-password" error={passErr} hint="Минимум 8 символов" />
        <label className={styles.check}>
          <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
          <span>Согласен на обработку персональных данных для целей обучения (152-ФЗ)</span>
        </label>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <Button size="lg" type="submit" className={styles.submit} disabled={busy || invalid}>{busy ? "Создаём…" : "Создать учётную запись"}</Button>
      </form>
      <div className={styles.or}><span>или сразу с подтверждением</span></div>
      <EsiaButton />
      <p className={styles.switch}>Уже есть учётная запись? <a href="#/login">Войти</a></p>
    </div>
  );
}

/** Возврат с Госуслуг: обмен одноразового кода на токен. */
function EsiaReturn({ code }) {
  const [state, setState] = useState(code ? "loading" : "error");
  const [error, setError] = useState(code ? null : "Госуслуги не вернули код входа.");
  const [account, setAccount] = useState(null);
  const once = useRef(false);
  useEffect(() => {
    if (!code || once.current) return;
    once.current = true;
    api.esiaCallback(code).then((acc) => { setAccount(acc); setState("done"); }, (err) => { setError(err.message); setState("error"); });
  }, [code]);
  if (state === "loading") return <div className={styles.card}><p className={styles.lead}>Проверяем вход через Госуслуги…</p></div>;
  if (state === "error") {
    return (
      <div className={styles.card}>
        <h1 className={styles.title}>Не получилось</h1>
        <p className={styles.lead}>{error}</p>
        <EsiaButton />
        <a className={styles.skip} href="#/login">Вернуться ко входу</a>
      </div>
    );
  }
  return <Verified account={account} />;
}

function Verified({ account }) {
  return (
    <div className={`${styles.card} ${styles.success}`}>
      <span className={styles.bigBadge}><VerifiedBadge size={72} /></span>
      <h1 className={styles.title}>Личность подтверждена</h1>
      <p className={styles.via}><GosuslugiMark size={20} />Подтверждено через Госуслуги</p>
      <p className={styles.lead}>{account ? account.displayName : "Учётная запись"} — теперь полноценный участник: очки без коэффициента и официальные награды.</p>
      <Button size="lg" className={styles.submit} onClick={() => navigate("/today")}>Продолжить</Button>
      <a className={styles.skip} href="#/achievements">Посмотреть награды</a>
    </div>
  );
}

/** Демо-выбор тестового гражданина, когда фронт работает без backend (моки). */
function EsiaDemo() {
  const [done, setDone] = useState(null);
  if (done) return <Verified account={done} />;
  return (
    <div className={styles.card}>
      <span className={styles.esiaHead}><GosuslugiMark size={48} /></span>
      <h1 className={styles.title}>Госуслуги · демо</h1>
      <p className={styles.lead}>Демонстрационный вход: выберите тестового гражданина. Это не настоящий портал и не проверка реальных учётных записей.</p>
      <ul className={styles.citizens}>
        {api.ESIA_DEMO_CITIZENS.map((c) => (
          <li key={c.code}>
            <button type="button" className={styles.citizen} onClick={() => api.esiaCallback(c.code).then(setDone)}>
              <b>{c.fullName}</b>
              <small>СНИЛС {c.snils}</small>
              <Icon name="chevronRight" size={16} />
            </button>
          </li>
        ))}
      </ul>
      <a className={styles.skip} href="#/login">Отмена</a>
    </div>
  );
}
