package com.muvusoft.agentfarm.core.theme

/**
 * Agent Farm's look, once: the colours of its icon (the green hub, the navy ground, the blue-grey nodes)
 * as a light and a dark scheme, the type sizes and the spacing. The Compose theme reads these, and the
 * farm's web pages are given the same values so a page opened on the phone looks like part of the app.
 * res/values{,-night}/colors.xml repeat only the background (the window behind the first frame);
 * test/test_theme_tokens.js holds them to this file.
 */
class Palette(
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val background: Long, val onBackground: Long,
    val surfaceVariant: Long, val onSurfaceVariant: Long,
    val containerLowest: Long, val containerLow: Long, val container: Long, val containerHigh: Long, val containerHighest: Long,
    val outline: Long, val outlineVariant: Long,
    val error: Long, val onError: Long, val errorContainer: Long, val onErrorContainer: Long,
    /** A turn is running: the hub's green. */
    val running: Long,
    /** Something waits for the user: a question, a permission, an approval. */
    val waiting: Long, val waitingContainer: Long, val onWaitingContainer: Long,
)

object Tokens {
    val LIGHT = Palette(
        primary = 0xFF2E7D46, onPrimary = 0xFFFFFFFF, primaryContainer = 0xFFC6EDD0, onPrimaryContainer = 0xFF0A2814,
        secondary = 0xFF4B5D86, onSecondary = 0xFFFFFFFF, secondaryContainer = 0xFFDDE3F4, onSecondaryContainer = 0xFF142039,
        background = 0xFFF7F8FB, onBackground = 0xFF191C22,
        surfaceVariant = 0xFFE2E5EE, onSurfaceVariant = 0xFF454955,
        containerLowest = 0xFFFFFFFF, containerLow = 0xFFF1F3F8, container = 0xFFECEEF4, containerHigh = 0xFFE6E9F0, containerHighest = 0xFFE0E3EB,
        outline = 0xFF757A87, outlineVariant = 0xFFC5C9D4,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, errorContainer = 0xFFF9DEDC, onErrorContainer = 0xFF410E0B,
        running = 0xFF2E7D46,
        waiting = 0xFF9A5B00, waitingContainer = 0xFFFFE8C7, onWaitingContainer = 0xFF2E1A00,
    )

    val DARK = Palette(
        primary = 0xFF6FD58A, onPrimary = 0xFF003918, primaryContainer = 0xFF1D5631, onPrimaryContainer = 0xFFC6EDD0,
        secondary = 0xFFADBDE6, onSecondary = 0xFF1A2A4A, secondaryContainer = 0xFF33436A, onSecondaryContainer = 0xFFDDE3F4,
        background = 0xFF12161F, onBackground = 0xFFE2E4EB,
        surfaceVariant = 0xFF2A3140, onSurfaceVariant = 0xFFC3C7D3,
        containerLowest = 0xFF0D1118, containerLow = 0xFF181D27, container = 0xFF1C222D, containerHigh = 0xFF242B37, containerHighest = 0xFF2E3542,
        outline = 0xFF8D92A0, outlineVariant = 0xFF3E4554,
        error = 0xFFF2B8B5, onError = 0xFF601410, errorContainer = 0xFF8C1D18, onErrorContainer = 0xFFF9DEDC,
        running = 0xFF6FD58A,
        waiting = 0xFFFFB95C, waitingContainer = 0xFF4A3200, onWaitingContainer = 0xFFFFE8C7,
    )

    /** Type sizes in sp. Body text is never under 14; the smallest meta line is 13. */
    const val TITLE_SP = 18
    const val BODY_LARGE_SP = 16
    const val BODY_SP = 14
    const val META_SP = 13
    const val LABEL_SP = 12
    const val CODE_SP = 13

    /** Spacing in dp. A touch target is never under 48. */
    const val GAP_DP = 8
    const val ROW_PAD_DP = 12
    const val EDGE_DP = 16
    const val CARD_RADIUS_DP = 14
    const val MIN_TOUCH_DP = 48
}
