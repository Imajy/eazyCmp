package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aj.shared.theme.blackColor
import com.aj.shared.theme.borderBGColor
import com.aj.shared.theme.grayColor
import com.aj.shared.theme.rejectedRedColor
import com.aj.shared.theme.whiteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonDropDown(
    label: String = "",
    placeholder: String = "",
    items: List<String> = emptyList(),
    selectedItem: String = "",
    onItemSelected: (String) -> Unit = {},
    onClick: () -> Unit = {},
    trailingIcon: ImageVector? = null,
    trailingImage: Painter? = null,
    trailingIconTint: Color = blackColor,
    error: String? = null,
    showFullList: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var displayText by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Sync selected value
    LaunchedEffect(selectedItem) {
        displayText = selectedItem
    }

    val showSearch = items.size > 10

    val filteredItems by remember(searchText, items) {
        derivedStateOf {
            when {
                items.isEmpty() -> emptyList()

                !showSearch -> if (showFullList) items else items.take(30)

                searchText.isBlank() -> if (showFullList) items else items.take(30)

                else -> items.filter { it.contains(searchText, ignoreCase = true) }.take(50)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {

        // 🔹 FIELD
        Box(modifier = Modifier.fillMaxWidth()) {

            OutLinedSimpleTextField(
                value = displayText,
                label = label,
                onValueChange = {},
                placeholderText = placeholder,
                placeHolderColor = blackColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(whiteColor, RoundedCornerShape(6.dp)),
                trailingIcon = trailingIcon ?: Icons.Default.KeyboardArrowDown,
                trailingIconTine = trailingIconTint,
                trailingImage = trailingImage,
                borderColor = if (error != null) rejectedRedColor else borderBGColor,
                radius = 6,
                enabled = false
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        searchText = ""
                        if (items.isNotEmpty()) {
                            showDialog = true
                        }
                        onClick()
                    }
            )
        }

        // 🔴 DIALOG
        if (showDialog) {

            val isLargeList = filteredItems.size > 15

            Dialog(
                onDismissRequest = {
                    showDialog = false
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLargeList) Modifier.fillMaxHeight()
                            else Modifier.wrapContentHeight()
                        )
                        .background(Color.White, RoundedCornerShape(12.dp))
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {

                        // 🔹 HEADER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label.ifBlank { "Select Item" },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        // 🔍 SEARCH (Conditional)
                        if (showSearch) {
                            OutLinedSimpleTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholderText = "Search...",
                                modifier = Modifier.fillMaxWidth(),
                                radius = 6
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // 📋 LIST
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = isLargeList)
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState())
                        ) {

                            if (filteredItems.isEmpty()) {
                                Text(
                                    text = "No match found",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    color = grayColor,
                                    fontSize = 12.sp
                                )
                            } else {

                                filteredItems.forEachIndexed { index, item ->

                                    val isSelected = item == selectedItem

                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            Color.Black,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                else
                                                    Color.Transparent
                                            )
                                            .clickable {
                                                onItemSelected(item)
                                                displayText = item
                                                showDialog = false
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                            .padding(14.dp)
                                    )

                                    if (index < filteredItems.lastIndex) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }

                        // 🔻 FOOTER BUTTONS
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // RESET
                            if (selectedItem.isNotBlank()) {
                                Text(
                                    text = "Reset",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .clickable {
                                            onItemSelected("")
                                            displayText = ""
                                            showDialog = false
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                        .padding(8.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier)
                            }

                            // CANCEL
                            Text(
                                text = "Cancel",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clickable {
                                        showDialog = false
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 🔻 ERROR
        if (error != null) {
            Text(
                text = error,
                color = rejectedRedColor,
                fontSize = 12.sp
            )
        }
    }
}