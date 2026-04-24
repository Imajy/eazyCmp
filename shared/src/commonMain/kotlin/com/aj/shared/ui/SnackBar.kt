package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.aj.shared.theme.blackColor
import com.aj.shared.theme.errorBrush
import com.aj.shared.theme.successBrush
import com.aj.shared.theme.transparentColor
import com.aj.shared.theme.warningBrush
import com.aj.shared.theme.whiteColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AppSnackbarManager {
    private lateinit var hostState: SnackbarHostState
    private var onSnackbarDataChange: ((AppSnackbar?) -> Unit)? = null
    private var autoDismissJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    fun init(
        hostState: SnackbarHostState,
        onSnackbarDataChange: (AppSnackbar?) -> Unit
    ) {
        this.hostState = hostState
        this.onSnackbarDataChange = onSnackbarDataChange
    }

    fun show(
        message: String? = null,
        type: SnackbarType = SnackbarType.ERROR,
        actionLabel: String? = "OK",
        autoDismissMillis: Long = 4000,
        onAction: (() -> Unit)? = null
    ) {
        dismiss()

        val data = AppSnackbar(
            message = message ?: "Something went wrong",
            type = type,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short,
            onAction = onAction
        )

        onSnackbarDataChange?.invoke(data)

        scope.launch {
            autoDismissJob = launch {
                delay(autoDismissMillis)
                dismiss()
            }

            val result = hostState.showSnackbar(
                message = message ?: "Something went wrong",
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short,
                withDismissAction = false
            )

            if (result == SnackbarResult.ActionPerformed) {
                autoDismissJob?.cancel()
                onAction?.invoke()
                dismiss()
            }
        }
    }

    fun dismiss() {
        autoDismissJob?.cancel()
        if (::hostState.isInitialized) {
            hostState.currentSnackbarData?.dismiss()
            onSnackbarDataChange?.invoke(null)
        }
    }
}

@Composable
fun CustomTopSnackbar(
    data: AppSnackbar,
    modifier: Modifier = Modifier
) {
    val radius = 10
    val brush = when (data.type) {
        SnackbarType.SUCCESS -> successBrush
        SnackbarType.ERROR -> errorBrush
        SnackbarType.WARNING -> warningBrush
    }
    if (data.message.isNotBlank()) {
        Box(
            modifier = modifier
                .background(
                    color = whiteColor,
                    shape = RoundedCornerShape(radius.dp)
                )
                .beamBorder(brush = brush, radius = radius)
                .padding(horizontal = 10.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.message,
                    color = blackColor,
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f),
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

enum class SnackbarType {
    SUCCESS,
    ERROR,
    WARNING
}

data class AppSnackbar(
    val message: String,
    val type: SnackbarType = SnackbarType.SUCCESS,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val onAction: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null
)

val screenGradientColor = Brush.verticalGradient(
    colors = listOf(Color(0xFFE3F2FD), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
)

@Composable
fun SnackBarBoxApp(brush: Brush = screenGradientColor, content: @Composable () -> Unit) {

    val snackbarHostState = remember { SnackbarHostState() }
    var currentSnackbar by remember { mutableStateOf<AppSnackbar?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    AppSnackbarManager.init(
        hostState = snackbarHostState,
        onSnackbarDataChange = { currentSnackbar = it }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush)
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            },
    ) {
        content()
        currentSnackbar?.let { snackbar ->
//            Dialog(
//                onDismissRequest = {},
//                properties = DialogProperties(
//                    usePlatformDefaultWidth = false,
//                    dismissOnClickOutside = true,
//                    dismissOnBackPress = true,
//                ),
//            ) {
            Popup(
                alignment = Alignment.TopCenter,
                properties = PopupProperties(
                    focusable = false, // back press handle करेगा
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    CustomTopSnackbar(
                        data = snackbar,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}