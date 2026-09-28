package com.readyport.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.readyport.R
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.theme.LocalDimens

/** 첫 실행 질문 (PRD 3.2). '네'면 쉬운 모드를 켠다. 누가 쓸지 모르므로 이 화면은 항상 큰 글씨로 그린다. */
@Composable
fun FirstRunScreen(onAnswer: (firstTimeAbroad: Boolean) -> Unit) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.gap),
    ) {
        Spacer(Modifier.height(dimens.gap * 2))
        Text(
            text = stringResource(R.string.first_run_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.first_run_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(dimens.gap * 2))
        PrimaryButton(text = stringResource(R.string.first_run_yes), onClick = { onAnswer(true) })
        OutlinedButton(
            onClick = { onAnswer(false) },
            modifier = Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight),
        ) {
            Text(stringResource(R.string.first_run_no), style = MaterialTheme.typography.labelLarge)
        }
    }
}
