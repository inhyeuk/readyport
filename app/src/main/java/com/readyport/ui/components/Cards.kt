package com.readyport.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

enum class CardTone(val container: Color, val content: Color, val border: Color?) {
    Neutral(Tokens.Surface, Tokens.Ink, Tokens.LineSoft),
    Accent(Tokens.Accent, Tokens.Surface, null),
    Navy(Tokens.Navy, Tokens.Surface, null),
    Caution(Tokens.CautionBg, Tokens.CautionText, Tokens.CautionBorder),
    Notice(Tokens.Ground, Tokens.InkSecondary, Tokens.Line),
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content),
        border = tone.border?.let { BorderStroke(1.dp, it) },
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.gap / 2),
            content = content,
        )
    }
}

/** 제목 + 설명 카드. [comingSoon]이면 아직 만들지 않은 기능이라는 표시를 붙인다. */
@Composable
fun TopicCard(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Neutral,
    comingSoon: Boolean = false,
) {
    InfoCard(modifier = modifier, tone = tone) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
        if (comingSoon) StatusChip(stringResource(R.string.coming_soon))
    }
}

@Composable
fun StatusChip(text: String, container: Color = Tokens.AccentSoft, content: Color = Tokens.Ink) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** 정책·정보 카드 하단 표기: "출처 [기관] · 최종 확인 YYYY.MM.DD" (PRD 5장 공통) */
@Composable
fun SourceFooter(source: String, verifiedDate: String) {
    Text(
        text = stringResource(R.string.source_footer, source, verifiedDate),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
