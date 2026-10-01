package com.readyport.ui.form

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.readyport.BuildConfig
import com.readyport.R
import com.readyport.autofill.FieldReport
import com.readyport.autofill.FieldReporter
import com.readyport.autofill.FieldValue
import com.readyport.autofill.FormValues
import com.readyport.autofill.Recipe
import com.readyport.autofill.SafeClipboard
import com.readyport.pack.FormInfo
import com.readyport.pack.PackRepository
import com.readyport.pack.PackVersionSource
import com.readyport.ui.components.BannerTone
import com.readyport.ui.components.NoticeBanner
import com.readyport.ui.components.StatusKind
import com.readyport.ui.components.StatusTag
import com.readyport.ui.nav.FormConfirmRoute
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import com.readyport.vault.FormRecord
import com.readyport.vault.WalletRepository
import com.readyport.vault.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

/** 입국 서류 화면들이 공통으로 쓰는 준비물: 레시피, 국가 팩의 양식 안내, 안전 스위치 */
data class FormContext(
    val formId: String,
    val form: FormInfo?,
    val recipe: Recipe?,
    val recipeVersion: String?,
    val killed: Boolean,
) {
    /** 자동 입력을 쓸 수 있는지. 레시피가 없거나 스위치가 꺼졌으면 수동 모드 (ARCHITECTURE 9.2) */
    val autofillAvailable: Boolean get() = recipe != null && !killed
}

private suspend fun loadContext(formId: String, packs: PackRepository, versions: PackVersionSource): FormContext {
    val country = formId.substringBefore('_')
    val form = packs.pack(country)?.value?.forms?.firstOrNull { it.id == formId }
    val recipe = packs.recipe(formId)
    return FormContext(formId, form, recipe?.value, recipe?.version, versions.autofillKilled(formId))
}

// ---------------- 3개 국어 확인 (PRD 5.2) ----------------

data class ConfirmUi(
    val context: FormContext? = null,
    val wallet: WalletState = WalletState.Locked(false),
    val values: Map<String, FieldValue> = emptyMap(),
    /** 사용자가 이번에 적거나 고른 값 (메모리에만. 저장은 '맞아요'를 누를 때 암호화 보관함에) */
    val draft: Map<String, String> = emptyMap(),
    val saveFailed: Boolean = false,
)

@HiltViewModel
class FormConfirmViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val packs: PackRepository,
    private val versions: PackVersionSource,
    private val wallet: WalletRepository,
) : ViewModel() {
    private val formId = handle.toRoute<FormConfirmRoute>().formId
    private val _ui = MutableStateFlow(ConfirmUi())
    val ui: StateFlow<ConfirmUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = loadContext(formId, packs, versions)
            _ui.update { it.copy(context = ctx) }
            wallet.state.collect { state -> onWallet(state) }
        }
    }

    private fun onWallet(state: WalletState) {
        val ctx = _ui.value.context ?: return
        val contents = (state as? WalletState.Unlocked)?.contents
        val saved = contents?.forms?.get(formId)?.values.orEmpty()
        // 처음 열 때만 저장된 값·기본 제안으로 초안을 채운다
        val draft = _ui.value.draft.ifEmpty { FormValues.defaults(ctx.recipe ?: return@ifEmpty saved) + saved }
        val values = if (contents != null && ctx.recipe != null) FormValues.build(ctx.recipe, contents, draft) else emptyMap()
        _ui.update { it.copy(wallet = state, draft = draft, values = values) }
    }

    fun unlock() = viewModelScope.launch { wallet.unlock() }

    fun setValue(key: String, value: String) {
        _ui.update { ui ->
            val draft = ui.draft + (key to value)
            val contents = (ui.wallet as? WalletState.Unlocked)?.contents
            val recipe = ui.context?.recipe
            val values = if (contents != null && recipe != null) FormValues.build(recipe, contents, draft) else ui.values
            ui.copy(draft = draft, values = values)
        }
    }

    /** '맞아요, 입력해 주세요' — 확인한 값을 암호화 보관함에 저장 */
    suspend fun confirm(): WalletRepository.SaveResult {
        val draft = _ui.value.draft.filterValues { it.isNotBlank() }
        val result = wallet.update { c ->
            c.copy(forms = c.forms + (formId to FormRecord(formId, draft, "confirmed", LocalDateTime.now().toString())))
        }
        _ui.update { it.copy(saveFailed = result != WalletRepository.SaveResult.Saved) }
        return result
    }
}

