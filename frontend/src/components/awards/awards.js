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
