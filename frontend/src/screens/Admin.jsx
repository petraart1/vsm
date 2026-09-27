import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import * as api from "../api.js";
import { navigate } from "../router.js";
import Button from "../components/ui/Button.jsx";
import Icon from "../components/ui/Icon.jsx";
import Skeleton from "../components/ui/Skeleton.jsx";
import EmptyState from "../components/ui/EmptyState.jsx";
import Toast from "../components/ui/Toast.jsx";
import VerifiedBadge from "../components/ui/VerifiedBadge.jsx";
import Medal from "../components/awards/Medal.jsx";
import { LogoMark } from "../components/brand/Logo.jsx";
import { useAccount } from "../account.js";
import { BLOCK_TITLES, blockTitle } from "../progress.js";
import styles from "./Admin.module.css";

/**
 * Консоль администратора (роль ADMIN) — отдельная полноэкранная раскладка без игровой навигации:
 * шапка, собственное левое меню и прокручиваемая рабочая область. Только администрирование —
 * без прохождения сценариев и личного прогресса. Разделы — #/admin/<раздел>.
 */
const SECTIONS = [
  { key: "overview", label: "Обзор", icon: "dashboard", title: "Обзор", description: "Сводные показатели обучения и выгрузки для аналитики." },
  { key: "users", label: "Учётные записи", icon: "user", title: "Учётные записи", description: "Роли и подтверждение личности пользователей стенда." },
  { key: "players", label: "Проводники", icon: "users", title: "Проводники", description: "Результаты обучения по каждому проводнику." },
  { key: "scenarios", label: "Сценарии", icon: "list", title: "Сценарии", description: "Каталог ситуаций: доступность для проводников, статистика, импорт и экспорт." },
  { key: "events", label: "События", icon: "flag", title: "События", description: "Временные цели для проводников с наградой за выполнение." },
  { key: "awards", label: "Награды", icon: "medal", title: "Награды", description: "Награды программы, которые выдаются вручную." }
];

