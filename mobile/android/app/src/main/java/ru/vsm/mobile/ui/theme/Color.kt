package ru.vsm.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// Светлая тема — палитра сайта (frontend/src/styles/theme.css): корпоративный синий/белый/серый,
// красный только для критичных состояний.
object VsmColorsLight {
    val backgroundCanvas = Color(0xFFF2F2F7)
    val backgroundSurface = Color(0xFFFFFFFF)
    val backgroundSurfaceRaised = Color(0xFFF7F7FA)
    val backgroundSurfaceSunken = Color(0xFFE9E9EF)
    val textPrimary = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF5E5E63)
    val textMuted = Color(0xFF8E8E93)
    val textOnAccent = Color(0xFFFFFFFF)
    val borderDefault = Color(0xFFE3E3E8)
    val borderStrong = Color(0xFFC7C7CC)
    val actionPrimary = Color(0xFF0A64D8)
    val actionPrimaryHover = Color(0xFF0B3D91)
    val actionSecondary = Color(0xFFE6F0FC)
    val actionDestructive = Color(0xFFD93025)
    val navy = Color(0xFF0B3D91)
    val loyaltyFillHigh = Color(0xFF1F9BD8)
    val loyaltyWarning = Color(0xFF5B6B82)
    val loyaltyCritical = Color(0xFFD93025)
    val safetyFillHigh = Color(0xFF0A64D8)
    val safetyWarning = Color(0xFF5B6B82)
    val safetyCritical = Color(0xFFD93025)
    val feedbackSuccess = Color(0xFF0A64D8)
    val feedbackWarning = Color(0xFF5B6B82)
    val feedbackDanger = Color(0xFFD93025)
}

// Тёмная тема — те же токены сайта под :root[data-theme="dark"].
object VsmColorsDark {
    val backgroundCanvas = Color(0xFF000000)
    val backgroundSurface = Color(0xFF1C1C1E)
    val backgroundSurfaceRaised = Color(0xFF2C2C2E)
    val backgroundSurfaceSunken = Color(0xFF3A3A3C)
    val textPrimary = Color(0xFFF5F5F7)
    val textSecondary = Color(0xFFAEAEB2)
    val textMuted = Color(0xFF8E8E93)
    val textOnAccent = Color(0xFFFFFFFF)
    val borderDefault = Color(0xFF38383A)
    val borderStrong = Color(0xFF48484A)
    val actionPrimary = Color(0xFF3E8BFF)
    val actionPrimaryHover = Color(0xFF4C8DF6)
    val actionSecondary = Color(0xFF0F2847)
    val actionDestructive = Color(0xFFFF5A4F)
    val navy = Color(0xFF4C8DF6)
    val loyaltyFillHigh = Color(0xFF5AC8FA)
    val loyaltyWarning = Color(0xFF98A4B8)
    val loyaltyCritical = Color(0xFFFF5A4F)
    val safetyFillHigh = Color(0xFF3E8BFF)
    val safetyWarning = Color(0xFF98A4B8)
    val safetyCritical = Color(0xFFFF5A4F)
    val feedbackSuccess = Color(0xFF4B8FF0)
    val feedbackWarning = Color(0xFF98A4B8)
    val feedbackDanger = Color(0xFFFF5A4F)
}
