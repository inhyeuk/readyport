package com.readyport.ui.present

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.vault.EntryDoc
import com.readyport.vault.PassportRecord
import com.readyport.vault.TravelCompanion
import com.readyport.vault.VaultContents
import com.readyport.vault.WalletState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 21 입국 때 보여 주기·27 같이 가는 사람: 잠금, 되돌릴 수 없는 지우기 전 확인(D8), 동반자별 여권 등록 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class PresentUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(@StringRes id: Int) = context.getString(id)

    private fun shown(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun inDialog(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private val doc = EntryDoc(
        id = "d1", formId = "TH_TDAC", travelerId = "self", blobId = "b1", confirmationNo = "TDAC-0000",
        arrivalDate = "2026-11-03", flightNo = "KE651", source = "capture", savedAt = "2026-11-01T10:00",
    )

    @Test
    fun lockedPresentShowsOfflineAndUnlock() {
        var unlocked = false
        rule.setContent { ReadyPortTheme { PresentContent(PresentUi(locked = true), { unlocked = true }, {}, {}, {}) } }
        rule.onNodeWithText(s(R.string.present_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.present_offline)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.settings_local_only_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.wallet_unlock)).performClick()
        assertTrue(unlocked)
        rule.onAllNodesWithText(s(R.string.present_brightness)).assertCountEquals(0)
    }

    @Test
    fun presentDeleteAsksFirstAndShowsMaskedValues() {
        var deleted: EntryDoc? = null
        rule.setContent {
            ReadyPortTheme {
                PresentContent(
                    PresentUi(
                        locked = false,
                        travelers = listOf(Traveler("self", s(R.string.present_self))),
                        docs = listOf(DocView(doc, "태국 입국 카드 (TDAC)", null, "E•••••• A•••", "L••••••C3")),
                    ),
                    {}, {}, {}, { deleted = it },
                )
            }
        }
        shown("TDAC-0000")
        shown("L••••••C3")
        shown(s(R.string.present_delete))
        rule.onNodeWithText(s(R.string.present_delete)).performClick()
        assertEquals(null, deleted)
        rule.onNodeWithText(s(R.string.present_delete_confirm_title)).assertIsDisplayed()
        inDialog(s(R.string.present_delete)).performClick()
        assertEquals("d1", deleted?.id)
    }

    @Test
    fun emptyPresentOffersAddPhoto() {
        var added: String? = null
        rule.setContent {
            ReadyPortTheme {
                PresentContent(PresentUi(locked = false, travelers = listOf(Traveler("self", s(R.string.present_self)))), {}, { added = it }, {}, {})
            }
        }
        shown(s(R.string.present_empty_title))
        rule.onNodeWithText(s(R.string.present_add_photo)).performClick()
        assertEquals("self", added)
    }

    @Test
    fun companionsRegisterPassportAndDeleteAfterConfirm() {
        val passport = PassportRecord("ERIKSSON", "ANNA MARIA", "L898902C3", "UTO", "UTO", "1974-08-12", "F", "2036-04-15", "mrz", true, "x")
        val companions = listOf(
            TravelCompanion("c1", "첫째", null, consentConfirmed = true, addedAt = "x"),
            TravelCompanion("c2", "어머니", passport, consentConfirmed = true, addedAt = "x"),
        )
        var register: String? = null
        var deleted: String? = null
        rule.setContent {
            ReadyPortTheme {
                CompanionsContent(WalletState.Unlocked(VaultContents(companions = companions)), {}, {}, { deleted = it }, { register = it })
            }
        }
        // 여권 등록 버튼은 여권이 없는 사람에게만, 등록된 사람은 '여권 등록됨'
        rule.onAllNodesWithText(s(R.string.companion_passport_add)).assertCountEquals(1)
        rule.onAllNodesWithText(s(R.string.companion_passport_done)).assertCountEquals(1)
        rule.onNodeWithText(s(R.string.companion_passport_add)).performClick()
        assertEquals("c1", register)

        rule.onAllNodesWithText(s(R.string.companion_delete))[0].performClick()
        assertEquals(null, deleted)
        rule.onNodeWithText(s(R.string.companion_delete_confirm_title)).assertIsDisplayed()
        // 대화상자에는 이름을 넣지 않는다
        rule.onAllNodesWithText("첫째").assertCountEquals(1)
        inDialog(s(R.string.companion_delete)).performClick()
        assertEquals("c1", deleted)
    }

    @Test
    fun lockedCompanionsAskToUnlock() {
        var unlocked = false
        rule.setContent { ReadyPortTheme { CompanionsContent(WalletState.Locked(hasData = true), { unlocked = true }, {}, {}, {}) } }
        rule.onNodeWithText(s(R.string.wallet_locked_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.wallet_unlock)).performClick()
        assertTrue(unlocked)
        rule.onAllNodesWithText(s(R.string.companion_add)).assertCountEquals(0)
    }
}
