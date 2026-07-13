package com.aj.shared.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.aj.shared.EazyCmp
import com.aj.shared.display.DisplaySettingsManager
import com.aj.shared.display.EazyCmpDisplayHost

@Composable
fun EazyCmpProviders(
    themeManager: ThemeManager = EazyCmp.theme,
    displayManager: DisplaySettingsManager = EazyCmp.display,
    colors: EazyColors = EazyColors(),
    typography: EazyTypography = EazyTypography(),
    materialTypography: Typography? = null,
    content: @Composable () -> Unit,
) {
    EazyCmpDisplayHost(manager = displayManager) {
        CompositionLocalProvider(
            LocalEazyColors provides colors,
            LocalEazyTypography provides typography,
        ) {
            EazyCmpTheme(
                themeManager = themeManager,
                colors = colors,
                typography = typography,
                materialTypography = materialTypography,
                content = content,
            )
        }
    }
}