// ---------------- 자동 입력 (PRD 5.3) · 수동 모드 ----------------

data class AutofillUi(
    val context: FormContext? = null,
    val values: Map<String, FieldValue> = emptyMap(),
    val engine: String? = null,
    val locked: Boolean = false,
    val submitted: Boolean = false,
)

@HiltViewModel
class AutofillViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val packs: PackRepository,
    private val versions: PackVersionSource,
    private val wallet: WalletRepository,
    private val reporter: FieldReporter,
    val clipboard: SafeClipboard,
) : ViewModel() {
    // AutofillRoute·FormManualRoute 모두 formId 인자를 가진다
    private val formId: String = checkNotNull(handle["formId"])
    private val _ui = MutableStateFlow(AutofillUi())
    val ui: StateFlow<AutofillUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val ctx = loadContext(formId, packs, versions)
            // 엔진은 앱에 내장된 파일만 쓴다. 원격 JavaScript는 실행하지 않는다 (PRD 7.5)
            val engine = withContext(Dispatchers.IO) {
                appContext.assets.open("autofill/engine.js").use { it.readBytes().decodeToString() }
            }
            _ui.update { it.copy(context = ctx, engine = engine) }
            if (ctx.killed) report("-", "kill_switch")
            wallet.state.collect { state ->
                val contents = (state as? WalletState.Unlocked)?.contents
                val values = if (contents != null && ctx.recipe != null) {
                    FormValues.build(ctx.recipe, contents, contents.forms[formId]?.values.orEmpty())
                } else emptyMap()
                _ui.update { it.copy(values = values, locked = contents == null) }
            }
        }
    }

    fun unlock() = viewModelScope.launch { wallet.unlock() }

    /** 지금 보이는 단계를 채울 계획 (JS 호출 문자열) */
    fun fillScript(visibleSteps: Set<String>): String? {
        val recipe = _ui.value.context?.recipe ?: return null
        val plan = FormValues.plan(recipe, _ui.value.values, visibleSteps)
        return "window.__readyport ? window.__readyport.fill($plan) : null"
    }

    fun probeScript(): String? {
        val recipe = _ui.value.context?.recipe ?: return null
        val probes = recipe.steps.map { it.probe ?: "__none__" }
        return "window.__readyport ? window.__readyport.probe(${kotlinx.serialization.json.JsonArray(probes.map { kotlinx.serialization.json.JsonPrimitive(it) })}) : null"
    }

    fun readScript(keys: Collection<String>): String? {
        val recipe = _ui.value.context?.recipe ?: return null
        val map = recipe.fields.filter { it.key in keys && it.selector != null }
            .associate { it.key to kotlinx.serialization.json.JsonPrimitive(it.selector!!) }
        return "window.__readyport ? window.__readyport.read(${kotlinx.serialization.json.JsonObject(map)}) : null"
    }

    /** 제출 완료 화면 그림을 지갑에 암호화해 저장 (입국 때 보여 주기) */
    suspend fun saveCapture(png: ByteArray): Boolean {
        val v = _ui.value.values
        return com.readyport.ui.present.saveCapture(wallet, png, formId, v["trip.arrival_date"]?.display, v["trip.flight_no"]?.value)
    }

    fun onSubmittedPage() {
        if (_ui.value.submitted) return
        _ui.update { it.copy(submitted = true) }
        viewModelScope.launch {
            wallet.update { c ->
                val now = LocalDateTime.now().toString()
                val rec = c.forms[formId] ?: FormRecord(formId, emptyMap(), "confirmed", now)
                c.copy(forms = c.forms + (formId to rec.copy(status = "submitted", submittedAt = now, updatedAt = now)))
            }
        }
    }

    /** 개인정보 없는 실패 리포트 */
    fun report(stepId: String, code: String, siteVersion: String? = null) {
        val ctx = _ui.value.context ?: return
        reporter.report(
            FieldReport(
                formId = formId,
                packVersion = ctx.recipeVersion ?: "none",
                stepId = stepId,
                errorCode = code,
                appVersion = BuildConfig.VERSION_NAME,
                ts = System.currentTimeMillis(),
                siteVersion = siteVersion,
            ),
        )
    }
}

