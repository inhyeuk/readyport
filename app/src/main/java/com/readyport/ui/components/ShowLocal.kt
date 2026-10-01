package com.readyport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.Tokens

// ======================= 현지인에게 보여 주기 (DESIGN_SPEC 3.2, 6-18·19·20 — D 묶음에서 옮김) =======================

/**
 * 현지어(태국어 등)를 표시 역할이 아닌 크기로 보일 때: 행간 1.5배 + 줄 높이 가운데·자르지 않음 —
 * 위아래로 쌓이는 부호(ที่นี่)가 겹치거나 잘리지 않게 (DESIGN_SPEC 3.2). 크기는 [base] 역할 그대로(고정 sp 없음).
 * 앱 글자 스타일에 고정한 한국어(ReadyPortLineBreak.Korean)는 뺀다 — 현지어 글꼴·줄바꿈은 기기 언어를 따른다(localLarge·localMedium과 같음).
 */
fun localText(base: TextStyle): TextStyle = base.copy(
    lineHeight = base.fontSize * 1.5f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    localeList = null,
)

/**
 * 현지인에게 보여 주는 전체 화면 틀 (18·19·20 공통): 글은 스크롤 영역에, 닫기 버튼은 아래에 고정 —
 * 여러 줄 태국어가 localLarge(200%면 약 112sp)로 화면을 넘겨도 닫기가 항상 보인다.
 */
@Composable
fun ShowLocalBody(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().background(Tokens.Surface)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
        Box(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)) {
            PrimaryButton(stringResource(R.string.help_close), onClick = onClose, icon = Icons.Outlined.Close)
        }
    }
}
