package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Standard UI semantics live in Material so stock components and custom Bitchat composables
// share one source of truth. LocalBitchatPalette below only supplies app-specific extra colors.
// Perto: roxo como cor principal e laranja como destaque, nos modos escuro e claro.
internal val DarkBitchatColorScheme = darkColorScheme(
    primary = Color(0xFFAFA9EC),
    onPrimary = Color(0xFF26215C),
    primaryContainer = Color(0xFF3C3489),
    onPrimaryContainer = Color(0xFFEEEDFE),
    secondary = Color(0xFFEF9F27),
    onSecondary = Color(0xFF412402),
    secondaryContainer = Color(0xFF633806),
    onSecondaryContainer = Color(0xFFFAEEDA),
    tertiary = DarkBitchatPalette.accentOrange,
    onTertiary = Color(0xFF412402),
    background = Color(0xFF17143A),
    onBackground = Color(0xFFEEEDFE),
    surface = Color(0xFF1F1B4A),
    onSurface = Color(0xFFEEEDFE),
    surfaceVariant = Color(0xFF2A2560),
    onSurfaceVariant = Color(0xFFAFA9EC),
    outline = Color(0xFF3C3489),
    outlineVariant = Color(0xFF2A2560),
    error = Color(0xFFF09595),
    onError = Color(0xFF501313)
)

internal val LightBitchatColorScheme = lightColorScheme(
    primary = Color(0xFF534AB7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCECBF6),
    onPrimaryContainer = Color(0xFF26215C),
    secondary = Color(0xFFBA7517),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFAEEDA),
    onSecondaryContainer = Color(0xFF412402),
    tertiary = LightBitchatPalette.accentOrange,
    onTertiary = Color(0xFF412402),
    background = Color(0xFFF7F6FE),
    onBackground = Color(0xFF26215C),
    surface = Color(0xFFEEEDFE),
    onSurface = Color(0xFF26215C),
    surfaceVariant = Color(0xFFE4E2FA),
    onSurfaceVariant = Color(0xFF534AB7),
    outline = Color(0xFFCECBF6),
    outlineVariant = Color(0xFFDEDCF8),
    error = Color(0xFFA32D2D),
    onError = Color.White
)

/**
 * Perto: monta o esquema de cores a partir do tema escolhido. O roxo é o padrão; as outras
 * opções só trocam a cor principal e os fundos levemente tingidos, o laranja continua de destaque.
 */
internal fun pertoColorScheme(
    accent: com.bitchat.android.ui.PertoAccent,
    dark: Boolean,
): androidx.compose.material3.ColorScheme {
    val base = if (dark) DarkBitchatColorScheme else LightBitchatColorScheme
    return when (accent) {
        com.bitchat.android.ui.PertoAccent.Roxo -> base
        com.bitchat.android.ui.PertoAccent.VerdeAgua -> if (dark) base.copy(
            primary = Color(0xFF5DCAA5), onPrimary = Color(0xFF04342C),
            primaryContainer = Color(0xFF085041), onPrimaryContainer = Color(0xFFE1F5EE),
            background = Color(0xFF071F1A), surface = Color(0xFF0C2A23),
            surfaceVariant = Color(0xFF12362D), onSurfaceVariant = Color(0xFF9FE1CB),
            outline = Color(0xFF0F6E56), outlineVariant = Color(0xFF12362D),
            onBackground = Color(0xFFE1F5EE), onSurface = Color(0xFFE1F5EE),
        ) else base.copy(
            primary = Color(0xFF0F6E56), onPrimary = Color.White,
            primaryContainer = Color(0xFF9FE1CB), onPrimaryContainer = Color(0xFF04342C),
            background = Color(0xFFF4FBF8), surface = Color(0xFFE1F5EE),
            surfaceVariant = Color(0xFFD3EFE4), onSurfaceVariant = Color(0xFF0F6E56),
            outline = Color(0xFF9FE1CB), outlineVariant = Color(0xFFC4E9DB),
            onBackground = Color(0xFF04342C), onSurface = Color(0xFF04342C),
        )
        com.bitchat.android.ui.PertoAccent.Coral -> if (dark) base.copy(
            primary = Color(0xFFF0997B), onPrimary = Color(0xFF4A1B0C),
            primaryContainer = Color(0xFF712B13), onPrimaryContainer = Color(0xFFFAECE7),
            background = Color(0xFF221009), surface = Color(0xFF2E160D),
            surfaceVariant = Color(0xFF3A1C11), onSurfaceVariant = Color(0xFFF5C4B3),
            outline = Color(0xFF993C1D), outlineVariant = Color(0xFF3A1C11),
            onBackground = Color(0xFFFAECE7), onSurface = Color(0xFFFAECE7),
        ) else base.copy(
            primary = Color(0xFF993C1D), onPrimary = Color.White,
            primaryContainer = Color(0xFFF5C4B3), onPrimaryContainer = Color(0xFF4A1B0C),
            background = Color(0xFFFFF8F5), surface = Color(0xFFFAECE7),
            surfaceVariant = Color(0xFFF6DED4), onSurfaceVariant = Color(0xFF993C1D),
            outline = Color(0xFFF5C4B3), outlineVariant = Color(0xFFF2D3C7),
            onBackground = Color(0xFF4A1B0C), onSurface = Color(0xFF4A1B0C),
        )
    }
}

@Composable
fun BitchatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    remember(context) { com.bitchat.android.ui.PertoPrefs.init(context); true }
    val accent by com.bitchat.android.ui.PertoPrefs.accent.collectAsState()
    val colorScheme = pertoColorScheme(accent, shouldUseDark)
    val palette = if (shouldUseDark) DarkBitchatPalette else LightBitchatPalette

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(LocalBitchatPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
