package com.aj.shared.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aj.shared.theme.grayColor
import com.aj.shared.theme.transparentColor
import com.aj.shared.theme.whiteColor


@Composable
fun GenericTabs(
    selected : String,
    list : List<String>,
    selectedBrush : Brush =  Brush.horizontalGradient(
        listOf(
            Color(0xFF1A5493),
            Color(0xFF00AFEF)
        )
    ),
    unselectedBrush : Brush = Brush.horizontalGradient(
        listOf(transparentColor, transparentColor)
    ),
    selectedTextColor : Color = whiteColor,
    unSelectedTextColor : Color = grayColor,
    onTabSelected: (String) -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(50.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.background(whiteColor)
                .padding(4.dp)
        ) {
            list.forEach { type ->

                val isSelected = selected == type
                val textColor = if (isSelected) selectedTextColor else unSelectedTextColor

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (isSelected) {
                                selectedBrush
                            } else {
                                unselectedBrush
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(type)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "type",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}