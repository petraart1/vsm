#!/usr/bin/env python3
"""Генерирует frontend/css/tokens.css из design/tokens.json.

Источник истины — design/tokens.json (общий контракт web/Android). Запускать заново
после любого изменения tokens.json:

    python3 frontend/scripts/generate-tokens-css.py

Правила именования CSS custom properties:
  color.light.background.canvas   -> --color-background-canvas          (на :root, светлая тема)
  color.dark.background.canvas    -> --color-background-canvas          (на :root[data-theme="dark"])
  spacing.4                       -> --spacing-4
  radius.sm                       -> --radius-sm
  elevation.1                     -> --shadow-1
  motion.durationFast             -> --motion-duration-fast
  typography.scale.h1.size        -> --font-size-h1 (rem), --line-height-h1, --font-weight-h1
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
TOKENS_PATH = ROOT / "design" / "tokens.json"
OUT_PATH = ROOT / "frontend" / "css" / "tokens.css"


def kebab(name):
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1-\2", name)
    return s.lower()


def color_props(theme_dict):
    lines = []
    for group, values in theme_dict.items():
        if group.startswith("_"):
            continue
        for key, val in values.items():
            if key.startswith("_"):
                continue
            lines.append(f"  --color-{kebab(group)}-{kebab(key)}: {val};")
    return lines


def main():
    tokens = json.loads(TOKENS_PATH.read_text(encoding="utf-8"))

    light_lines = color_props(tokens["color"]["light"])
    dark_lines = color_props(tokens["color"]["dark"]) if "dark" in tokens["color"] else []

    spacing_lines = [
        f"  --spacing-{k}: {v}px;" for k, v in tokens["spacing"].items() if not k.startswith("_")
    ]
    radius_lines = [
        f"  --radius-{kebab(k)}: {v if isinstance(v, str) else str(v) + 'px'};"
        for k, v in tokens["radius"].items()
        if not k.startswith("_")
    ]
    shadow_lines = [
        f"  --shadow-{k}: {v};" for k, v in tokens["elevation"].items() if not k.startswith("_")
    ]
    motion = tokens["motion"]
    motion_lines = [
        f"  --motion-duration-fast: {motion['durationFast']}ms;",
        f"  --motion-duration-base: {motion['durationBase']}ms;",
        f"  --motion-duration-slow: {motion['durationSlow']}ms;",
        f"  --motion-easing-standard: {motion['easingStandard']};",
        f"  --motion-easing-emphasized: {motion['easingEmphasized']};",
    ]

    typo = tokens["typography"]
    font_lines = [
        f"  --font-family-base: {typo['fontFamily']['base']};",
        f"  --font-family-mono: {typo['fontFamily']['mono']};",
    ]
    for name, scale in typo["scale"].items():
        key = kebab(name)
        font_lines.append(f"  --font-size-{key}: {scale['size']}rem;")
        font_lines.append(f"  --line-height-{key}: {scale['lineHeight']};")
        font_lines.append(f"  --font-weight-{key}: {scale['weight']};")
        if "fontStyle" in scale:
            font_lines.append(f"  --font-style-{key}: {scale['fontStyle']};")

    out = []
    out.append("/* СГЕНЕРИРОВАНО из design/tokens.json скриптом")
    out.append("   frontend/scripts/generate-tokens-css.py — не редактировать вручную,")
    out.append("   при изменении tokens.json перезапустить скрипт. */")
    out.append("")
    out.append(":root {")
    out.extend(light_lines)
    out.append("")
    out.extend(spacing_lines)
    out.extend(radius_lines)
    out.extend(shadow_lines)
    out.extend(motion_lines)
    out.extend(font_lines)
    out.append("}")

    if dark_lines:
        out.append("")
        out.append('/* Тёмная тема — активируется через :root[data-theme="dark"] (переключатель в Профиле). */')
        out.append('@media (prefers-color-scheme: dark) {')
        out.append('  :root:not([data-theme="light"]) {')
        out.extend("  " + l for l in dark_lines)
        out.append("  }")
        out.append("}")
        out.append(':root[data-theme="dark"] {')
        out.extend(dark_lines)
        out.append("}")

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"written {OUT_PATH} ({len(out)} lines)")


if __name__ == "__main__":
    main()
