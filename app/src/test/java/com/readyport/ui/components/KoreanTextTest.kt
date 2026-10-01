package com.readyport.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 한국어 줄바꿈 공용 도우미 (2단계 통합 — A·C·F keepWords, B koreanPhraseWrap, D keepAll/joinWords, E KoBreak를 하나로).
 * 규칙: API 33 미만에서만 낱말 안 WORD JOINER + 관형사·의존 명사·짧은 괄호 묶음 NBSP. 33 이상과 한글 없는 글은 그대로.
 */
class KoreanTextTest {

    private val wj = "\u2060"
    private val nbsp = "\u00A0"
    private val zwsp = "\u200B"

    /** 보이지 않는 글자를 걷어 내면 원문과 같아야 한다 (NBSP는 공백으로 돌려 비교) */
    private fun visible(s: String) = s.replace(wj, "").replace(zwsp, "").replace(nbsp, " ")

    // ---------------- keepWords: API 구분 ----------------

    @Test
    fun keepWordsJoinsSyllablesOnlyBelowApi33() {
        val s = keepWords("네, 처음이에요", sdk = 31)
        assertEquals("네, 처음이에요", visible(s))
        assertTrue("처${wj}음${wj}이${wj}에${wj}요" in s)
        // 띄어쓰기 앞뒤에는 넣지 않는다 → 줄은 띄어쓰기에서만 바뀐다
        assertFalse(" $wj" in s || "$wj " in s)
        // 줄바꿈 문자 앞뒤에도 넣지 않는다
        assertEquals("가${wj}나\n다", keepWords("가나\n다", sdk = 31))
    }

    @Test
    fun keepWordsLeavesTextAsIsFromApi33() {
        listOf("네, 처음이에요", "출국하는 날, 이 순서대로", "(6단계 중 1단계)", "관세청·농림축산검역본부").forEach {
            assertEquals(it, keepWords(it, sdk = 33))
            assertEquals(it, keepWords(it, sdk = 36))
        }
    }

    @Test
    fun keepWordsIsIdempotent() {
        listOf("주세요", "도착했어요! 이 순서대로 해요", "지금: 준비 (6단계 중 1단계)", "유심·인터넷 준비").forEach {
            val once = keepWords(it, sdk = 31)
            assertEquals(once, keepWords(once, sdk = 31))
        }
    }

    // ---------------- joinKoreanWords (C·D 묶음 규칙) ----------------

    @Test
    fun joinsSyllablesInsideWordsOnly() {
        assertEquals("숙${wj}소${wj}로 돌${wj}아${wj}가${wj}기", joinKoreanWords("숙소로 돌아가기"))
        // 숫자·괄호·영문이 한글에 붙어 있으면 한 낱말
        assertEquals("(6${wj}단${wj}계${nbsp}중 1${wj}단${wj}계${wj})", joinKoreanWords("(6단계 중 1단계)"))
        assertEquals("QR${wj}을", joinKoreanWords("QR을"))
        assertEquals("PDF${wj}에${wj}서 고${wj}르${wj}기", joinKoreanWords("PDF에서 고르기"))
        assertEquals("무${wj}료${wj})", joinKoreanWords("무료)"))
        assertEquals("Play 스${wj}토${wj}어${wj}에${wj}서 받${wj}기", joinKoreanWords("Play 스토어에서 받기"))
        // 이모지는 깨지지 않는다
        assertEquals("첫${wj}째 👨‍👩‍👧", joinKoreanWords("첫째 👨‍👩‍👧"))
    }

    @Test
    fun leavesNonKoreanTextAlone() {
        listOf("2026.09.29", "TDAC", "IDR 500,000", "+66-81-914-5803", "กรุณาเรียกตำรวจ", "Play Store 123", "").forEach {
            assertEquals(it, joinKoreanWords(it))
            assertEquals(it, keepWords(it, sdk = 31))
        }
        // 영문끼리는 원래 끊기지 않으므로 WORD JOINER를 넣지 않는다
        assertFalse(joinKoreanWords("태국 입국 카드 (TDAC)").contains("T${wj}D"))
    }

