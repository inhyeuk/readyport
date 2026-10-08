package com.readyport.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.R
import com.readyport.board.BoardLimits
import com.readyport.board.BoardRepository
import com.readyport.board.NicknameProblem
import com.readyport.board.NicknameRules
import com.readyport.ui.components.AppScreen
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.IconBadge
import com.readyport.ui.components.IconBullet
import com.readyport.ui.components.KoText
import com.readyport.ui.components.LinkRow
import com.readyport.ui.components.PrimaryButton
import com.readyport.ui.components.QuietButton
import com.readyport.ui.components.IllusTones
import com.readyport.ui.components.isStackedLayout
import com.readyport.ui.nav.BoardJoinRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ======================= 처음 쓰기 전: 규칙 동의 → 게시판 이름 — DESIGN_SPEC 부록 L.8 =======================

enum class JoinStep { Rules, Nickname }

@Immutable
data class BoardJoinUi(
    val step: JoinStep = JoinStep.Rules,
    /** 규칙만 읽기(설정·게시판 아래 링크에서) — 동의 버튼 없음 */
    val readOnly: Boolean = false,
    val nickname: String = "",
    val problem: NicknameProblem? = null,
    val saving: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class BoardJoinViewModel @Inject constructor(handle: SavedStateHandle, private val repo: BoardRepository) : ViewModel() {
    private val route = handle.toRoute<BoardJoinRoute>()
    private val _ui = MutableStateFlow(BoardJoinUi(readOnly = route.rulesOnly, nickname = NicknameRules.suggest()))
    val ui: StateFlow<BoardJoinUi> = _ui.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    init {
        if (!route.rulesOnly) {
            viewModelScope.launch {
                // 이미 규칙에 동의했으면 이름 단계부터(닉네임까지 있으면 바로 끝)
                if (repo.rulesAgreed()) _ui.update { it.copy(step = JoinStep.Nickname) }
                val me = runCatching { repo.me() }.getOrNull()
                if (me?.joined == true && repo.rulesAgreed()) _done.value = true
            }
        }
    }

    fun agree() {
        viewModelScope.launch { repo.agreeRules() }
        _ui.update { it.copy(step = JoinStep.Nickname) }
    }

    fun setNickname(n: String) = _ui.update { it.copy(nickname = n, problem = NicknameRules.validate(n), error = null) }

    fun another() = setNickname(NicknameRules.suggest())

    fun save() {
        val s = _ui.value
        val problem = NicknameRules.validate(s.nickname)
        if (problem != null || s.saving) {
            _ui.update { it.copy(problem = problem) }
            return
        }
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.join(s.nickname)
                .onSuccess { _done.value = true }
                .onFailure { e -> _ui.update { it.copy(saving = false, error = e.toUiText()) } }
            _ui.update { it.copy(saving = false) }
        }
    }
}

@Composable
fun BoardJoinScreen(onDone: () -> Unit, viewModel: BoardJoinViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(done) { if (done) onDone() }
    when (ui.step) {
        JoinStep.Rules -> BoardRulesContent(onAgree = if (ui.readOnly) null else viewModel::agree, onContact = { contactOperator(context) })
        JoinStep.Nickname -> BoardNicknameContent(ui, viewModel::setNickname, viewModel::another, viewModel::save)
    }
}

/** 규칙 한 줄 */
private data class Rule(val icon: androidx.compose.ui.graphics.vector.ImageVector, val tone: BadgeTone, val title: Int, val body: Int)

private val Rules = listOf(
    Rule(Icons.Outlined.NoAccounts, BadgeTone.Danger, R.string.board_rule_private_title, R.string.board_rule_private_body),
    Rule(Icons.Outlined.Block, BadgeTone.Caution, R.string.board_rule_ads_title, R.string.board_rule_ads_body),
    Rule(Icons.Outlined.SentimentSatisfied, BadgeTone.Teal, R.string.board_rule_respect_title, R.string.board_rule_respect_body),
    Rule(Icons.Outlined.Gavel, BadgeTone.Violet, R.string.board_rule_illegal_title, R.string.board_rule_illegal_body),
    Rule(Icons.Outlined.Policy, BadgeTone.Accent, R.string.board_rule_official_title, R.string.board_rule_official_body),
)

/**
 * 커뮤니티 규칙: 그림(방패) + `함께 지켜요` + 규칙 다섯(배지 + 제목 + 한 줄) + 신고·임시조치 안내 + 운영자 연락 → `규칙을 지킬게요`(주 버튼).
 * [onAgree]가 null이면 읽기만(설정에서 열었을 때).
 */
