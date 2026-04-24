package com.aj.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aj.shared.extension.toTitleCase
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
    onItemSelected: (T?) -> Unit = {},
    onItemsSelected: (List<T>) -> Unit = {},
    onClick: () -> Unit = {},
    trailingIcon: ImageVector? = null,
    trailingImage: Painter? = null,
    trailingIconTint: Color = blackColor,
    error: String? = null,
    showFullList: Boolean = false,
    modifier: Modifier = Modifier,
    dropDownBackGround : Color = whiteColor
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
                value = displayText.replace("_", " "),
                label = label,
                onValueChange = {},
                placeholderText = placeholder,
                placeHolderColor = blackColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dropDownBackGround, RoundedCornerShape(6.dp)),
                trailingIcon = trailingIcon ?: DropdownArrowIcon,
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
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                showDialog = false
                            }
                        }
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
                                            text = (itemLabel?.invoke(item) ?: item.toString()).replace("_", " "),
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

                                                }else {
                                                    onItemSelected(null)
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
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
            )
        }
    }
}



val DropdownArrowIcon: ImageVector
    get() {
        if (_dropdownArrowIcon != null) {
            return _dropdownArrowIcon!!
        }
        _dropdownArrowIcon = ImageVector.Builder(
            name = "DropdownArrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {

            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(7f, 10f)
                lineTo(12f, 15f)
                lineTo(17f, 10f)
                close()
            }

        }.build()

        return _dropdownArrowIcon!!
    }

fun String.customToTitleCase(): String {
    return this.lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
private var _dropdownArrowIcon: ImageVector? = null