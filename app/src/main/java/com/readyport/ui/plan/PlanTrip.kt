package com.readyport.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.readyport.R
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.DatePickField
import com.readyport.ui.components.DateRules
import com.readyport.ui.components.EqualWidthPair
import com.readyport.ui.components.minTouch
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.digitsOf
import com.readyport.ui.components.parseDateDigits
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import java.time.LocalDate

/**
 * '이 계획으로 새 여행 만들기' — 출발일만 고르면 나라·기간은 계획 그대로다(돌아오는 날 = 출발일 + 계획 일수 − 1).
 * 기존 여행은 건드리지 않는다. [initialStart]: 계획을 보낼 때 정한 출발일(없으면 비워 둔다).
 */
@Composable
fun PlanTripDialog(country: String, days: Int, initialStart: String?, creating: Boolean, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(horizontal = 16.dp)) { PlanTripCard(country, days, initialStart, creating, onCreate, onDismiss) }
    }
}

@Composable
fun PlanTripCard(country: String, days: Int, initialStart: String?, creating: Boolean, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var start by rememberSaveable { mutableStateOf(digitsOf(initialStart)) }
    val parsed: LocalDate? = remember(start) { parseDateDigits(start) }
    val dimens = LocalDimens.current
    val title = stringResource(R.string.plan_trip_title)
    Surface(
        color = Tokens.Surface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().semantics { paneTitle = title },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.gap),
        ) {
            KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, heading = true, glueShort = true)
            KoText(stringResource(R.string.plan_trip_body, country, days), MaterialTheme.typography.bodyLarge, color = Tokens.Ink)
            DatePickField(
                label = stringResource(R.string.trip_start),
                value = start,
                onChange = { start = it },
                note = stringResource(R.string.date_pick_note),
                error = start.isNotBlank() && parsed == null,
                rules = DateRules(openOn = parsed, years = LocalDate.now().year.let { (it - 1)..(it + 5) }),
            )
            if (parsed != null) {
                KoText(
                    stringResource(R.string.plan_trip_end, parsed.plusDays((days - 1).toLong()).toString()),
                    MaterialTheme.typography.bodyMedium,
                    color = Tokens.InkSecondary,
                )
            }
            IconBullet(stringResource(R.string.plan_trip_note), Icons.Outlined.EditCalendar, tone = BadgeTone.Neutral)
            EqualWidthPair(
                gap = 8.dp,
                first = { m ->
                    TextButton(onClick = onDismiss, modifier = m.minTouch(), colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent)) {
                        KoText(stringResource(R.string.plan_trip_cancel), MaterialTheme.typography.labelLarge)
                    }
                },
                second = { m ->
                    TextButton(
                        onClick = { parsed?.let { onCreate(it.toString()) } },
                        enabled = parsed != null && !creating,
                        modifier = m.minTouch(),
                        colors = ButtonDefaults.textButtonColors(contentColor = Tokens.Accent, disabledContentColor = Tokens.InkTertiary),
                    ) { KoText(stringResource(if (creating) R.string.plan_trip_creating else R.string.plan_trip_create), MaterialTheme.typography.labelLarge) }
                },
            )
        }
    }
}
