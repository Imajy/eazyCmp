package com.aj.shared.ui


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aj.shared.theme.blackColor
import com.aj.shared.theme.errorBrush
import com.aj.shared.theme.successBrush
import com.aj.shared.theme.warningBrush
import com.aj.shared.theme.whiteColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

object AppSnackbarManager {
    private lateinit var hostState: SnackbarHostState
    private var onSnackbarDataChange: ((AppSnackbar?) -> Unit)? = null
    private var autoDismissJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeMessage: String? = null
    private var activeType: SnackbarType? = null

    var isSnackbarVisible by mutableStateOf(false)
        internal set

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
        if (onSnackbarDataChange == null) return

        val msg = message ?: "Something went wrong"

        if (activeMessage == msg && activeType == type) {
            autoDismissJob?.cancel()
            autoDismissJob = scope.launch {
                kotlinx.coroutines.delay(autoDismissMillis)
                dismiss()
            }
            return
        }

        dismiss()

        activeMessage = msg
        activeType = type

        val data = AppSnackbar(
            message = msg,
            type = type,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Short,
            onAction = onAction
        )

        isSnackbarVisible = true
        onSnackbarDataChange?.invoke(data)

        autoDismissJob = scope.launch {
            kotlinx.coroutines.delay(autoDismissMillis)
            dismiss()
        }
    }

    fun dismiss() {
        autoDismissJob?.cancel()
        activeMessage = null
        activeType = null
        isSnackbarVisible = false
        onSnackbarDataChange?.invoke(null)
    }
}

val screenGradientColor = Brush.verticalGradient(
    colors = listOf(Color(0xFFE3F2FD), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
)

@Composable
fun SnackBarBoxApp(brush: Brush = screenGradientColor, content: @Composable () -> Unit) {

    val snackbarHostState = remember { SnackbarHostState() }
    var showSnackbar by remember { mutableStateOf(false) }
    var activeSnackbarData by remember { mutableStateOf<AppSnackbar?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    AppSnackbarManager.init(
        hostState = snackbarHostState,
        onSnackbarDataChange = { snackbar ->
            if (snackbar != null) {
                activeSnackbarData = snackbar
                showSnackbar = true
            } else {
                showSnackbar = false
            }
        }
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

        AnimatedVisibility(
            visible = showSnackbar,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(animationSpec = tween(durationMillis = 200)),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .zIndex(Float.MAX_VALUE)
        ) {
            activeSnackbarData?.let { snackbar ->
                val swipeOffsetX = remember(snackbar) { Animatable(0f) }
                val swipeOffsetY = remember(snackbar) { Animatable(0f) }
                val coroutineScope = rememberCoroutineScope()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(swipeOffsetX.value.roundToInt(), swipeOffsetY.value.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    if (swipeOffsetY.value < -10f) {
                                        coroutineScope.launch {
                                            swipeOffsetY.animateTo(
                                                targetValue = -300f,
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                            AppSnackbarManager.dismiss()
                                        }
                                    } else if (abs(swipeOffsetX.value) > size.width / 6) {
                                        coroutineScope.launch {
                                            val target = if (swipeOffsetX.value > 0) size.width.toFloat() else -size.width.toFloat()
                                            swipeOffsetX.animateTo(
                                                targetValue = target,
                                                animationSpec = tween(durationMillis = 200)
                                            )
                                            AppSnackbarManager.dismiss()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            launch {
                                                swipeOffsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                            launch {
                                                swipeOffsetY.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x)
                                        val newY = swipeOffsetY.value + dragAmount.y
                                        if (newY < 0f) {
                                            swipeOffsetY.snapTo(newY)
                                        } else {
                                            // Rubber band feel when swiping down
                                            swipeOffsetY.snapTo(newY * 0.5f)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    CustomTopSnackbar(
                        data = snackbar,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
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