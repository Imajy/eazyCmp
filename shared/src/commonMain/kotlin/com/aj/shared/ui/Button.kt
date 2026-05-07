package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aj.shared.theme.whiteColor
import kotlinx.coroutines.delay


@Composable
fun CommonButton(
    label: String = "View All",
    painter: Painter? = null,
    icon: ImageVector? = null,
    color: Color = Color(0xFF2196F3),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(
        color = whiteColor,
        textAlign = TextAlign.Center
    ),
    enabled: Boolean = true,
    radius: Int = 8,
    isIconStart: Boolean = true
) {

    var isClicked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit){
        if(!isClicked){
            delay(1000)
            isClicked = true
        }
    }
    Row(
        modifier = modifier
            .background(color, RoundedCornerShape(radius.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if(isClicked){onClick()} }
            )
            .padding(padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isIconStart) {
            painter?.let { image ->
                Icon(
                    painter = image,
                    contentDescription = null,
                    tint = whiteColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            icon?.let { image ->
                Icon(
                    imageVector = image,
                    contentDescription = null,
                    tint = whiteColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
        }
        Text(
            text = label,
            style = style
        )
        if (!isIconStart) {
            painter?.let { image ->
                Icon(
                    painter = image,
                    contentDescription = null,
                    tint = whiteColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            icon?.let { image ->
                Icon(
                    imageVector = image,
                    contentDescription = null,
                    tint = whiteColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
        }
    }
}