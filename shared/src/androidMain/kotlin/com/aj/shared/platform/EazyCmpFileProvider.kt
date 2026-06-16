package com.aj.shared.platform

import androidx.core.content.FileProvider

/**
 * Dedicated FileProvider so manifest merger does not clash with the host app's FileProvider.
 */
class EazyCmpFileProvider : FileProvider()