// ======================= 한국어 줄바꿈 보정 (E 묶음 화면 공용: 16·17·22·28·자동 입력·수동 모드) =======================
// 공용 부품(components)은 1단계에서 동결이라 E 묶음 화면이 함께 쓰는 보정을 여기 둔다 — 2단계에서 공용 부품으로 옮길 후보.

/**
 * 한국어 줄바꿈 보정.
 * - API 33 미만(테스트 폰 S10 = Android 12)은 WordBreak.Phrase가 없어 한글을 **음절 사이 어디서나** 끊는다 —
 *   200%에서 `관/리`, `열/기`처럼 한 음절만 다음 줄로 떨어진다. 화면에 보이는 글자에만 낱말 안 글자 사이에
 *   WORD JOINER(U+2060)를 넣어 띄어쓰기에서만 줄이 바뀌게 한다(낱말이 한 줄보다 길면 Android가 그 안에서 자른다).
 * - [glueShort]: 제목의 한 음절 낱말(`이 휴대폰`의 `이`, `두 가지`의 `두`)이 줄 끝에 홀로 남지 않게 다음 낱말과 NBSP로 묶는다(모든 API).
 * TalkBack·테스트가 보는 글자(semantics)는 원문 그대로 둔다 — [KoText], [koSemantics].
 */
internal object KoBreak {
    const val WORD_JOINER = '\u2060'
    const val NBSP = '\u00A0'

    /** 이 기기가 한글을 음절마다 끊는지 (API 33 미만) */
    val syllableBreaks: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    /** 완성형 한글·호환 자모 (조합형 첫가끝 자모 U+1100–11FF는 사이에 글자를 넣으면 음절이 깨져 넣지 않는다) */
    fun isHangul(c: Char): Boolean = c in '\uAC00'..'\uD7A3' || c in '\u3130'..'\u318F'

    fun display(text: String, glueShort: Boolean = false, joinSyllables: Boolean = syllableBreaks): String {
        var s = text
        if (glueShort) s = glueShortWords(s)
        if (joinSyllables) s = joinSyllables(s)
        return s
    }

    /** 낱말(띄어쓰기 사이) 안에서 한글이 낀 두 글자 사이마다 WORD JOINER */
    fun joinSyllables(text: String): String {
        if (text.none(::isHangul)) return text
        val sb = StringBuilder(text.length * 2)
        text.forEachIndexed { i, c ->
            if (i > 0) {
                val p = text[i - 1]
                val inWord = !p.isWhitespace() && !c.isWhitespace() && p != WORD_JOINER && c != WORD_JOINER
                if (inWord && (isHangul(p) || isHangul(c))) sb.append(WORD_JOINER)
            }
            sb.append(c)
        }
        return sb.toString()
    }

    private val ShortWord = Regex("""(?<=^|[ \u00A0])([\uAC00-\uD7A3]) (?=\S)""")

    /** 한 음절 낱말 뒤 띄어쓰기를 NBSP로 (`내 정보는 이 휴대폰에만` → `이`가 `휴대폰에만`과 함께 움직인다) */
    fun glueShortWords(text: String): String = ShortWord.replace(text) { "${it.groupValues[1]}$NBSP" }

    /** 이 길이 이하만 한 덩어리로 묶는다 — 긴 이름(`Familydestinationsguide.com Images`)은 띄어쓰기에서 끊는 편이 낫다 */
    private const val KEEP_TOGETHER_MAX = 20

    /** 한 덩어리로 읽어야 하는 짧은 이름·라이선스(`CC BY 2.0`, `Kil Hyung-jin`): 띄어쓰기·하이픈에서 끊지 않는다 */
    fun keepTogether(text: String): String =
        if (text.length > KEEP_TOGETHER_MAX) text else text.replace(' ', NBSP).replace("-", "-$WORD_JOINER")

    /** 주소·호스트(`tdac.immigration.go.th`): 한 줄에 안 들어가면 점 뒤에서 끊는다(ZERO WIDTH SPACE) */
    fun breakAfterDots(text: String): String = breakAfter(text, ".")

    /** [marks] 글자 뒤에 줄을 바꿀 수 있는 자리(ZERO WIDTH SPACE)를 둔다 — 띄어쓰기 없는 긴 파일 이름(`12-Chureito-pagoda-…`) */
    fun breakAfter(text: String, marks: String): String =
        buildString(text.length + 8) {
            text.forEach { c ->
                append(c)
                if (c in marks) append(ZERO_WIDTH_SPACE)
            }
        }

