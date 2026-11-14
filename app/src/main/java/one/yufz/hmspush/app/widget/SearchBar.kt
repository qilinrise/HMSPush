package one.yufz.hmspush.app.widget

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@Composable
fun SearchBar(
    searchText: String = "",
    placeholderText: String = "",
    onNavigateBack: () -> Unit = {},
    onSearchTextChanged: (String) -> Unit = {},
) {
    var showClearButton by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val textState = remember { mutableStateOf(TextFieldValue(searchText, selection = TextRange(searchText.length))) }

    fun clear() {
        textState.value = TextFieldValue()
        onSearchTextChanged("")
    }

    fun back() {
        clear()
        onNavigateBack()
    }
    BackHandler {
        back()
    }
    Row(
        modifier = Modifier.height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = ::back) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back"
            )
        }
        TextField(
            value = textState.value,
            onValueChange = {
                textState.value = it
                onSearchTextChanged(it.text)
            },
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .padding(end = 12.dp)
                .onFocusChanged { focusState -> showClearButton = (focusState.isFocused) }
                .focusRequester(focusRequester),
            placeholder = { Text(text = placeholderText) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(percent = 50),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            trailingIcon = {
                AnimatedVisibility(
                    visible = showClearButton && textState.value.text.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = { clear() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "clear"
                        )
                    }
                }
            },
            maxLines = 1,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
            }),
        )
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@ExperimentalAnimationApi
@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun preView() {
    SearchBar("1234") {

    }
}