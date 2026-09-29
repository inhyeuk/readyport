package com.readyport.ui.country

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.TestPacks
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 비자 온라인 신청 도우미: 비자가 필요한 나라(인도네시아 e-VOA)만, 출처와 함께, 입력은 기존 입국 신고 레시피로 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class VisaApplyTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun onlyVisaCountriesHaveApplyAndItPointsAtARecipe() = runBlocking {
        for (code in listOf("TH", "JP", "SG", "MY", "ID")) {
            val pack = TestPacks.repo.pack(code)!!.value
            val applies = pack.requirements.mapNotNull { it.apply }
            if (code == "ID") {
                val apply = applies.single()
                // 입력은 레시피가 있는 양식으로만 돕는다
                assertTrue(pack.forms.any { it.id == apply.form })
                assertNotNull(TestPacks.repo.recipe(apply.form))
                // 출처가 팩에 있고, 공식 주소는 인도네시아 정부 도메인
                assertNotNull(pack.source(apply.source))
                assertTrue(apply.officialUrl!!.startsWith("https://allindonesia.imigrasi.go.id/"))
            } else {
                assertTrue("$code 는 비자가 필요 없는데 신청 안내가 있음", applies.isEmpty())
            }
        }
    }

    @Test
    fun indonesiaShowsApplyCardThatOpensTheForm() {
        val opened = mutableListOf<String>()
        rule.setContent {
            ReadyPortTheme { CountryContent(TestPacks.countryUi("ID"), CountryActions(openForm = { opened += it })) }
        }
        val start = context.getString(R.string.country_visa_apply_start)
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("인도네시아 전자 도착비자 (e-VOA)"))
        rule.onNodeWithText("인도네시아 전자 도착비자 (e-VOA)").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(start))
        rule.onNodeWithText(start).performClick()
        assertEquals(listOf("ID_ALL_INDONESIA"), opened)
    }

    @Test
    fun visaFreeCountryHasNoApplyCard() {
        rule.setContent { ReadyPortTheme { CountryContent(TestPacks.countryUi("TH"), CountryActions()) } }
        rule.onAllNodesWithText(context.getString(R.string.country_visa_apply_start)).fetchSemanticsNodes().let {
            assertTrue(it.isEmpty())
        }
    }
}