    const val ZERO_WIDTH_SPACE = '\u200B'

}

/** 시스템 글자 크기 130% 이상: 배지·끝 요소 옆 좁은 칸 대신 제목·설명에 폭 전체를 준다 */
internal const val LARGE_FONT_SCALE = 1.3f

/** 150% 이상: 사진·견본처럼 고정 폭 요소를 글 위로 올린다 */
internal const val HUGE_FONT_SCALE = 1.5f

@Composable
@ReadOnlyComposable
internal fun largeFont(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

@Composable
@ReadOnlyComposable
internal fun hugeFont(): Boolean = LocalDensity.current.fontScale >= HUGE_FONT_SCALE

/**
 * 보이는 글자(보정본) 대신 원문을 TalkBack·테스트에 준다. 글자 배치 결과(GetTextLayoutResult)는 [layout]으로 그대로 노출한다.
 * 이 Modifier를 단 노드 아래 글자는 모두 가려지므로 **누를 수 없는 노드(Text·StatusTag·안내 띠)에만** 쓴다.
 */
internal fun Modifier.koSemantics(original: String, heading: Boolean = false, layout: (() -> TextLayoutResult?)? = null): Modifier =
    clearAndSetSemantics {
        text = AnnotatedString(original)
        if (heading) heading()
        if (layout != null) getTextLayoutResult { list -> layout()?.let { list.add(it); true } ?: false }
    }

/** 버튼(누를 수 있는 노드) 라벨을 보정했으면 이름은 원문으로 — 버튼 동작 semantics는 그대로 둔다 */
internal fun Modifier.koDescription(original: String, shown: String): Modifier =
    if (shown == original) this else semantics { contentDescription = original }

/**
 * 한국어 줄바꿈을 보정한 Text. 보정이 없으면 보통 Text와 같다.
 * [display]: 보일 글자를 직접 준다(이름·라이선스를 한 덩어리로 묶은 문장 등). TalkBack·테스트는 언제나 [text].
 */
@Composable
internal fun KoText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    glueShort: Boolean = false,
    heading: Boolean = false,
    textAlign: TextAlign? = null,
    display: String? = null,
) {
    val shown = remember(text, glueShort, display) { display ?: KoBreak.display(text, glueShort) }
    if (shown == text) {
        Text(text, modifier.then(if (heading) Modifier.semantics { heading() } else Modifier), color = color, style = style, textAlign = textAlign)
    } else {
        val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
        Text(
            shown,
            modifier.koSemantics(text, heading) { layout[0] },
            color = color,
            style = style,
            textAlign = textAlign,
            onTextLayout = { layout[0] = it },
        )
    }
}

/** 줄바꿈을 보정한 StatusTag (누를 수 없는 태그라 원문을 semantics로 덮어도 된다) */
@Composable
internal fun KoStatusTag(text: String, kind: StatusKind, modifier: Modifier = Modifier, shown: String = KoBreak.display(text)) {
    StatusTag(shown, kind, modifier.then(if (shown == text) Modifier else Modifier.koSemantics(text)))
}

/** 줄바꿈을 보정한 안내 띠 (누를 수 없음). 두 줄이면 TalkBack은 두 문장을 이어 읽는다 */
@Composable
internal fun KoNotice(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Notice,
    secondLine: String? = null,
    secondIcon: ImageVector? = null,
) {
    val shown = KoBreak.display(text)
    val shownSecond = secondLine?.let { KoBreak.display(it) }
    val same = shown == text && shownSecond == secondLine
    NoticeBanner(
        shown,
        modifier.then(if (same) Modifier else Modifier.koSemantics(listOfNotNull(text, secondLine).joinToString("\n"))),
        icon = icon,
        tone = tone,
        secondLine = shownSecond,
        secondIcon = secondIcon,
    )
}

/**
 * 입국 카드 화면(17·자동 입력·수동 모드)의 보안 한 줄 — `SecurityBanner(compact = true)`와 같은 모양에 제목 줄바꿈만 보정
 * (`…저장돼/요`, `이` 홀로 남음 방지). 정부 비제휴 고지 바로 다음에 둔다 (DESIGN_SPEC 6장 머리말).
 */