export default function Admin({ route }) {
  const account = useAccount();
  const key = route && route.segments && SECTIONS.some((s) => s.key === route.segments[1]) ? route.segments[1] : "overview";
  const section = SECTIONS.find((s) => s.key === key);

  const [toasts, setToasts] = useState([]);
  const notify = useCallback((title, body) => {
    setToasts((list) => [...list.slice(-2), { id: `${Date.now()}-${Math.random()}`, title, body }]);
  }, []);
  const dismiss = useCallback((id) => setToasts((list) => list.filter((t) => t.id !== id)), []);

  const contentRef = useRef(null);
  useEffect(() => { if (contentRef.current) contentRef.current.scrollTop = 0; }, [key]);

  if (!account || account.role !== "ADMIN") {
    return (
      <div className={styles.denied}>
        <EmptyState
          title="Нужны права администратора"
          message="Этот раздел доступен только учётной записи с ролью администратора. Войдите под такой учётной записью."
          action={<Button as="a" href="#/login?next=/admin">Войти</Button>}
        />
      </div>
    );
  }

  function logout() {
    api.logout();
    navigate("/login");
  }

  return (
    <div className={styles.console}>
      <header className={styles.header}>
        <a className={styles.brand} href="#/admin/overview">
          <LogoMark size={26} />
          <span className={styles.brandText}>Администрирование</span>
        </a>
        <div className={styles.headerRight}>
          <span className={styles.who}>
            <span className={styles.whoName}>{account.displayName || account.login}</span>
            {account.verified && <VerifiedBadge size={14} />}
            <span className={styles.roleTag}>Администратор</span>
          </span>
          <a className={styles.headerLink} href="#/settings"><Icon name="settings" size={16} />Настройки</a>
          <button type="button" className={styles.headerLink} onClick={logout}><Icon name="logout" size={16} />Выйти</button>
        </div>
      </header>

      <nav className={styles.nav} aria-label="Разделы администрирования">
        <ul>
          {SECTIONS.map((s) => (
            <li key={s.key}>
              <a href={`#/admin/${s.key}`} className={styles.navItem} aria-current={s.key === key ? "page" : undefined}>
                <Icon name={s.icon} size={17} />{s.label}
              </a>
            </li>
          ))}
        </ul>
        <p className={styles.navFoot}>ReactLab · тренажёр проводника ВСМ</p>
      </nav>

      <div className={styles.content} ref={contentRef}>
        <div className={styles.inner}>
          <div className={styles.sectionHead}>
            <h1 className={styles.h1}>{section.title}</h1>
            <p className={styles.lead}>{section.description}</p>
          </div>
          {key === "overview" && <Overview notify={notify} />}
          {key === "users" && <Users notify={notify} selfId={account.id} />}
          {key === "players" && <Players />}
          {key === "scenarios" && <Scenarios notify={notify} />}
          {key === "events" && <Events notify={notify} />}
          {key === "awards" && <Awards notify={notify} />}
        </div>
      </div>

      <Toast toasts={toasts} onDismiss={dismiss} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Общие элементы

function pct(v) { return v == null || isNaN(v) ? "—" : `${Math.round(v * 100)}%`; }
function num(v) { return v == null || isNaN(v) ? "—" : Math.round(v).toLocaleString("ru-RU"); }
function fmtDate(v) { return v ? new Date(v).toLocaleDateString("ru-RU", { day: "numeric", month: "short", year: "numeric" }) : "—"; }
function fmtDateTime(v) { return v ? new Date(v).toLocaleString("ru-RU", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"; }
function ago(v) {
  if (!v) return "—";
  const m = Math.round((Date.now() - new Date(v).getTime()) / 60000);
  if (m < 1) return "только что";
  if (m < 60) return `${m} мин назад`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} ч назад`;
  const d = Math.round(h / 24);
  if (d < 8) return `${d} дн назад`;
  return fmtDate(v);
}
function errText(e) { return (e && e.message) || "Неизвестная ошибка"; }

function Panel({ title, note, actions, children, flush }) {
  return (
    <section className={styles.panel}>
      {(title || actions) && (
        <div className={styles.panelHead}>
          <div>
            {title && <h2 className={styles.h2}>{title}</h2>}
            {note && <p className={styles.note}>{note}</p>}
          </div>
          {actions && <div className={styles.panelActions}>{actions}</div>}
        </div>
      )}
      <div className={flush ? styles.panelFlush : styles.panelBody}>{children}</div>
    </section>
  );
}

function SearchBox({ value, onChange, placeholder }) {
  return (
    <label className={styles.searchBox}>
      <Icon name="search" size={15} />
      <input type="search" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} aria-label={placeholder} />
      {value && <button type="button" className={styles.searchClear} onClick={() => onChange("")} aria-label="Очистить"><Icon name="x" size={13} /></button>}
    </label>
  );
}

function Segmented({ value, onChange, options, label }) {
  return (
    <div className={styles.segmented} role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" aria-checked={value === o.value} className={styles.segment} onClick={() => onChange(o.value)}>
          {o.label}{o.count != null && <span className={styles.segCount}>{o.count}</span>}
        </button>
      ))}
    </div>
  );
}

function Switch({ checked, onChange, label, disabled }) {
  return (
    <button type="button" role="switch" aria-checked={checked} aria-label={label} className={styles.switch} onClick={() => onChange(!checked)} disabled={disabled}>
      <span className={styles.switchKnob} />
    </button>
  );
}

function SortTh({ label, k, sort, setSort, align = "right", defaultDir = "desc" }) {
  const active = sort.key === k;
  function click() { setSort(active ? { key: k, dir: sort.dir === "asc" ? "desc" : "asc" } : { key: k, dir: defaultDir }); }
  return (
    <th className={align === "right" ? styles.thNum : undefined} aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}>
      <button type="button" className={styles.sortBtn} data-active={active || undefined} onClick={click}>
        {label}
        <span className={styles.sortIcon}><Icon name={active && sort.dir === "asc" ? "arrowUp" : "arrowDown"} size={12} strokeWidth={2} /></span>
      </button>
    </th>
  );
}

function TableSkeleton({ rows = 6 }) {
  return (
    <div className={styles.skelRows}>
      {Array.from({ length: rows }, (_, i) => <Skeleton key={i} height="36px" />)}
    </div>
  );
}

function LoadError({ text, onRetry }) {
  return (
    <div className={styles.loadError}>
      <p>Не удалось загрузить данные: {text}</p>
      {onRetry && <Button size="sm" variant="secondary" onClick={onRetry}>Повторить</Button>}
    </div>
  );
}

function Empty({ children }) { return <p className={styles.emptyRow}>{children}</p>; }

function RateCell({ value }) {
  return (
    <span className={styles.rate}>
      <span className={styles.rateBar}><span style={{ transform: `scaleX(${Math.max(0, Math.min(1, value || 0))})` }} /></span>
      <span className={styles.numText}>{pct(value)}</span>
    </span>
  );
}

function Confirm({ state, onClose }) {
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!state) return undefined;
    const onKey = (e) => { if (e.key === "Escape" && !busy) onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [state, busy, onClose]);
  if (!state) return null;
  function go() {
    setBusy(true);
    Promise.resolve(state.run()).finally(() => { setBusy(false); onClose(); });
  }
  return (
    <div className={styles.overlay} onClick={() => !busy && onClose()}>
      <div className={styles.dialog} role="alertdialog" aria-modal="true" aria-labelledby="admin-confirm-title" onClick={(e) => e.stopPropagation()}>
        <h2 id="admin-confirm-title" className={styles.dialogTitle}>{state.title}</h2>
        {state.message && <p className={styles.dialogText}>{state.message}</p>}
        <div className={styles.dialogActions}>
          <Button variant="secondary" onClick={onClose} disabled={busy}>Отмена</Button>
          <Button onClick={go} disabled={busy} className={state.danger ? styles.dangerBtn : undefined} autoFocus>{state.confirmLabel || "Подтвердить"}</Button>
        </div>
      </div>
    </div>
  );
}

function useLoad(fn, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let alive = true;
    setError(null);
    fn().then((d) => { if (alive) setData(d); }, (e) => { if (alive) setError(errText(e)); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  return [data, error, reload, setData];
}

// ---------------------------------------------------------------------------
// Обзор

const CSV = [["players", "Проводники"], ["scenarios", "Сценарии"], ["blocks", "Блоки"], ["teams", "Команды"]];

function Overview({ notify }) {
  const [d, err, reload] = useLoad(() => Promise.all([api.adminOverview(), api.adminBlocks()]).then(([overview, blocks]) => ({ overview, blocks })));
  const [sort, setSort] = useState({ key: "successRate", dir: "asc" });
  const [csvBusy, setCsvBusy] = useState(null);

  function csv(k, label) {
    setCsvBusy(k);
    Promise.resolve(api.adminDownloadCsv(k)).then(
      () => notify("Выгрузка готова", `${label}, CSV`),
      (e) => notify("Не удалось выгрузить CSV", errText(e))
    ).finally(() => setCsvBusy(null));
  }

  const blocks = useMemo(() => sortRows((d && d.blocks) || [], sort, (b) => (sort.key === "block" ? blockTitle(b.block) : b[sort.key])), [d, sort]);

  if (err) return <LoadError text={err} onRetry={reload} />;
  const o = d && d.overview;
  const completion = o && o.totalPlaythroughs ? o.completedPlaythroughs / o.totalPlaythroughs : null;

  return (
    <div className={styles.stackY}>
      <div className={styles.kpis}>
        {!o ? Array.from({ length: 8 }, (_, i) => <Skeleton key={i} height="92px" />) : (
          <>
            <Kpi label="Проводников" value={num(o.totalPlayers)} />
            <Kpi label="Активны за сутки" value={num(o.activePlayers24h)} note={o.totalPlayers ? `${pct(o.activePlayers24h / o.totalPlayers)} от всех` : null} />
            <Kpi label="Активны за неделю" value={num(o.activePlayers7d)} note={o.totalPlayers ? `${pct(o.activePlayers7d / o.totalPlayers)} от всех` : null} />
            <Kpi label="Прохождений" value={num(o.totalPlaythroughs)} note={`${num(o.inProgressPlaythroughs)} в процессе`} />
            <Kpi label="Завершено" value={num(o.completedPlaythroughs)} />
            <Kpi label="Доля завершённых" value={pct(completion)} />
            <Kpi label="Средняя безопасность" value={num(o.avgSafetyScore)} note="из 100" />
            <Kpi label="Средняя лояльность" value={num(o.avgLoyaltyScore)} note="из 100" />
          </>
        )}
      </div>

      <Panel title="Итоги завершённых прохождений">
        {!o ? <Skeleton height="48px" /> : (
          <>
            <div className={styles.stackBar} role="img" aria-label={`Успешно ${pct(o.successRate)}, частично ${pct(o.partialRate)}, с критическими ошибками ${pct(o.failureRate)}`}>
              <span style={{ flexGrow: o.successRate || 0 }} data-tone="1" />
              <span style={{ flexGrow: o.partialRate || 0 }} data-tone="2" />
              <span style={{ flexGrow: o.failureRate || 0 }} data-tone="3" />
            </div>
            <ul className={styles.legend}>
              <li data-tone="1">Успешно <b>{pct(o.successRate)}</b></li>
              <li data-tone="2">Частично <b>{pct(o.partialRate)}</b></li>
              <li data-tone="3">С критическими ошибками <b>{pct(o.failureRate)}</b></li>
            </ul>
          </>
        )}
      </Panel>

      <Panel title="Блоки ситуаций" note="По умолчанию сверху — блоки с наименьшей долей успешных прохождений." flush>
        {!d ? <TableSkeleton rows={6} /> : blocks.length === 0 ? <Empty>Данных по блокам пока нет.</Empty> : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <SortTh label="Блок" k="block" sort={sort} setSort={setSort} align="left" defaultDir="asc" />
                  <SortTh label="Прохождений" k="totalPlaythroughs" sort={sort} setSort={setSort} />
                  <SortTh label="Завершено" k="completedPlaythroughs" sort={sort} setSort={setSort} />
                  <SortTh label="Успешно" k="successRate" sort={sort} setSort={setSort} />
                  <SortTh label="Безопасность" k="avgSafetyScore" sort={sort} setSort={setSort} />
                  <SortTh label="Лояльность" k="avgLoyaltyScore" sort={sort} setSort={setSort} />
                </tr>
              </thead>
              <tbody>
                {blocks.map((b) => (
                  <tr key={b.block}>
                    <td className={styles.strong}>{blockTitle(b.block)}</td>
                    <td className={styles.tdNum}>{num(b.totalPlaythroughs)}</td>
                    <td className={styles.tdNum}>{num(b.completedPlaythroughs)}</td>
                    <td className={styles.tdNum}><RateCell value={b.successRate} /></td>
                    <td className={styles.tdNum}>{num(b.avgSafetyScore)}</td>
                    <td className={styles.tdNum}>{num(b.avgLoyaltyScore)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <Panel title="Выгрузки" note="CSV для HR и аналитики: разделитель «;», кодировка UTF-8.">
        <div className={styles.row}>
          {CSV.map(([k, label]) => (
            <Button key={k} variant="secondary" size="sm" onClick={() => csv(k, label)} disabled={csvBusy === k}>
              <Icon name="download" size={14} />{label}
            </Button>
          ))}
        </div>
      </Panel>
    </div>
  );
}

function Kpi({ label, value, note }) {
  return (
    <div className={styles.kpi}>
      <p className={styles.kpiLabel}>{label}</p>
      <p className={styles.kpiValue}>{value}</p>
      {note && <p className={styles.kpiNote}>{note}</p>}
    </div>
  );
}

function sortRows(rows, sort, get) {
  const dir = sort.dir === "asc" ? 1 : -1;
  return rows.slice().sort((a, b) => {
    const x = get(a);
    const y = get(b);
    if (x == null && y == null) return 0;
    if (x == null) return 1;
    if (y == null) return -1;
    if (typeof x === "string" || typeof y === "string") return String(x).localeCompare(String(y), "ru") * dir;
    return (x - y) * dir;
  });
}

// ---------------------------------------------------------------------------
// Учётные записи

function Users({ notify, selfId }) {
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [role, setRole] = useState("all");
  const [confirm, setConfirm] = useState(null);
  useEffect(() => { const t = window.setTimeout(() => setQuery(q.trim()), 250); return () => window.clearTimeout(t); }, [q]);
  const [list, err, reload, setList] = useLoad(() => api.adminUsers(query), [query]);

  const rows = useMemo(() => (list || []).filter((u) => role === "all" || (role === "admin" ? u.role === "ADMIN" : u.role !== "ADMIN")), [list, role]);
  const admins = (list || []).filter((u) => u.role === "ADMIN").length;

  function patch(u, body, okTitle) {
    return api.adminUpdateUser(u.id, body).then((res) => {
      setList((l) => (l || []).map((x) => (x.id === u.id ? { ...x, ...body, ...(res && res.id ? res : {}) } : x)));
      notify(okTitle, u.displayName || u.login);
    }, (e) => notify("Не удалось изменить учётную запись", errText(e)));
  }

  function askRole(u) {
    const makeAdmin = u.role !== "ADMIN";
    setConfirm({
      title: makeAdmin ? "Назначить администратором?" : "Снять права администратора?",
      message: makeAdmin
        ? `${u.displayName || u.login} получит полный доступ к консоли: учётные записи, сценарии, события и награды.`
        : `${u.displayName || u.login} потеряет доступ к консоли администрирования.`,
      confirmLabel: makeAdmin ? "Назначить" : "Снять права",
      danger: !makeAdmin,
      run: () => patch(u, { role: makeAdmin ? "ADMIN" : "USER" }, makeAdmin ? "Назначен администратор" : "Права администратора сняты")
    });
  }

  function askVerify(u) {
    const verify = !u.verified;
    setConfirm({
      title: verify ? "Подтвердить личность?" : "Снять подтверждение?",
      message: verify
        ? "Учётная запись будет отмечена как подтверждённая: очки начисляются полностью, доступны награды для подтверждённых."
        : "Отметка о подтверждении будет снята: очки начисляются с понижающим коэффициентом.",
      confirmLabel: verify ? "Подтвердить" : "Снять подтверждение",
      danger: !verify,
      run: () => patch(u, { verified: verify }, verify ? "Личность подтверждена" : "Подтверждение снято")
    });
  }

  return (
    <Panel
      flush
      actions={
        <>
          <Segmented label="Роль" value={role} onChange={setRole} options={[
            { value: "all", label: "Все" },
            { value: "admin", label: "Администраторы", count: list ? admins : null },
            { value: "user", label: "Пользователи" }
          ]} />
          <SearchBox value={q} onChange={setQ} placeholder="Имя, логин или почта" />
        </>
      }
    >
      {err ? <LoadError text={err} onRetry={reload} /> : !list ? <TableSkeleton /> : rows.length === 0 ? <Empty>{query ? "Ничего не найдено." : "Учётных записей нет."}</Empty> : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Имя</th><th>Логин</th><th>Почта</th><th>Роль</th><th>Подтверждение</th><th className={styles.thNum}>Создана</th><th className={styles.thNum}><span className="visually-hidden">Действия</span></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((u) => {
                const self = u.id === selfId;
                const isAdmin = u.role === "ADMIN";
                return (
                  <tr key={u.id}>
                    <td className={styles.strong}>{u.displayName || "—"}{self && <span className={styles.youTag}>вы</span>}</td>
                    <td className={styles.mono}>{u.login}</td>
                    <td className={styles.muted}>{u.email || "—"}</td>
                    <td><span className={styles.pill} data-tone={isAdmin ? "blue" : undefined}>{isAdmin ? "Администратор" : "Пользователь"}</span></td>
                    <td>{u.verified ? <VerifiedBadge size={14} withLabel /> : <span className={styles.muted}>Нет</span>}</td>
                    <td className={styles.tdNum}>{fmtDate(u.createdAt)}</td>
                    <td className={styles.tdActions}>
                      <Button size="sm" variant="ghost" onClick={() => askVerify(u)}>{u.verified ? "Снять подтверждение" : "Подтвердить"}</Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => askRole(u)}
                        disabled={self && isAdmin}
                        title={self && isAdmin ? "Нельзя снять права с собственной учётной записи" : undefined}
                      >
                        {isAdmin ? "Снять админа" : "Сделать админом"}
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <Confirm state={confirm} onClose={() => setConfirm(null)} />
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Проводники

function Players() {
  const [list, err, reload] = useLoad(() => api.adminPlayers());
  const [q, setQ] = useState("");
  const [sort, setSort] = useState({ key: "totalScore", dir: "desc" });
  const rows = useMemo(() => {
    const n = q.trim().toLowerCase();
    const f = (list || []).filter((p) => !n || [p.displayName, p.teamName].some((x) => String(x || "").toLowerCase().includes(n)));
    return sortRows(f, sort, (p) => (sort.key === "lastActivity" ? (p.lastActivity ? new Date(p.lastActivity).getTime() : null) : sort.key === "displayName" ? p.displayName : p[sort.key]));
  }, [list, q, sort]);

  return (
    <Panel
      flush
      note={list ? `${list.length} в статистике` : null}
      title="Статистика обучения"
      actions={<SearchBox value={q} onChange={setQ} placeholder="Имя или команда" />}
    >
      {err ? <LoadError text={err} onRetry={reload} /> : !list ? <TableSkeleton rows={8} /> : rows.length === 0 ? <Empty>{q ? "Ничего не найдено." : "Проводники ещё не проходили сценарии."}</Empty> : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <SortTh label="Проводник" k="displayName" sort={sort} setSort={setSort} align="left" defaultDir="asc" />
                <th>Команда</th>
                <SortTh label="Очки" k="totalScore" sort={sort} setSort={setSort} />
                <SortTh label="Прохождений" k="totalPlaythroughs" sort={sort} setSort={setSort} />
                <SortTh label="Успешно" k="successRate" sort={sort} setSort={setSort} />
                <SortTh label="Безопасность" k="avgSafetyScore" sort={sort} setSort={setSort} />
                <SortTh label="Лояльность" k="avgLoyaltyScore" sort={sort} setSort={setSort} />
                <SortTh label="Активность" k="lastActivity" sort={sort} setSort={setSort} />
              </tr>
            </thead>
            <tbody>
              {rows.map((p) => (
                <tr key={p.playerId}>
                  <td className={styles.strong}>{p.displayName || "Без имени"}</td>
                  <td className={styles.muted}>{p.teamName || "—"}</td>
                  <td className={styles.tdNum}>{num(p.totalScore)}</td>
                  <td className={styles.tdNum}>{num(p.totalPlaythroughs)}</td>
                  <td className={styles.tdNum}><RateCell value={p.successRate} /></td>
                  <td className={styles.tdNum}>{num(p.avgSafetyScore)}</td>
                  <td className={styles.tdNum}>{num(p.avgLoyaltyScore)}</td>
                  <td className={styles.tdNum} title={fmtDateTime(p.lastActivity)}>{ago(p.lastActivity)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}

// ---------------------------------------------------------------------------
// Сценарии

const MD_TEMPLATE = `# Пассажир просит поменяться местами
Код: seat-swap-family
Блок: seating
Описание: Семья хочет сидеть вместе, соседний пассажир против.

## start
Пассажирка с ребёнком просит пересадить её к мужу — через проход, на соседнее место.

- [explain] Уточнить свободные места и предложить вариант обмена без ущерба для других -> agree (лояльность +8, безопасность +2)
> Пояснение: решение по правилам, никого не ущемляет.
> Шаги: признать, правило, решение, заверить
- [refuse] Отказать: «Места по билетам» -> end-bad (лояльность -8)
Таймер: 30 с, по умолчанию: refuse

## agree
Пассажиры договорились, семья сидит вместе.
Итог: SUCCESS

## end-bad
Семья недовольна, пишет жалобу.
Итог: PARTIAL
`;

function Scenarios({ notify }) {
  const [data, err, reload, setData] = useLoad(() => Promise.all([
    api.adminScenarios(),
    api.adminScenarioStats().catch(() => [])
  ]).then(([list, stats]) => {
    const byCode = {};
    (stats || []).forEach((s) => { if (s.code) byCode[s.code] = s; });
    return (list || []).map((s) => ({ ...s, stats: byCode[s.code] || null }));
  }));
  const [status, setStatus] = useState("all");
  const [block, setBlock] = useState("all");
  const [q, setQ] = useState("");
  const [pending, setPending] = useState({});
  const [showImport, setShowImport] = useState(false);
  const [exported, setExported] = useState(null);

  const list = data || [];
  const blocks = useMemo(() => {
    const keys = [];
    list.forEach((s) => { const b = s.block || "misc"; if (!keys.includes(b)) keys.push(b); });
    return keys.sort((a, b) => blockTitle(a).localeCompare(blockTitle(b), "ru"));
  }, [list]);

  const filtered = useMemo(() => {
    const n = q.trim().toLowerCase();
    return list.filter((s) =>
      (status === "all" || (status === "on" ? s.active : !s.active)) &&
      (block === "all" || (s.block || "misc") === block) &&
      (!n || String(s.title || "").toLowerCase().includes(n) || String(s.code || "").toLowerCase().includes(n))
    );
  }, [list, status, block, q]);

  const groups = useMemo(() => {
    const g = {};
    filtered.forEach((s) => { const b = s.block || "misc"; (g[b] = g[b] || []).push(s); });
    return blocks.filter((b) => g[b]).map((b) => ({ block: b, items: g[b] }));
  }, [filtered, blocks]);

  const activeCount = list.filter((s) => s.active).length;

  function toggle(s, next) {
    setPending((p) => ({ ...p, [s.code]: true }));
    setData((l) => l.map((x) => (x.code === s.code ? { ...x, active: next } : x)));
    api.adminSetScenarioActive(s.code, next).then(
      () => notify(next ? "Сценарий включён" : "Сценарий выключен", s.title),
      (e) => {
        setData((l) => l.map((x) => (x.code === s.code ? { ...x, active: !next } : x)));
        notify("Не удалось изменить доступность", `${s.title}: ${errText(e)}`);
      }
    ).finally(() => setPending((p) => { const c = { ...p }; delete c[s.code]; return c; }));
  }

  function exportJson(s) {
    api.editorExport(s.code).then(
      (d) => setExported({ code: s.code, title: s.title, json: JSON.stringify(d, null, 2) }),
      (e) => notify("Не удалось экспортировать", errText(e))
    );
  }

  return (
    <div className={styles.stackY}>
      {showImport && (
        <MarkdownImport
          notify={notify}
          onClose={() => setShowImport(false)}
          onSaved={reload}
        />
      )}

      <Panel
        flush
        title="Каталог"
        note={data ? `${list.length} сценариев · ${activeCount} доступны проводникам` : null}
        actions={!showImport && <Button size="sm" onClick={() => setShowImport(true)}><Icon name="plus" size={14} />Добавить сценарий</Button>}
      >
        <div className={styles.toolbar}>
          <Segmented label="Доступность" value={status} onChange={setStatus} options={[
            { value: "all", label: "Все", count: data ? list.length : null },
            { value: "on", label: "Включены", count: data ? activeCount : null },
            { value: "off", label: "Выключены", count: data ? list.length - activeCount : null }
          ]} />
          <label className={styles.selectWrap}>
            <span className="visually-hidden">Блок</span>
            <select value={block} onChange={(e) => setBlock(e.target.value)}>
              <option value="all">Все блоки</option>
              {blocks.map((b) => <option key={b} value={b}>{blockTitle(b)}</option>)}
            </select>
            <Icon name="chevronDown" size={14} />
          </label>
          <span className={styles.grow} />
          <SearchBox value={q} onChange={setQ} placeholder="Название или код" />
        </div>

        {err ? <LoadError text={err} onRetry={reload} /> : !data ? <TableSkeleton rows={8} /> : groups.length === 0 ? <Empty>Нет сценариев по выбранным условиям.</Empty> : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Сценарий</th>
                  <th className={styles.thNum}>Прохождений</th>
                  <th className={styles.thNum}>Успешно</th>
                  <th className={styles.thNum}>Таймауты</th>
                  <th>Доступен</th>
                  <th className={styles.thNum}><span className="visually-hidden">Экспорт</span></th>
                </tr>
              </thead>
              {groups.map((g) => (
                <tbody key={g.block}>
                  <tr className={styles.groupRow}>
                    <th colSpan={6} scope="colgroup">{blockTitle(g.block)}<span className={styles.groupCount}>{g.items.length}</span></th>
                  </tr>
                  {g.items.map((s) => (
                    <tr key={s.code} data-off={!s.active || undefined}>
                      <td>
                        <span className={styles.cellTitle}>{s.title}</span>
                        <span className={styles.cellSub}>
                          <span className={styles.mono}>{s.code}</span>
                          {s.version != null && <> · версия {s.version}</>}
                          {s.flagship && <> · расширенный</>}
                        </span>
                      </td>
                      <td className={styles.tdNum}>{s.stats ? num(s.stats.totalPlaythroughs) : "—"}</td>
                      <td className={styles.tdNum}>{s.stats ? <RateCell value={s.stats.successRate} /> : "—"}</td>
                      <td className={styles.tdNum}>{s.stats ? pct(s.stats.timeoutRate) : "—"}</td>
                      <td>
                        <span className={styles.switchCell}>
                          <Switch checked={!!s.active} onChange={(v) => toggle(s, v)} disabled={!!pending[s.code]} label={`Доступность: ${s.title}`} />
                          <span className={styles.switchLabel}>{s.active ? "Включён" : "Выключен"}</span>
                        </span>
                      </td>
                      <td className={styles.tdActions}>
                        <Button size="sm" variant="ghost" onClick={() => exportJson(s)}><Icon name="code" size={14} />JSON</Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              ))}
            </table>
          </div>
        )}
      </Panel>

      {exported && <JsonDialog data={exported} onClose={() => setExported(null)} notify={notify} />}
    </div>
  );
}

function MarkdownImport({ notify, onClose, onSaved }) {
  const [md, setMd] = useState(MD_TEMPLATE);
  const [check, setCheck] = useState(null);
  const [busy, setBusy] = useState(false);

  function run(save) {
    setBusy(true);
    setCheck(null);
    api.editorImportMarkdown(md, save).then((r) => {
      setBusy(false);
      if (save) {
        setCheck({ ok: true, text: `Сохранено: «${r.title || r.code}». Сценарий доступен в каталоге.` });
        notify("Сценарий сохранён", r.title || r.code);
        onSaved();
        return;
      }
      const errors = r.graphErrors || [];
      const sc = r.scenario || {};
      setCheck({
        ok: !errors.length,
        preview: { title: sc.title, code: sc.code, block: sc.block, nodes: sc.nodes ? sc.nodes.length : 0 },
        text: errors.length ? errors.join("\n") : "Разметка корректна, граф без ошибок."
      });
    }, (e) => { setBusy(false); setCheck({ ok: false, text: errText(e) }); });
  }

  return (
    <Panel
      title="Новый сценарий из markdown"
      note="Заголовок, код и блок, затем узлы: реплика, варианты с эффектами на шкалы, пояснения и итог. Перед сохранением граф проверяется."
      actions={<Button size="sm" variant="ghost" onClick={onClose}><Icon name="x" size={14} />Закрыть</Button>}
    >
      <div className={styles.importGrid}>
        <textarea className={styles.editor} value={md} onChange={(e) => setMd(e.target.value)} spellCheck={false} aria-label="Текст сценария в формате markdown" />
        <div className={styles.importSide}>
          <p className={styles.sideTitle}>Проверка</p>
          {!check && <p className={styles.note}>Нажмите «Проверить», чтобы увидеть результат разбора до сохранения.</p>}
          {check && check.preview && (
            <dl className={styles.dl}>
              <dt>Название</dt><dd>{check.preview.title || "—"}</dd>
              <dt>Код</dt><dd className={styles.mono}>{check.preview.code || "—"}</dd>
              {check.preview.block && <><dt>Блок</dt><dd>{blockTitle(check.preview.block)}</dd></>}
              <dt>Узлов</dt><dd className={styles.numText}>{check.preview.nodes}</dd>
            </dl>
          )}
          {check && <pre className={styles.check} data-ok={check.ok || undefined}>{check.text}</pre>}
          <div className={styles.row}>
            <Button variant="secondary" size="sm" onClick={() => run(false)} disabled={busy}>Проверить</Button>
            <Button size="sm" onClick={() => run(true)} disabled={busy}>Сохранить в каталог</Button>
            <Button variant="ghost" size="sm" onClick={() => { setMd(MD_TEMPLATE); setCheck(null); }} disabled={busy}>Шаблон</Button>
          </div>
        </div>
      </div>
    </Panel>
  );
}

function JsonDialog({ data, onClose, notify }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  function download() {
    const url = URL.createObjectURL(new Blob([data.json], { type: "application/json" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `${data.code}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  function copy() {
    if (navigator.clipboard) navigator.clipboard.writeText(data.json).then(() => notify("Скопировано", `${data.code}.json`), () => notify("Не удалось скопировать"));
  }
  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={`${styles.dialog} ${styles.dialogWide}`} role="dialog" aria-modal="true" aria-labelledby="admin-json-title" onClick={(e) => e.stopPropagation()}>
        <div className={styles.dialogHead}>
          <div>
            <h2 id="admin-json-title" className={styles.dialogTitle}>{data.title}</h2>
            <p className={`${styles.note} ${styles.mono}`}>{data.code}.json</p>
          </div>
          <button type="button" className={styles.iconBtn} onClick={onClose} aria-label="Закрыть"><Icon name="x" size={16} /></button>
        </div>
        <pre className={styles.json}>{data.json}</pre>
        <div className={styles.dialogActions}>
          <Button variant="secondary" size="sm" onClick={copy}>Копировать</Button>
          <Button size="sm" onClick={download}><Icon name="download" size={14} />Скачать</Button>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// События

const GOALS = [
  { key: "BLOCK_SCENARIOS_NO_FAILURE", label: "Ситуации блока без критических ошибок" },
  { key: "SAFETY_STREAK", label: "Серия прохождений с высокой безопасностью" },
  { key: "ROLE_MODEL_ALL_STEPS", label: "Прохождения со всеми шагами ролевой модели" }
];
const ACHIEVEMENTS = [["", "Без ачивки"], ["CHALLENGE_CHAMPION", "Чемпион месяца"], ["FLAWLESS_SAFETY", "Безупречная безопасность"], ["PASSENGER_FAVORITE", "Любимец пассажиров"], ["VERSATILE", "Универсал"], ["CERTIFICATE", "Сертификат"]];

function toLocalInput(d) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function goalText(e) {
  const g = GOALS.find((x) => x.key === e.goalType);
  const base = g ? g.label : e.goalType;
  const extra = [];
  if (e.targetBlock) extra.push(blockTitle(e.targetBlock));
  if (e.safetyThreshold != null) extra.push(`порог ${e.safetyThreshold}`);
  return `${base}${extra.length ? ` (${extra.join(", ")})` : ""} · ${e.targetCount || 1} раз`;
}

function Events({ notify }) {
  const [list, err, reload] = useLoad(() => api.adminEvents());
  const now = new Date();
  const [f, setF] = useState({ title: "", description: "", goalType: GOALS[0].key, targetBlock: "safety", targetCount: 3, safetyThreshold: 70, startsAt: toLocalInput(now), endsAt: toLocalInput(new Date(now.getTime() + 7 * 864e5)), rewardPoints: 300, rewardAchievementCode: "" });
  const [busy, setBusy] = useState(false);
  const [filter, setFilter] = useState("all");
  const [confirm, setConfirm] = useState(null);
  const set = (k) => (e) => setF((x) => ({ ...x, [k]: e.target.type === "number" ? Number(e.target.value) : e.target.value }));

  function create(e) {
    e.preventDefault();
    if (new Date(f.endsAt) <= new Date(f.startsAt)) { notify("Проверьте даты", "Окончание должно быть позже начала."); return; }
    setBusy(true);
    const body = {
      ...f,
      targetBlock: f.goalType === "BLOCK_SCENARIOS_NO_FAILURE" ? f.targetBlock : null,
      safetyThreshold: f.goalType === "SAFETY_STREAK" ? f.safetyThreshold : null,
      startsAt: new Date(f.startsAt).toISOString(),
      endsAt: new Date(f.endsAt).toISOString(),
      rewardAchievementCode: f.rewardAchievementCode || null
    };
    api.adminCreateEvent(body).then(() => {
      notify("Событие создано", "Оно появится у проводников в списке целей.");
      setF((x) => ({ ...x, title: "", description: "" }));
      reload();
    }, (e2) => notify("Не удалось создать событие", errText(e2))).finally(() => setBusy(false));
  }

  function askFinish(ev) {
    setConfirm({
      title: "Завершить событие?",
      message: `«${ev.title}» завершится сейчас. Проводники больше не смогут его выполнить.`,
      confirmLabel: "Завершить",
      danger: true,
      run: () => api.adminFinishEvent(ev.id).then(() => { notify("Событие завершено", ev.title); reload(); }, (e) => notify("Не удалось завершить событие", errText(e)))
    });
  }

  const items = (list || []).filter((e) => filter === "all" || (filter === "active" ? e.active : !e.active));
  const activeCount = (list || []).filter((e) => e.active).length;

  return (
    <div className={styles.split}>
      <Panel
        flush
        title="Список событий"
        note="Ежемесячные цели создаются автоматически и здесь не показываются."
        actions={<Segmented label="Статус" value={filter} onChange={setFilter} options={[
          { value: "all", label: "Все" },
          { value: "active", label: "Идут", count: list ? activeCount : null },
          { value: "done", label: "Завершены" }
        ]} />}
      >
        {err ? <LoadError text={err} onRetry={reload} /> : !list ? <TableSkeleton rows={4} /> : items.length === 0 ? <Empty>{list.length ? "Нет событий с таким статусом." : "Событий пока нет. Создайте первое в форме справа."}</Empty> : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr><th>Событие</th><th>Период</th><th className={styles.thNum}>Очки</th><th>Статус</th><th className={styles.thNum}><span className="visually-hidden">Действия</span></th></tr>
              </thead>
              <tbody>
                {items.map((ev) => (
                  <tr key={ev.id}>
                    <td>
                      <span className={styles.cellTitle}>{ev.title}</span>
                      <span className={styles.cellSub}>{goalText(ev)}</span>
                      {ev.description && <span className={styles.cellSub}>{ev.description}</span>}
                    </td>
                    <td className={styles.nowrap}>{fmtDate(ev.startsAt)} — {fmtDate(ev.endsAt)}</td>
                    <td className={styles.tdNum}>{num(ev.rewardPoints)}</td>
                    <td><span className={styles.pill} data-tone={ev.active ? "blue" : undefined}>{ev.active ? "Идёт" : "Завершено"}</span></td>
                    <td className={styles.tdActions}>
                      {ev.active && <Button size="sm" variant="secondary" onClick={() => askFinish(ev)}>Завершить</Button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <Panel title="Новое событие">
        <form className={styles.form} onSubmit={create}>
          <label>Название<input value={f.title} onChange={set("title")} required maxLength={150} placeholder="Неделя безопасности" /></label>
          <label>Описание<textarea value={f.description} onChange={set("description")} required maxLength={500} rows={3} placeholder="Что нужно сделать, чтобы выполнить событие" /></label>
          <label>Цель<select value={f.goalType} onChange={set("goalType")}>{GOALS.map((g) => <option key={g.key} value={g.key}>{g.label}</option>)}</select></label>
          {f.goalType === "BLOCK_SCENARIOS_NO_FAILURE" && (
            <label>Блок ситуаций<select value={f.targetBlock} onChange={set("targetBlock")}>{Object.keys(BLOCK_TITLES).map((k) => <option key={k} value={k}>{BLOCK_TITLES[k]}</option>)}</select></label>
          )}
          <div className={styles.row2}>
            <label>Сколько раз<input type="number" min={1} max={100} value={f.targetCount} onChange={set("targetCount")} /></label>
            {f.goalType === "SAFETY_STREAK" && <label>Порог безопасности<input type="number" min={0} max={100} value={f.safetyThreshold} onChange={set("safetyThreshold")} /></label>}
          </div>
          <div className={styles.row2}>
            <label>Начало<input type="datetime-local" value={f.startsAt} onChange={set("startsAt")} required /></label>
            <label>Окончание<input type="datetime-local" value={f.endsAt} onChange={set("endsAt")} required /></label>
          </div>
          <div className={styles.row2}>
            <label>Очки за выполнение<input type="number" min={0} max={5000} value={f.rewardPoints} onChange={set("rewardPoints")} /></label>
            <label>Награда<select value={f.rewardAchievementCode} onChange={set("rewardAchievementCode")}>{ACHIEVEMENTS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></label>
          </div>
          <div className={styles.row}><Button type="submit" disabled={busy}>Создать событие</Button></div>
        </form>
      </Panel>
      <Confirm state={confirm} onClose={() => setConfirm(null)} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Награды

const GLYPHS = ["medal", "shield", "train", "users", "smile", "flag", "sparkle", "hand", "coffee", "cross", "ticket", "bolt", "flame"];
const SHAPES = [
  { value: "circle", label: "Круг" },
  { value: "hexagon", label: "Шестигранник" },
  { value: "octagon", label: "Восьмигранник" },
  { value: "shield", label: "Щит" }
];

function Awards({ notify }) {
  const [list, err, reload] = useLoad(() => api.adminAwards());
  const [f, setF] = useState({ title: "", description: "", shape: "shield", glyph: "sparkle", verifiedOnly: false });
  const [grant, setGrant] = useState({});
  const [busy, setBusy] = useState(null);

  function create(e) {
    e.preventDefault();
    setBusy("create");
    api.adminCreateAward(f).then(() => {
      notify("Награда создана", f.title);
      setF((x) => ({ ...x, title: "", description: "" }));
      reload();
    }, (e2) => notify("Не удалось создать награду", errText(e2))).finally(() => setBusy(null));
  }

  function give(a) {
    const who = (grant[a.id] || "").trim();
    if (!who) return;
    setBusy(a.id);
    api.adminGrantAward(a.id, who).then(() => {
      notify("Награда выдана", `«${a.title}» — ${who}`);
      setGrant((g) => ({ ...g, [a.id]: "" }));
      reload();
    }, (e) => notify("Не удалось выдать награду", errText(e))).finally(() => setBusy(null));
  }

  return (
    <div className={styles.split}>
      <Panel flush title="Награды программы" note="Встроенные ачивки выдаются автоматически. Созданные здесь награды выдаются вручную — по логину или идентификатору игрока.">
        {err ? <LoadError text={err} onRetry={reload} /> : !list ? <TableSkeleton rows={3} /> : list.length === 0 ? <Empty>Созданных наград пока нет.</Empty> : (
          <ul className={styles.awardList}>
            {list.map((a) => (
              <li key={a.id}>
                <Medal shape={a.shape} finish="enamel" glyph={a.glyph || "medal"} size={52} />
                <div className={styles.awardBody}>
                  <span className={styles.cellTitle}>
                    {a.title}
                    {a.verifiedOnly && <span className={styles.pill} data-tone="blue">только подтверждённым</span>}
                  </span>
                  <span className={styles.cellSub}>{a.description}</span>
                  <span className={styles.cellSub}>Выдано: <span className={styles.numText}>{num(a.grantedCount)}</span></span>
                </div>
                <form className={styles.grantRow} onSubmit={(e) => { e.preventDefault(); give(a); }}>
                  <input placeholder="Логин или ID игрока" value={grant[a.id] || ""} onChange={(e) => setGrant((g) => ({ ...g, [a.id]: e.target.value }))} aria-label={`Кому выдать «${a.title}»`} />
                  <Button type="submit" size="sm" variant="secondary" disabled={busy === a.id || !(grant[a.id] || "").trim()}>Выдать</Button>
                </form>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Panel title="Новая награда">
        <div className={styles.preview}>
          <Medal shape={f.shape} finish="enamel" glyph={f.glyph} size={104} spin backTitle={f.title || "Награда"} backNote="ReactLab · ВСМ" />
        </div>
        <form className={styles.form} onSubmit={create}>
          <label>Название<input value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} required maxLength={120} placeholder="Наставник смены" /></label>
          <label>За что<textarea rows={2} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} required maxLength={300} placeholder="Помог коллеге-стажёру на рейсе" /></label>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Форма</span>
            <Segmented label="Форма" value={f.shape} onChange={(v) => setF({ ...f, shape: v })} options={SHAPES} />
          </div>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Пиктограмма</span>
            <div className={styles.glyphs} role="radiogroup" aria-label="Пиктограмма">
              {GLYPHS.map((g) => (
                <button key={g} type="button" role="radio" aria-checked={f.glyph === g} className={styles.glyph} onClick={() => setF({ ...f, glyph: g })} aria-label={g}>
                  <Icon name={g} size={18} />
                </button>
              ))}
            </div>
          </div>
          <label className={styles.switchRow}>
            <Switch checked={f.verifiedOnly} onChange={(v) => setF({ ...f, verifiedOnly: v })} label="Только для подтверждённых через Госуслуги" />
            <span>Только для подтверждённых через Госуслуги</span>
          </label>
          <div className={styles.row}><Button type="submit" disabled={busy === "create"}>Создать награду</Button></div>
        </form>
      </Panel>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Стенд

