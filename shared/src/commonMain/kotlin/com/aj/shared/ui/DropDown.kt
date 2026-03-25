package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun <T> CommonDropDown(
    label: String = "",
    placeholder: String = "",
    items: List<T> = emptyList(),
    selectedItem: T? = null,
    selectedItems: List<T> = emptyList(),
    isMultiSelect: Boolean = false,
    itemLabel: ((T) -> String)? = null,
    onItemSelected: (T) -> Unit = {},
    onItemsSelected: (List<T>) -> Unit = {},
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
    var tempSelectedItems by remember { mutableStateOf(emptySet<T>()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(selectedItem, selectedItems) {
        displayText = if (isMultiSelect)
            selectedItems.joinToString(", ") { item -> itemLabel?.invoke(item) ?: item.toString() }
        else
            selectedItem?.let { item -> itemLabel?.invoke(item) ?: item.toString() } ?: ""
    }
    LaunchedEffect(showDialog) {
        if (showDialog) {
            tempSelectedItems = selectedItems.toMutableSet()
        }
    }

    val showSearch = items.size > 10

    val filteredItems by remember(searchText, items) {
        derivedStateOf {
            val filtered = if (searchText.isBlank()) items
            else
                items.filter { item ->
                    val label = itemLabel?.invoke(item) ?: item.toString() // Logic yahan bhi same
                    label.contains(searchText, ignoreCase = true)
                }
            if (showFullList) filtered
            else filtered.take(50)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
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
                borderColor = if (error != null) rejectedRedColor
                else borderBGColor,
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
                        if (items.isNotEmpty())
                            showDialog = true
                        onClick()
                    }
            )
        }

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
                        .background(whiteColor, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = label.ifBlank { "Select Item" },
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (showSearch) {
                            Spacer(Modifier.height(8.dp))

                            OutLinedSimpleTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholderText = "Search...",
                                modifier = Modifier.fillMaxWidth(),
                                radius = 6
                            )
                        }

                        Spacer(Modifier.height(8.dp))

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
                                    val isSelected = if (isMultiSelect) tempSelectedItems.contains(item)
                                    else item == selectedItem

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                else Color.Transparent
                                            )
                                            .clickable {

                                                if (isMultiSelect) {
                                                    tempSelectedItems =
                                                        if (tempSelectedItems.contains(item)) {
                                                            tempSelectedItems - item // Remove
                                                        } else {
                                                            tempSelectedItems + item // Add
                                                        }
                                                } else {
                                                    onItemSelected(item)
                                                    displayText = itemLabel?.invoke(item) ?: item.toString()

                                                    showDialog = false
                                                }
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically

                                    ) {
                                        if (isMultiSelect) {
                                            CustomCheckbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    tempSelectedItems = if (it) {
                                                        tempSelectedItems + item // Add
                                                    } else {
                                                        tempSelectedItems - item // Remove
                                                    }
                                                }
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }

                                        Text(
                                            text = itemLabel?.invoke(item) ?: item.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black
                                        )
                                    }

                                    if (index < filteredItems.lastIndex)
                                        HorizontalDivider()
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if ((isMultiSelect && tempSelectedItems.isNotEmpty()) || (!isMultiSelect && selectedItem != null)) {
                                Text(
                                    text = "Reset",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                displayText = ""
                                                if (isMultiSelect) {
                                                    tempSelectedItems - tempSelectedItems
                                                    onItemsSelected(emptyList())

                                                }
                                                showDialog = false
                                            }
                                            .padding(8.dp)
                                )
                            } else { Spacer(Modifier) }

                            Row {
                                if (isMultiSelect) {
                                    Text(
                                        text = "Done",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                val result = tempSelectedItems.toList()

                                                onItemsSelected(result)

                                                displayText = result.joinToString(", ") { item ->
                                                    itemLabel?.invoke(item) ?: item.toString()
                                                }
                                                showDialog = false
                                            }
                                            .padding(8.dp)
                                    )
                                }

                                Text(
                                    text = "Cancel",
                                    color = Color.Gray,
                                    modifier = Modifier
                                        .clickable { showDialog = false }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = rejectedRedColor,
                fontSize = 12.sp
            )
        }
    }
}