@Composable
internal fun CompactSecurityLine(modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    Surface(color = Tokens.Navy, contentColor = Tokens.Surface, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            // 큰 글자로 여러 줄이 되면 자물쇠를 첫 줄에 맞춘다
            verticalAlignment = if (largeFont()) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(dimens.icon))
            KoText(
                stringResource(R.string.settings_local_only_title),
                MaterialTheme.typography.titleSmall,
                Modifier.weight(1f),
                color = Tokens.Surface,
                glueShort = true,
            )
        }
    }
}

/**
 * [badge] [title] [trailing] 한 줄 + 그 아래 [below].
 * - [stack](큰 글자)이면 제목이 그 사이 한 줄에 다 들어가지 않을 때 배지·끝 요소만 윗줄에 두고 제목을 아래 줄 **폭 전체**로 내린다 —
 *   좁은 칸에서 제목이 음절 단위로 쪼개지지 않게. [below]도 폭 전체(배지 아래까지).
 * - 아니면 기존 목록 행처럼 배지 옆에 제목, [below]는 제목 자리에서 시작해 **끝 요소 아래까지** 넓힌다(오른쪽 끝이 들쭉날쭉하지 않게).
 * 설명이 없는 한 줄 행은 세로 가운데, 설명이 있으면 위 맞춤 (4.2).
 */
@Composable
internal fun BadgeTitleLayout(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    stack: Boolean = false,
    gap: Dp = 16.dp,
) {
    Layout(
        contents = listOf<@Composable () -> Unit>(badge ?: {}, title, trailing ?: {}, below ?: {}),
        modifier = modifier,
    ) { (badgeMs, titleMs, trailMs, belowMs), constraints ->
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else titleMs.first().maxIntrinsicWidth(Constraints.Infinity)
        val g = gap.roundToPx()
        val loose = Constraints(maxWidth = w)
        val b = badgeMs.firstOrNull()?.measure(loose)
        val t = trailMs.firstOrNull()?.measure(loose)
        val bw = b?.let { it.width + g } ?: 0
        val tw = t?.let { it.width + g } ?: 0
        val besideW = (w - bw - tw).coerceAtLeast(0)
        val titleM = titleMs.first()
        val stacked = stack && (b != null || t != null) && titleM.maxIntrinsicWidth(Constraints.Infinity) > besideW
        val small = 2.dp.roundToPx()
        if (stacked) {
            val topH = maxOf(b?.height ?: 0, t?.height ?: 0)
            val tp = titleM.measure(Constraints(maxWidth = w))
            val titleY = topH + 8.dp.roundToPx()
            val bp = belowMs.firstOrNull()?.measure(Constraints(maxWidth = w))
            val belowY = titleY + tp.height + small
            val h = maxOf(bp?.let { belowY + it.height } ?: (titleY + tp.height), constraints.minHeight)
            layout(w, h) {
                b?.placeRelative(0, (topH - b.height) / 2)
                t?.placeRelative(w - t.width, (topH - t.height) / 2)
                tp.placeRelative(0, titleY)
                bp?.placeRelative(0, belowY)
            }
        } else {
            val tp = titleM.measure(Constraints(maxWidth = besideW))
            val sideH = maxOf(b?.height ?: 0, t?.height ?: 0)
            val headH = maxOf(sideH, tp.height)
            // 한 줄 행(설명 없음)은 세로 가운데. 설명이 있으면 배지·제목·끝 요소의 위를 맞춘다
            val center = belowMs.isEmpty()
            fun y(h: Int) = if (center) (headH - h) / 2 else 0
            val belowX = if (stack) 0 else bw
            val bp = belowMs.firstOrNull()?.measure(Constraints(maxWidth = (w - belowX).coerceAtLeast(0)))
            val belowY = when {
                bp == null -> 0
                // 큰 글자: 배지 아래까지 폭 전체로
                stack -> headH + 8.dp.roundToPx()
                // 끝 요소(스위치 등) 아래부터는 오른쪽 끝까지 넓힌다
                else -> maxOf(tp.height + small, (t?.height ?: 0) + small)
            }
            val h = maxOf(headH, bp?.let { belowY + it.height } ?: 0, constraints.minHeight)
            layout(w, h) {
                b?.placeRelative(0, y(b.height))
                tp.placeRelative(bw, y(tp.height))
                t?.placeRelative(w - t.width, y(t.height))
                bp?.placeRelative(belowX, belowY)
            }
        }
    }
}
