package com.aj.shared.storage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.serialization.KSerializer

@Composable
fun <T> rememberFormDraft(
    formId: String,
    serializer: KSerializer<T>,
    initial: T,
    manager: FormDraftManager = com.aj.shared.internal.ServiceLocator.formDraftsOrDefault(),
    autoSave: Boolean = true,
): Pair<T, (T) -> Unit> {
    var state by remember(formId) {
        mutableStateOf(manager.load(formId, serializer) ?: initial)
    }

    val update: (T) -> Unit = { newValue ->
        state = newValue
        if (autoSave) manager.save(formId, newValue, serializer)
    }

    DisposableEffect(formId) {
        onDispose {
            if (autoSave) manager.save(formId, state, serializer)
        }
    }

    return state to update
}