@Composable
fun BoardRulesContent(onAgree: (() -> Unit)?, onContact: () -> Unit = {}) {
    val dimens = LocalDimens.current
    AppScreen(title = stringResource(R.string.board_rules_title), speech = stringResource(R.string.board_rules_speech), icon = Icons.Outlined.Gavel) {
        item(key = "head") {
            JoinHead(BoardIllus.Rules, IllusTones.Teal, stringResource(R.string.board_rules_head), stringResource(R.string.board_rules_lead))
        }
        item(key = "rules") {
            FormBlock {
                Rules.forEach { r ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconBadge(r.icon, tone = r.tone)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            KoText(stringResource(r.title), MaterialTheme.typography.titleMedium, color = Tokens.Ink, glueShort = true)
                            KoText(stringResource(r.body), MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
                        }
                    }
                }
            }
        }
        item(key = "enforce") {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.inner)) {
                IconBullet(stringResource(R.string.board_rules_enforce), Icons.Outlined.Policy, tone = BadgeTone.Neutral)
                LinkRow(stringResource(R.string.board_contact), onClick = onContact, icon = Icons.Outlined.MailOutline)
            }
        }
        if (onAgree != null) {
            item(key = "agree") { PrimaryButton(stringResource(R.string.board_rules_agree), onClick = onAgree, icon = Icons.Outlined.Check) }
        }
    }
}

/**
 * 게시판 이름 정하기: 그림(이름표) + 설명 → 이름 칸(`여행자 4821` 추천, 다른 이름 추천받기) → 무엇이 공개되는지 세 줄 → 시작(주 버튼).
 */
@Composable
fun BoardNicknameContent(
    ui: BoardJoinUi,
    onChange: (String) -> Unit = {},
    onAnother: () -> Unit = {},
    onSave: () -> Unit = {},
) {
    AppScreen(title = stringResource(R.string.board_nick_title), speech = stringResource(R.string.board_nick_speech), icon = Icons.Outlined.HowToReg) {
        item(key = "head") {
            JoinHead(BoardIllus.Nickname, IllusTones.Warm, stringResource(R.string.board_nick_head), stringResource(R.string.board_nick_lead))
        }
        item(key = "field") {
            FormBlock {
                BoardField(
                    label = stringResource(R.string.board_nick_field),
                    value = ui.nickname,
                    onChange = onChange,
                    max = BoardLimits.NICKNAME.last,
                    problem = nicknameProblem(ui.problem),
                    singleLine = true,
                )
                KoText(stringResource(R.string.board_nick_rule), MaterialTheme.typography.bodySmall, color = Tokens.InkTertiary)
                QuietButton(stringResource(R.string.board_nick_another), onClick = onAnother, icon = Icons.Outlined.Casino)
            }
        }
        item(key = "privacy") {
            FormBlock {
                IconBullet(stringResource(R.string.board_privacy_public), Icons.Outlined.Public, tone = BadgeTone.Accent)
                IconBullet(stringResource(R.string.board_privacy_local), Icons.Outlined.Lock, tone = BadgeTone.Success)
                IconBullet(stringResource(R.string.board_privacy_anon), Icons.Outlined.PhonelinkErase, tone = BadgeTone.Caution)
            }
        }
        ui.error?.let { e -> item(key = "error") { ErrorLine(e.text()) } }
        item(key = "save") {
            PrimaryButton(
                stringResource(R.string.board_nick_start),
                onClick = onSave,
                enabled = ui.problem == null && ui.nickname.isNotBlank() && !ui.saving,
                icon = Icons.Outlined.HowToReg,
            )
        }
    }
}

@Composable
private fun nicknameProblem(p: NicknameProblem?): String? = when (p) {
    NicknameProblem.TooShort -> stringResource(R.string.board_nick_short)
    NicknameProblem.TooLong -> stringResource(R.string.board_nick_long)
    NicknameProblem.BadChars -> stringResource(R.string.board_nick_chars)
    NicknameProblem.Reserved -> stringResource(R.string.board_nick_reserved)
    NicknameProblem.Profanity -> stringResource(R.string.board_nick_profanity)
    null -> null
}

/** 그림 패널 + 제목 + 한 줄 (규칙·이름 화면 머리) */
@Composable
private fun JoinHead(image: androidx.compose.ui.graphics.vector.ImageVector, tone: com.readyport.ui.components.IllusTone, title: String, lead: String) {
    BoardCard {
        val texts: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KoText(title, MaterialTheme.typography.titleLarge, color = Tokens.Ink, heading = true, glueShort = true)
                KoText(lead, MaterialTheme.typography.bodyMedium, color = Tokens.InkSecondary)
            }
        }
        if (isStackedLayout()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtPanel(image, tone, HeadArt)
                texts()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ArtPanel(image, tone, HeadArt)
                Box(Modifier.weight(1f)) { texts() }
            }
        }
    }
}

private val HeadArt = 96.dp
