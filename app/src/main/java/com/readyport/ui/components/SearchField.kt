package com.readyport.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 공용 검색 칸 (관광지 SPEC_v5 §0 — 게시판·영상의 검색 칸은 아직 각자 것을 쓴다. 다음 정리 때 이 부품으로 합친다).
 * 돋보기 + 글자 지우기(48dp). IME '검색'을 누르면 키보드를 내린다(결과는 입력하는 동안 이미 보인다).
 * 높이 56dp(쉬운 모드 64dp) 이상.
 */
@Composable
fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    label: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val dimens = LocalDimens.current
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        label = { KoText(label) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onChange("") }, modifier = Modifier.minTouchSize()) {
                    Icon(Icons.Outlined.Clear, contentDescription = clearLabel)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tokens.Surface,
            unfocusedContainerColor = Tokens.Surface,
            unfocusedBorderColor = Tokens.LineStrong,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.buttonHeight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { onFocusChange(it.isFocused) },
    )
}
