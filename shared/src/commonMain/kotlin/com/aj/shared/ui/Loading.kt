package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun CustomLoading() {

    Dialog(onDismissRequest = { }) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color.White, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {

            CustomImage(
                placeholder = Placeholder.LottieUrl("https://letterhead.ajmonic.com/loading.json"),
                model = null
            )
        }
    }
}

suspend fun loadJson(path: String): String? {

    return CustomImageResourceResolver
        .resolveBytes
        ?.invoke(path)
        ?.decodeToString()

}