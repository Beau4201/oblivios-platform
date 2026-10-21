// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.passwords.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The roles of the framework palette of the reference page (docs/design/password-manager-2026-10-20.html), Dark and
 * Light. Dynamic colour is off on purpose. The same values are in res/values(-night)/colors.xml for the window.
 */
@Immutable
class PwColors(
    val dark: Boolean,
    val bg: Color, val row: Color, val bar: Color, val highest: Color,
    val on: Color, val onVariant: Color, val outline: Color, val outlineVariant: Color,
    val primary: Color, val onPrimary: Color, val secondary: Color, val onSecondary: Color,
    val secondaryContainer: Color, val onSecondaryContainer: Color,
    val error: Color, val inverse: Color, val onInverse: Color,
)

private val DarkPw = PwColors(
    true, Color(0xFF17191F), Color(0xFF292C34), Color(0xFF23262E), Color(0xFF1D1F26),
    Color(0xFFE4E5F0), Color(0xFFA9AAB5), Color(0xFF73757F), Color(0xFF454850),
    Color(0xFFB8C6EE), Color(0xFF323F60), Color(0xFFC0C6DC), Color(0xFF394052),
    Color(0xFF353B4D), Color(0xFFB8BFD5), Color(0xFFF2B8B5), Color(0xFFE4E5F0), Color(0xFF2F3036),
)

private val LightPw = PwColors(
    false, Color(0xFFEDEDF6), Color(0xFFFAF8FE), Color(0xFFE1E2ED), Color(0xFFE7E7F1),
    Color(0xFF30323A), Color(0xFF5D5F68), Color(0xFF787A84), Color(0xFFB0B1BC),
    Color(0xFF4C5E8B), Color(0xFFF9F8FF), Color(0xFF585F72), Color(0xFFF9F8FF),
    Color(0xFFDCE2F9), Color(0xFF4B5164), Color(0xFFB3261E), Color(0xFF2F3036), Color(0xFFF1F0F7),
)

val LocalPw = staticCompositionLocalOf { DarkPw }

/** The type roles of the page: 36/44 large title, 22/28 bar title, 16/24 list and body, 14/20 supporting, 12/16 label. */
private val PwTypography = Typography(
    headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PasswordsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkPw else LightPw
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.secondary, onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSecondaryContainer,
            background = c.bg, onBackground = c.on, surface = c.bg, onSurface = c.on, onSurfaceVariant = c.onVariant,
            outline = c.outline, outlineVariant = c.outlineVariant, error = c.error,
            inverseSurface = c.inverse, inverseOnSurface = c.onInverse,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary, secondary = c.secondary, onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSecondaryContainer,
            background = c.bg, onBackground = c.on, surface = c.bg, onSurface = c.on, onSurfaceVariant = c.onVariant,
            outline = c.outline, outlineVariant = c.outlineVariant, error = c.error,
            inverseSurface = c.inverse, inverseOnSurface = c.onInverse,
        )
    }
    CompositionLocalProvider(LocalPw provides c) {
        MaterialTheme(colorScheme = scheme, typography = PwTypography, content = content)
    }
}

val pw: PwColors
    @Composable get() = LocalPw.current
