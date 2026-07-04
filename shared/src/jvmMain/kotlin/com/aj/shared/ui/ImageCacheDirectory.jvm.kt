package com.aj.shared.ui

import coil3.PlatformContext
import com.aj.shared.getCacheDir

internal actual fun imageCacheDirectory(context: PlatformContext): String = getCacheDir()
