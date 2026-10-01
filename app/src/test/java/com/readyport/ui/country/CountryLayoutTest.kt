package com.readyport.ui.country

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.LocalTypeExtras
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 칸 위치를 비교하므로 실제 글자 폭으로 잰다(NATIVE) — LEGACY는 글자 하나를 약 1px로 잰다 */
private fun SemanticsNodeInteraction.bounds(): DpRect = getBoundsInRoot()

private fun DpRect.toRect() = Rect(left.value, top.value, right.value, bottom.value)

/**
 * 나라 화면 배치 회귀 (검토 지적): 숫자 타일 순서(6-04), 쇼핑 미리보기 판정 배지 위치(카드 단위, 1열이면 항상 이름 아래).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h2000dp")
class CountryLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    /**
     * 재검토 R12: 비자 카드 타일은 비자 사실만 [30일 도착비자][IDR 500,000 비자 비용] 한 줄, `IDR 500,000`은 쪼개지지 않고
     * (코드 `IDR` 작은 글자 + 숫자 한 줄), 입국 신고 비용 `무료`는 비자 카드가 아니라 1단계 입국 신고 카드 안 —
     * 비자 비용 옆에 `무료`가 붙어 비자가 무료로 읽히지 않게. 순서는 1단계 입국 신고 → 2단계 비자 신청.
     */
    @Test
    fun visaTilesHoldOnlyVisaFactsAndFormFeeMovesToTheFormCard() {
        var labelLine = 0f
        var statLine = 0f
        rule.setContent {
            ReadyPortTheme {
                labelLine = MaterialTheme.typography.labelMedium.lineHeight.value
                statLine = LocalTypeExtras.current.statSmall.lineHeight.value
                CountryContent(TestPacks.countryUi("ID"), CountryActions())
            }
        }
        val pack = runBlocking { TestPacks.repo.pack("ID")!!.value }
        val req = pack.requirements.single()
        val form = pack.forms.single { it.id in req.forms }
        val days = rule.onNodeWithText(s(R.string.fact_days, 30)).bounds().toRect()
        val fee = rule.onNodeWithText("IDR 500,000").bounds().toRect()
        assertEquals("머무는 날과 비자 비용은 같은 줄", days.top, fee.top, 1f)
        assertTrue("머무는 날이 왼쪽", days.right <= fee.left)
        // 금액 칸 = 통화 코드 한 줄 + 숫자 한 줄 (숫자가 두 줄이면 statSmall 한 줄만큼 더 높다)
        val amount = rule.onNodeWithText("IDR 500,000", useUnmergedTree = true).bounds().toRect()
        assertTrue("IDR 500,000이 두 줄 이상: ${amount.height}dp", amount.height < labelLine + statLine * 1.5f)
        // 입국 신고 비용 칩은 입국 신고(All Indonesia) 카드 제목 아래, 비자 타일보다 아래
        val formTitle = rule.onNodeWithText(form.nameKo).bounds().toRect()
        val formFee = rule.onNode(hasText(s(R.string.fact_label_form_fee))).bounds().toRect()
        assertTrue("입국 신고 비용이 양식 카드 밖", formFee.top > formTitle.top && formFee.top > fee.bottom)
        rule.onAllNodesWithText("무료").fetchSemanticsNodes().forEach { n ->
            assertTrue("비자 카드 안에 `무료`", n.boundsInRoot.top / rule.density.density > formTitle.top)
        }
        // 1단계(입국 신고)가 2단계(비자 신청)보다 위
        val step1 = rule.onNodeWithText(s(R.string.country_step_eyebrow, 1, s(R.string.entry_form_label))).bounds().toRect()
        val step2 = rule.onNodeWithText(s(R.string.country_step_eyebrow, 2, s(R.string.country_visa_apply_label))).bounds().toRect()
        assertTrue("1단계(입국 신고)가 2단계(비자 신청)보다 위에 있어야 함", step1.bottom <= step2.top)
    }

    @Test
    fun shoppingVerdictsSitTheSameWayInEveryRow() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("JP"), CountryActions(), CountrySection.Shopping) } }
        val below = verdictBelowName("JP")
        // 카드 단위로 정한다 — 한 줄은 옆, 다른 줄은 아래인 지그재그가 없어야 한다
        assertTrue("판정 배지 위치가 줄마다 다름: $below", below.distinct().size == 1)
    }

    /** 품목 이름(첫 3개)마다 판정 배지가 이름 아래에 있는지 (위에서부터 같은 순서로 짝짓는다) */
    internal fun verdictBelowName(code: String): List<Boolean> {
        val pack = runBlocking { TestPacks.repo.pack(code)!!.value }
        val names = pack.shopping.take(3).map { item ->
            rule.onNode(hasText(item.names.ko), useUnmergedTree = true).bounds().toRect()
        }
        val labels = listOf(R.string.import_allowed, R.string.import_caution, R.string.import_prohibited).map { s(it) }
        val verdicts = labels.flatMap { label ->
            rule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
                val b = n.boundsInRoot
                val d = rule.density.density
                Rect(b.left / d, b.top / d, b.right / d, b.bottom / d)
            }
        }.sortedBy { it.top }.take(3)
        assertEquals(3, verdicts.size)
        return names.sortedBy { it.top }.zip(verdicts).map { (name, verdict) ->
            // 겹치면 안 된다
            assertTrue("배지가 이름을 덮음: $name / $verdict", !name.overlaps(verdict))
            verdict.top >= name.bottom - 0.5f
        }
    }
}

/**
 * 쉬운 모드 + 글자 200% + sdk 31(S10): 1열이라 판정 배지는 항상 이름 아래 — 한 글자 이름(`차`)이 배지에 덮이던 문제 회귀.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h4000dp", fontScale = 2.0f)
class CountryLayoutLargeFontTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun easyModeVerdictsAlwaysBelowNames() {
        rule.setContent { ReadyPortTheme(easyMode = true) { CountryContent(TestPacks.countryUi("JP"), CountryActions(), CountrySection.Shopping) } }
        val pack = runBlocking { TestPacks.repo.pack("JP")!!.value }
        val d = rule.density.density
        fun rect(n: androidx.compose.ui.semantics.SemanticsNode) = n.boundsInRoot.let { Rect(it.left / d, it.top / d, it.right / d, it.bottom / d) }
        val names = pack.shopping.take(3).map { item ->
            rect(rule.onNode(hasText(item.names.ko), useUnmergedTree = true).fetchSemanticsNode())
        }.sortedBy { it.top }
        val labels = listOf(R.string.import_allowed, R.string.import_caution, R.string.import_prohibited).map { context.getString(it) }
        val verdicts = labels.flatMap { label ->
            rule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().map { rect(it) }
        }.sortedBy { it.top }.take(3)
        assertEquals(3, verdicts.size)
        names.zip(verdicts).forEach { (name, verdict) ->
            assertTrue("배지가 이름 아래에 있어야 함: $name / $verdict", verdict.top >= name.bottom - 0.5f)
        }
    }
}
