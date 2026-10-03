package com.readyport.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 둘러보기 히어로 소개 한 줄 (운영자 2026-10-03): *"'레디포트는 당신의 여행이 수월해지도록 돕습니다.'라는 문구를
 * 작게 2줄 이내로 표시해줘."*
 * - 문구가 **운영자 문장 그대로**인지 (합니다체 예외 — 해요체로 '고치지' 않는다).
 * - 어느 조건에서도 **두 줄을 넘지 않는지**: 기본·쉬운 모드 × 100%·200% × 393dp·360dp × 기기 언어 영어.
 *   글자 폭을 실제로 재도록 NATIVE 그래픽으로 돌린다(LEGACY는 글자 하나를 1px로 재서 줄 수가 실제와 다르다).
 * - 예전 문구(`home_value_prop`)가 둘러보기 첫 화면에서 사라졌는지 — 첫 실행·스토어 그래픽에는 그대로 남는다.
 */
abstract class HomeTaglineBase {

    @get:Rule
    val rule = createComposeRule()

    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected fun show(easy: Boolean) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                HomeContent(TestPacks.homeUi(), HomeActions(), today = LocalDate.of(2026, 9, 28))
            }
        }
        rule.waitForIdle()
    }

    /** 소개 한 줄이 실제로 몇 줄로 그려졌는지 (FitLines가 GetTextLayoutResult로 알려 준다) */
    private fun lines(): Int? {
        val node = rule.onAllNodes(hasText(context.getString(R.string.explore_hero_tagline)))
            .fetchSemanticsNodes().firstOrNull() ?: return null
        val results = mutableListOf<TextLayoutResult>()
        runCatching { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
        return results.firstOrNull()?.lineCount
    }

    protected fun assertAtMostTwoLines(easy: Boolean, label: String) {
        show(easy)
        val n = lines()
        assertNotNull("$label: 소개 한 줄이 화면에 없다", n)
        println("TAGLINE $label: lines=$n")
        assertTrue("$label: 소개 한 줄이 ${n}줄이다 (2줄 이내여야 함)", n!! <= TAGLINE_MAX_LINES)
        // 예전 가치 문장은 둘러보기에서 사라졌다 (첫 실행·스토어 그래픽에는 남아 있다)
        assertEquals(
            "$label: 예전 가치 문장이 둘러보기에 남아 있다",
            0,
            rule.onAllNodes(hasText(context.getString(R.string.home_value_prop))).fetchSemanticsNodes().size,
        )
    }
}

/** 393dp · 글자 100% */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w393dp-h4000dp-xhdpi")
class HomeTaglineTest : HomeTaglineBase() {

    /** 운영자가 적어 준 문장 그대로인지 (합니다체 예외 — 지키라고 둔 테스트다) */
    @Test fun taglineIsTheOwnersSentenceVerbatim() {
        assertEquals("레디포트는 당신의 여행이 수월해지도록 돕습니다.", context.getString(R.string.explore_hero_tagline))
    }

    @Test fun basic() = assertAtMostTwoLines(easy = false, label = "393dp-100-basic")

    @Test fun easy() = assertAtMostTwoLines(easy = true, label = "393dp-100-easy")
}

/** 393dp · 글자 200% (S10과 같은 sdk 31 선형 2배) */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w393dp-h6000dp-xhdpi", fontScale = 2.0f)
class HomeTaglineFont200Test : HomeTaglineBase() {

    @Test fun basic() = assertAtMostTwoLines(easy = false, label = "393dp-200-basic")

    @Test fun easy() = assertAtMostTwoLines(easy = true, label = "393dp-200-easy")
}

/** 360dp 좁은 창 · 글자 200% */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [31], qualifiers = "ko-rKR-w360dp-h6000dp-xhdpi", fontScale = 2.0f)
class HomeTaglineNarrowTest : HomeTaglineBase() {

    @Test fun basic() = assertAtMostTwoLines(easy = false, label = "360dp-200-basic")

    @Test fun easy() = assertAtMostTwoLines(easy = true, label = "360dp-200-easy")
}

/** 기기 언어가 영어일 때 (문구는 한국어 그대로 — 줄 수만 본다) */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "en-rUS-w393dp-h4000dp-xhdpi")
class HomeTaglineEnglishLocaleTest : HomeTaglineBase() {

    @Test fun basic() = assertAtMostTwoLines(easy = false, label = "en-393dp-100-basic")

    @Test fun easy() = assertAtMostTwoLines(easy = true, label = "en-393dp-100-easy")
}
