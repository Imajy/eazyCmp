package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aj.shared.theme.blackColor
import com.aj.shared.theme.transparentColor
import com.aj.shared.theme.whiteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomScaffold(
    title: String = "",
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    action1Click: () -> Unit = {},
    action2Click: () -> Unit = {},
    action1Img: Any? = null,
    action2Img: Any? = null,
    backImg: Any? = null,
    isLoading: Boolean = false,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {

    LocalWindowInfo.current.containerSize.height.dp

    val density = LocalDensity.current

    val keyboardHeightPx = WindowInsets.ime.getBottom(density)

    val screenHeightPx = LocalWindowInfo.current.containerSize.height

    with(density) {

        (screenHeightPx - keyboardHeightPx).toDp()

    }


    Scaffold(
        contentWindowInsets = WindowInsets(0,0,0,0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        color = blackColor,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        CustomImage(
                            model = backImg,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onBackClick
                                )
                                .size(35.dp).padding(start = 4.dp)
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (action1Img != null) {
                            CustomImage(
                                model = action1Img,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = action1Click
                                    )
                                    .size(35.dp).padding(start = 4.dp)
                            )
                        }
                        if (action2Img != null) {
                            CustomImage(
                                model = action2Img,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = action2Click
                                    )
                                    .size(35.dp).padding(start = 4.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = transparentColor,
                    titleContentColor = whiteColor
                )
            )
        },
        bottomBar = {
            if (bottomBar != null) {
                bottomBar()
            }
        },
        floatingActionButton = {
            if (floatingActionButton != null) {
                floatingActionButton()
            }
        },
        containerColor = transparentColor,
        modifier = Modifier.background(screenGradientColor)
    ) { padding ->
        val imeBottom = WindowInsets.ime
            .asPaddingValues()
            .calculateBottomPadding()

        val bottomSpace = if (imeBottom > 0.dp) 0.dp
        else padding.calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(bottom = bottomSpace, start = padding.calculateRightPadding(LayoutDirection.Rtl), end = padding.calculateEndPadding(LayoutDirection.Rtl))
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    content(PaddingValues(0.dp))
                }
            }
            if (isLoading) {
                CustomLoading()
            }
        }
    }
}