    @Test
    fun longWordsBreakAfterDotsAndBeforeOpenersShortWordsStayWhole() {
        // 긴 낱말(6자 초과): 가운뎃점 뒤·여는 괄호 앞은 줄을 바꿔도 된다
        assertEquals("관${wj}세${wj}청${wj}·농${wj}림${wj}축${wj}산", joinKoreanWords("관세청·농림축산"))
        assertEquals("800${wj}달${wj}러(${wj}과${wj}세${wj}가${wj}격", joinKoreanWords("800달러(과세가격"))
        // 한글 뒤 여는 괄호 앞은 짧은 낱말에서도 줄바꿈 자리 (`칩 확인 / (선택)`)
        assertEquals("확${wj}인(${wj}선${wj}택${wj})", joinKoreanWords("확인(선택)"))
        // 짧은 낱말의 가운뎃점·물결은 한 덩어리 (`과일·/식물`, `2일~/4일` 방지)
        assertEquals("과${wj}일${wj}·${wj}식${wj}물", joinKoreanWords("과일·식물"))
        // `~4`는 한글이 없어 WORD JOINER를 넣지 않지만 원래 끊기지 않는다(UAX#14 AL×NU)
        assertEquals("2${wj}일${wj}~4${wj}일", joinKoreanWords("2일~4일"))
    }

    @Test
    fun spacedSeparatorsStickToThePreviousWord() {
        // ` · `, ` — `가 줄 머리에 오지 않게 앞 띄어쓰기를 NBSP로
        assertEquals("일${wj}본${wj}정${wj}부${wj}관${wj}광${wj}국$nbsp· 기${wj}념${wj}품", keepWords("일본정부관광국 · 기념품", sdk = 31))
        assertTrue("신고하세요$nbsp— 관세의" in glueGroups("신고하세요 — 관세의"))
        // 따옴표·화살표는 묶지 않는다 — 좁은 줄에서 묶음이 한 줄보다 길어지면 음절 사이에서 억지로 끊긴다
        assertEquals("'오프라인 지도' → '내 지도 선택'", glueGroups("'오프라인 지도' → '내 지도 선택'"))
    }

    @Test
    fun determinerStaysWithNextWord() {
        assertEquals("이${nbsp}순${wj}서${wj}대${wj}로", joinKoreanWords("이 순서대로"))
        assertEquals("새${nbsp}여${wj}행", joinKoreanWords("새 여행"))
        // 낱말 끝의 '이'(조사)는 관형사가 아니다
        assertTrue(joinKoreanWords("인터넷이 없어도").contains("이 없"))
    }

    @Test
    fun boundNounStaysWithPreviousWord() {
        assertEquals("낼${nbsp}수 있${wj}어${wj}요", joinKoreanWords("낼 수 있어요"))
        // '수'로 시작하는 보통 낱말(수입)은 그대로
        assertTrue(joinKoreanWords("밝힌 수입 금지").contains("힌 수"))
    }

    @Test
    fun quotesAndParenthesesBreakAtTheirSpaces() {
        // 따옴표·괄호 묶음은 묶지 않는다 — 쉬운 모드 200%의 좁은 줄에서 묶음이 한 줄보다 길어지면 음절 사이에서 억지로 끊긴다
        assertFalse(nbsp in keepWords("'내 지도 선택'을 눌러요", sdk = 31))
        val stage = keepWords("지금: 준비 (6단계 중 1단계)", sdk = 31)
        assertEquals("지${wj}금${wj}: 준${wj}비 (6${wj}단${wj}계${nbsp}중 1${wj}단${wj}계${wj})", stage)
        assertFalse(nbsp in glueGroups("(둥근 핀 2개)"))
    }

    @Test
    fun visibleTextIsUnchanged() {
        listOf(
            "도착했어요! 이 순서대로 해요",
            "현지어 문장 카드와 긴급 연락처는 인터넷이 없어도 볼 수 있어요.",
            "내는 때: 태국에 도착하는 날을 포함해 3일 안에 내요. 예: 5월 4일 도착이면 5월 2일~4일",
            "외교부 해외안전여행 · 태국",
            "관광경찰 (영어·한국어, 무료)",
            "면세 한도와 반입 금지 품목은 관세청·농림축산검역본부 공식 안내로 확인하세요.",
        ).forEach {
            assertEquals(it, visible(keepWords(it, sdk = 31)))
            assertEquals(it, visible(koDisplay(it, glueShort = true, sdk = 31)))
            assertEquals(it, visible(koDisplay(it, glueShort = true, sdk = 36)))
        }
    }

    // ---------------- koDisplay (KoText 전용 — 모든 API) ----------------

