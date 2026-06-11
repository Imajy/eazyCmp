package com.aj.shared.ui.kit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.aj.shared.theme.borderBGColor
import com.aj.shared.theme.grayColor
import com.aj.shared.ui.OutLinedSimpleTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    leadingIcon: ImageVector = Icons.Default.Search,
    onClear: (() -> Unit)? = null,
) {
    OutLinedSimpleTextField(
        modifier = modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        placeholderText = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = if (query.isNotEmpty()) Icons.Default.Clear else null,
        onTrailingClicked = {
            onQueryChange("")
            onClear?.invoke()
        },
        borderColor = borderBGColor,
        placeHolderColor = grayColor,
    )
}
