#!/usr/bin/env node
/**
 * Генерирует frontend/src/styles/tokens.css из design/tokens.json.
 *
 * Источник истины — design/tokens.json (общий контракт web/Android), который сам не входит
 * в репозиторий фронтенда. Скрипт запускается автоматически перед dev/build (npm pre-хуки),
 * должен работать на голом Node без зависимостей. Если design/tokens.json недоступен (например,
 * сборка выполняется без соседнего репозитория дизайна) — скрипт молча ничего не делает и
 * оставляет уже закоммиченный frontend/src/styles/tokens.css как есть.
 *
 * Правила именования CSS custom properties — см. исходную версию на Python
 * (frontend/scripts/generate-tokens-css.py в истории), сохранены без изменений:
 *   color.light.background.canvas -> --color-background-canvas          (:root, светлая тема)
 *   color.dark.background.canvas  -> --color-background-canvas          (:root[data-theme="dark"])
 *   spacing.4                     -> --spacing-4
 *   radius.sm                     -> --radius-sm
 *   elevation.1                   -> --shadow-1
 *   motion.durationFast           -> --motion-duration-fast
 *   typography.scale.h1.size      -> --font-size-h1 (rem), --line-height-h1, --font-weight-h1
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..", "..");
const TOKENS_PATH = path.join(ROOT, "design", "tokens.json");
const OUT_PATH = path.join(__dirname, "..", "src", "styles", "tokens.css");

function kebab(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, "$1-$2").toLowerCase();
}

function colorProps(themeDict) {
  const lines = [];
  for (const [group, values] of Object.entries(themeDict)) {
    if (group.startsWith("_")) continue;
    for (const [key, val] of Object.entries(values)) {
      if (key.startsWith("_")) continue;
      lines.push(`  --color-${kebab(group)}-${kebab(key)}: ${val};`);
    }
  }
  return lines;
}

function main() {
  if (!existsSync(TOKENS_PATH)) {
    console.log(`design/tokens.json not found (${TOKENS_PATH}) — keeping existing tokens.css as is`);
    return;
  }

  let tokens;
  try {
    tokens = JSON.parse(readFileSync(TOKENS_PATH, "utf-8"));
  } catch (err) {
    console.warn(`could not read/parse design/tokens.json (${err.message}) — keeping existing tokens.css as is`);
    return;
  }

  const lightLines = colorProps(tokens.color.light);
  const darkLines = tokens.color.dark ? colorProps(tokens.color.dark) : [];

  const spacingLines = Object.entries(tokens.spacing)
    .filter(([k]) => !k.startsWith("_"))
    .map(([k, v]) => `  --spacing-${k}: ${v}px;`);

  const radiusLines = Object.entries(tokens.radius)
    .filter(([k]) => !k.startsWith("_"))
    .map(([k, v]) => `  --radius-${kebab(k)}: ${typeof v === "string" ? v : `${v}px`};`);

  const shadowLines = Object.entries(tokens.elevation)
    .filter(([k]) => !k.startsWith("_"))
    .map(([k, v]) => `  --shadow-${k}: ${v};`);

  const motion = tokens.motion;
  const motionLines = [
    `  --motion-duration-fast: ${motion.durationFast}ms;`,
    `  --motion-duration-base: ${motion.durationBase}ms;`,
    `  --motion-duration-slow: ${motion.durationSlow}ms;`,
    `  --motion-easing-standard: ${motion.easingStandard};`,
    `  --motion-easing-emphasized: ${motion.easingEmphasized};`
  ];

  const typo = tokens.typography;
  const fontLines = [
    `  --font-family-base: ${typo.fontFamily.base};`,
    `  --font-family-mono: ${typo.fontFamily.mono};`
  ];
  for (const [name, scale] of Object.entries(typo.scale)) {
    const key = kebab(name);
    fontLines.push(`  --font-size-${key}: ${scale.size}rem;`);
    fontLines.push(`  --line-height-${key}: ${scale.lineHeight};`);
    fontLines.push(`  --font-weight-${key}: ${scale.weight};`);
    if (scale.fontStyle) fontLines.push(`  --font-style-${key}: ${scale.fontStyle};`);
  }

  const out = [];
  out.push("/* СГЕНЕРИРОВАНО из design/tokens.json скриптом");
  out.push("   frontend/scripts/generate-tokens-css.mjs — не редактировать вручную,");
  out.push("   при изменении tokens.json перезапустить скрипт (npm run dev / npm run build делают это сами). */");
  out.push("");
  out.push(":root {");
  out.push(...lightLines);
  out.push("");
  out.push(...spacingLines);
  out.push(...radiusLines);
  out.push(...shadowLines);
  out.push(...motionLines);
  out.push(...fontLines);
  out.push("}");

  if (darkLines.length > 0) {
    out.push("");
    out.push('/* Тёмная тема — активируется через :root[data-theme="dark"] (переключатель в Профиле). */');
    out.push("@media (prefers-color-scheme: dark) {");
    out.push('  :root:not([data-theme="light"]) {');
    out.push(...darkLines.map((l) => "  " + l));
    out.push("  }");
    out.push("}");
    out.push(':root[data-theme="dark"] {');
    out.push(...darkLines);
    out.push("}");
  }

  mkdirSync(path.dirname(OUT_PATH), { recursive: true });
  writeFileSync(OUT_PATH, out.join("\n") + "\n", "utf-8");
  console.log(`written ${OUT_PATH} (${out.length} lines)`);
}

main();
