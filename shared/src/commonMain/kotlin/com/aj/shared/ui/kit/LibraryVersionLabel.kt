package com.aj.shared.ui.kit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.aj.shared.EazyCmp
import com.aj.shared.version.EazyCmpVersionCatalog

@Composable
fun EazyCmpVersionLabel(
    modifier: Modifier = Modifier,
    manifestUrl: String = EazyCmpVersionCatalog.DEFAULT_MANIFEST_URL,
    showLatestHint: Boolean = true,
) {
    var latest by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manifestUrl) {
        latest = EazyCmpVersionCatalog.fetchManifest(manifestUrl)?.latest
    }

    val text = when {
        !showLatestHint || latest == null || latest == EazyCmp.VERSION ->
            "EazyCmp ${EazyCmp.VERSION}"
        else ->
            "EazyCmp ${EazyCmp.VERSION} · latest $latest"
    }

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