    @Test
    fun longDottedWordsBreakAfterTheDotOnAllApis() {
        val body = koDisplay("관세청·농림축산검역본부 공식 안내", sdk = 36)
        assertTrue("관세청·$zwsp" in body)
        // 짧은 가운뎃점 낱말은 그대로 (`과일·식물`)
        assertEquals("과일·식물", koDisplay("과일·식물", sdk = 36))
        // 띄어쓴 가운뎃점(출처 이름)은 앞 낱말에 붙기만 한다
        assertEquals("외교부 해외안전여행$nbsp· 태국", koDisplay("외교부 해외안전여행 · 태국", sdk = 36))
        // 주소는 점 뒤에서 줄을 바꿀 수 있고, 날짜·숫자는 그대로
        assertTrue("imigrasi.${zwsp}go.${zwsp}id" in koDisplay("공식 사이트(imigrasi.go.id)만 열어요.", sdk = 36))
        assertFalse(zwsp in koDisplay("최종 확인 2026.09.29", sdk = 36))
        // 긴 낱말은 여는 괄호 앞에서도 줄을 바꿀 수 있다 (`1단/계(여행유의)` 방지)
        assertEquals("1단계$zwsp(여행유의)예요.", koDisplay("1단계(여행유의)예요.", sdk = 36))
        // 한글 뒤 괄호는 짧은 낱말에서도 (`칩 / 확인(선택)` 대신 `칩 확인 / (선택)`), 낱말 첫 글자 괄호는 그대로
        assertEquals("칩 확인$zwsp(선택)", koDisplay("칩 확인(선택)", sdk = 36))
        assertEquals("(TDAC)를", koDisplay("(TDAC)를", sdk = 36))
        // 짧은 가운뎃점 낱말은 그대로
        assertEquals("과일·식물", koDisplay("과일·식물", sdk = 36))
    }

    @Test
    fun glueShortWordsKeepsOneSyllableWordWithTheNext() {
        assertEquals("내${nbsp}정보는 이${nbsp}휴대폰에만 저장돼요", glueShortWords("내 정보는 이 휴대폰에만 저장돼요"))
        assertEquals("두${nbsp}가지", glueShortWords("두 가지"))
        // 낱말 끝 한 음절이 아니면 그대로
        assertEquals("휴대폰 정보", glueShortWords("휴대폰 정보"))
    }

    @Test
    fun noBreakAndKeepTogether() {
        val s = noBreak("11월 3일 (화)")
        assertFalse(" " in s)
        assertEquals("11월 3일 (화)", visible(s))
        assertEquals("CC${nbsp}BY${nbsp}2.0", keepTogether("CC BY 2.0"))
        assertEquals("Kil${nbsp}Hyung-${wj}jin", keepTogether("Kil Hyung-jin"))
        val long = "Familydestinationsguide.com Images"
        assertEquals(long, keepTogether(long))
        assertEquals("tdac.${zwsp}immigration.${zwsp}go.${zwsp}th", breakAfterDots("tdac.immigration.go.th"))
    }
}

/**
 * KoText: API 33 미만(sdk 31)에서 보이는 글자에만 보이지 않는 문자가 들어가고, 의미 글자(TalkBack·테스트)는 원문 그대로다.
 * 공용 부품(StatusTag·SourceFooter)도 같은 규칙을 지킨다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h2000dp", fontScale = 2.0f)
class KoTextSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shown(text: String): String {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.text.text
    }

    @Test
    fun visibleTextIsJoinedButSemanticsKeepTheOriginal() {
        val title = "숙소로 돌아가기"
        val tag = "반입 주의"
        val footer = SourceRef("외교부 해외안전여행 · 태국", "2026.09.29")
        rule.setContent {
            ReadyPortTheme {
                androidx.compose.foundation.layout.Column {
                    KoText(title)
                    StatusTag(tag, StatusKind.Caution)
                    SourceFooter(footer)
                }
            }
        }
        rule.onAllNodesWithText(title).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        assertTrue("보이는 글자는 낱말 안 WORD JOINER: ${shown(title)}", "숙\u2060소" in shown(title))
        assertTrue("\u2060" in shown(tag))
        val full = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.readyport.R.string.source_footer, footer.name, footer.verified)
        rule.onAllNodesWithText(full).fetchSemanticsNodes().let { assertEquals(1, it.size) }
    }

    @Test
    fun sourceFooterDateNeverSplits() {
        val footer = SourceRef("국토교통부 보조배터리 기내 반입 기준 (2026.4.8 발표)", "2026.09.29")
        rule.setContent { ReadyPortTheme(easyMode = true) { SourceFooter(footer) } }
        val full = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.readyport.R.string.source_footer, footer.name, footer.verified)
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(full).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.single()
        val text = layout.layoutInput.text.text
        val start = text.indexOf(footer.verified)
        assertTrue(start >= 0)
        assertEquals("날짜가 두 줄로 쪼개짐: $text", layout.getLineForOffset(start), layout.getLineForOffset(start + footer.verified.length - 1))
    }
}
