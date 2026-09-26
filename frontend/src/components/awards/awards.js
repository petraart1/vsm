import { BLOCK_ICON, formatDate } from "../../progress.js";

/**
 * Единый дизайн-код наград (требование QA — строгая, «не детская» система):
 *  - учебный модуль — круглая медаль с пиктограммой блока;
 *  - служебное отличие — шестигранник;
 *  - смена — круглая эмалевая медаль с поездом.
 * Отделка (металл / эмаль / стекло) — одна на всю витрину, выбирается пользователем.
 */
export const FINISH_OPTIONS = [
  { key: "metal", label: "Металл" },
  { key: "enamel", label: "Эмаль" },
  { key: "glass", label: "Стекло" }
];

const KEY = "reactlab.medalFinish";

export function readFinish() {
  try { return localStorage.getItem(KEY) || "enamel"; } catch (e) { return "enamel"; }
}

export function writeFinish(v) {
  try { localStorage.setItem(KEY, v); } catch (e) { /* приватный режим */ }
}

export function moduleMedal(q, finish) {
  return {
    shape: "circle",
    finish,
    glyph: BLOCK_ICON[q.block] || "medal",
    earned: q.status === "certified",
    backTitle: `Модуль ${q.code}`,
    backNote: q.status === "certified" ? `присвоено ${formatDate(q.certifiedAt, { day: "numeric", month: "short" }) || ""}` : `${q.completed} из ${q.total}`
  };
}

export function distinctionMedal(d, finish) {
  return {
    shape: "hexagon",
    finish,
    glyph: d.glyph || undefined,
    text: d.glyph ? undefined : d.mark,
    earned: !!d.earned,
    backTitle: "Служебное отличие",
    backNote: d.earned ? formatDate(d.earnedAt, { day: "numeric", month: "short", year: "numeric" }) : "ещё не получено"
  };
}

/**
 * Все полученные награды в виде «снимков» для витрины: { id, title, shape, glyph, text }.
 * Снимок самодостаточен — его можно показать другому пользователю без каталога.
 */
export function earnedAwards({ qualifications = [], distinctions = [], cleanShifts = 0, streakDays = [] }) {
  const out = [];
  if (cleanShifts > 0) out.push({ id: "shift:clean", title: "Смена без замечаний", shape: "circle", glyph: "train" });
  qualifications.filter((q) => q.status === "certified").forEach((q) => {
    const m = moduleMedal(q, "enamel");
    out.push({ id: `module:${q.block}`, title: q.title, shape: m.shape, glyph: m.glyph });
  });
  distinctions.filter((d) => d.earned).forEach((d) => {
    out.push({ id: `dist:${d.code}`, title: d.title, shape: "hexagon", glyph: d.glyph || null, text: d.glyph ? null : d.mark });
  });
  streakDays.forEach((n) => out.push({ id: `streak:${n}`, title: `Серия ${n} дней`, shape: "circle", text: String(n) }));
  return out;
}
