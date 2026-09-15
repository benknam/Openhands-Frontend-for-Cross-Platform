package com.openhands.remote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OpenHands color system ported from upstream 1.18.0 Tailwind design tokens.
 * See tailwind.config.js in /home/avenue/IDE/Openhands/ORGINAL/OpenHands-1.18.0/src/tailwind.config.js
 */
object OpenHandsColors {
    // ============================================================
    // Surface Colors (upstream: surface.*)
    // ============================================================
    val surface = Color(0xFF050505)
    val surfaceCard = Color(0xFF0A0A0A)
    val surfaceElevated = Color(0xFF1A1A1A)
    val surfaceOutline = Color(0xFF171717)
    val surfaceBackground = Color(0xFF262626)
    val surfaceDivider = Color(0xFF525252)
    val surfaceButton = Color(0xFF737373)
    val surfaceText = Color(0xFFA3A3A3)

    // ============================================================
    // Border Colors (upstream: border.*)
    // ============================================================
    val borderDefault = Color(0xFF242424)
    val borderHover = Color(0xFF3A3A3A)

    // ============================================================
    // Content Colors (upstream: content.*)
    // ============================================================
    val contentDefault = Color(0xFFFafafa)
    val contentMuted = Color(0xFF8c8c8c)
    val contentIcon = Color(0xFF3a3a3a)

    // ============================================================
    // Status Colors (upstream: status.*)
    // ============================================================
    val successBg = Color(0x1A10B981)      // rgba(16, 185, 129, 0.1)
    val successBorder = Color(0x6610B981)  // rgba(16, 185, 129, 0.4)
    val successText = Color(0xFF6ee7b7)
    val successBadgeBg = Color(0x2610B981) // rgba(16, 185, 129, 0.15)
    val failBg = Color(0x1AF43F5E)         // rgba(244, 63, 94, 0.1)
    val failBorder = Color(0x66F43F5E)     // rgba(244, 63, 94, 0.4)
    val failText = Color(0xFFfda4af)
    val failSolid = Color(0xFFdc2626)
    val failSolidHover = Color(0xFFb91c1c)

    // ============================================================
    // Toggle Colors (upstream: toggle.*)
    // ============================================================
    val toggleActive = Color(0xFF34d399)
    val toggleActiveBg = Color(0x3334D399)  // rgba(52, 211, 153, 0.2)
    val toggleActiveBorder = Color(0x8034D399) // rgba(52, 211, 153, 0.5)
    val toggleInactive = Color(0xFF242424)
    val toggleInactiveKnob = Color(0xFF8c8c8c)
    val toggleInactiveBorder = Color(0xFF3a3a3a)

    // ============================================================
    // Modal Colors (upstream: modal.*)
    // ============================================================
    val modalBackground = Color(0xFF171717)
    val modalInput = Color(0xFF27272A)
    val modalPrimary = Color(0xFFF3CE49)
    val modalSecondary = Color(0xFF737373)
    val modalMuted = Color(0xFFA3A3A3)

    // ============================================================
    // Misc (upstream: muted-overlay, pill-bg)
    // ============================================================
    val mutedOverlay = Color(0x66050505)  // rgba(5, 5, 5, 0.4)
    val pillBg = Color(0x4D1F1F1F)        // rgba(31, 31, 31, 0.3)

    // ============================================================
    // Legacy compatibility colors (existing code references these)
    // ============================================================
    val lightPrimary = Color(0xFF24C878)
    val lightOnPrimary = Color(0xFF07130D)
    val lightBackground = Color(0xFF0B0F14)
    val lightSurface = Color(0xFF151A22)
    val lightSurfaceVariant = Color(0xFF202630)
    val lightOnSurface = Color(0xFFF2F4F7)
    val lightOutline = Color(0xFF3A4452)

    val darkPrimary = Color(0xFF24C878)
    val darkOnPrimary = Color(0xFF07130D)
    val darkBackground = Color(0xFF0B0F14)
    val darkSurface = Color(0xFF151A22)
    val darkSurfaceVariant = Color(0xFF202630)
    val darkOnSurface = Color(0xFFF2F4F7)
    val darkOutline = Color(0xFF3A4452)

    // Legacy aliases for backward compatibility with existing UI code
    val background = Color(0xFF0B0F14)
    val surfaceVariant = Color(0xFF202630)
    val surfaceRaised = Color(0xFF202630)
    val surfaceHover = Color(0xFF29313D)
    val border = Color(0xFF343E4C)
    val text = Color(0xFFF2F4F7)
    val textSecondary = Color(0xFFAAB5C5)
    val textMuted = Color(0xFF7F8B9C)
    val textDisabled = Color(0xFF566171)
    val userMessage = Color(0xFF202630)
    val assistantMessage = Color(0xFF151A22)
    val toolCall = Color(0xFF202630)
    val toolResult = Color(0xFF1A2830)
    val success = Color(0xFF24C878)
    val warning = Color(0xFFF2C66D)
    val error = Color(0xFFFF7676)
    val disconnected = Color(0xFF7F8B9C)
}
