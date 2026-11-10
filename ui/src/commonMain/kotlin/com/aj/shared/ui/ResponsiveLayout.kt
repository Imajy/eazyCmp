package com.aj.shared.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Window Size Classes matching Material 3 specifications.
 */
enum class WindowSizeClass {
    COMPACT,   // Phone / narrow window (< 600dp)
    MEDIUM,    // Tablet / foldables / split-screen (600dp - 840dp)
    EXPANDED   // Desktop / large screens (> 840dp)
}

/**
 * Resolves the current window width into a WindowSizeClass.
 */
fun getWindowSizeClass(width: Dp): WindowSizeClass {
    return when {
        width < 600.dp -> WindowSizeClass.COMPACT
        width < 840.dp -> WindowSizeClass.MEDIUM
        else -> WindowSizeClass.EXPANDED
    }
}

/**
 * Responsive layout container that passes the current WindowSizeClass to its composable content.
 */
@Composable
fun ResponsiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(windowSizeClass: WindowSizeClass) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val sizeClass = getWindowSizeClass(maxWidth)
        content(sizeClass)
    }
}
