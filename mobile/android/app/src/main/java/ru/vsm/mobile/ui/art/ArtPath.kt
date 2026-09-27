package ru.vsm.mobile.ui.art

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Разбирает строку SVG/Android path-data (как в исходных React/SVG-компонентах сайта) в
 * Compose [Path]. Позволяет переносить геометрию иллюстраций дословно, без ручного повтора
 * moveTo/cubicTo для каждой кривой.
 */
fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
