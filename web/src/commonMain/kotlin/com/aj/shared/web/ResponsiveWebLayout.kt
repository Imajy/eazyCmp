package com.aj.shared.web

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Web Breakpoints for responsive web apps and websites.
 */
enum class WebBreakpoint {
    MOBILE,       // < 640dp
    TABLET,       // 640dp - 1024dp
    DESKTOP,      // 1024dp - 1440dp
    ULTRAWIDE     // > 1440dp
}

fun getWebBreakpoint(width: Dp): WebBreakpoint {
    return when {
        width < 640.dp -> WebBreakpoint.MOBILE
        width < 1024.dp -> WebBreakpoint.TABLET
        width < 1440.dp -> WebBreakpoint.DESKTOP
        else -> WebBreakpoint.ULTRAWIDE
    }
}

@Composable
fun ResponsiveWebContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(breakpoint: WebBreakpoint) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val breakpoint = getWebBreakpoint(maxWidth)
        content(breakpoint)
    }
}
