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
import androidx.compose.ui.test.onNodeWithContentDescription
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
    private fun s(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

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
        // 그림을 못 열었으면 QR이 있는 것처럼 보이지 않게 그렇다고 알린다
        shown(s(R.string.present_image_missing))
        shown("TDAC-0000")
        shown("L••••••C3")
        // '다른 폰으로 보내기' 대신 누가·무엇이·어디로 가는지 그대로 (재검토 R18 — '이 휴대폰에만' 약속과 부딪히지 않게)
        shown(s(R.string.present_share_family))
        shown(s(R.string.present_share_note))
        rule.onAllNodesWithText("다른 폰으로 보내기").assertCountEquals(0)
        // 지우기는 맨 아래 관리 줄 — TalkBack은 서류 이름과 함께 읽는다
        shown(s(R.string.present_delete))
        rule.onNodeWithContentDescription(s(R.string.delete_named_cd, "태국 입국 카드 (TDAC)")).assertExists()
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

        // 사람마다의 지우기는 TalkBack에서 누구를 지우는지 함께 읽는다 (재검토 R18)
        rule.onNodeWithContentDescription(s(R.string.delete_named_cd, "어머니")).assertExists()
        rule.onNodeWithContentDescription(s(R.string.delete_named_cd, "첫째")).performClick()
        assertEquals(null, deleted)
        rule.onNodeWithText(s(R.string.companion_delete_confirm_title)).assertIsDisplayed()
        // 대화상자에는 이름을 넣지 않는다
        rule.onAllNodesWithText("첫째").assertCountEquals(1)
        inDialog(s(R.string.companion_delete)).performClick()
        assertEquals("c1", deleted)
    }

    /** 칸 라벨은 짧게 보이지만 TalkBack·테스트가 읽는 칸 이름은 원문(companion_label) 하나 — 예시를 두 번 읽지 않는다 */
    @Test
    fun companionNameFieldKeepsFullAccessibleName() {
        rule.setContent { ReadyPortTheme { CompanionsContent(WalletState.Unlocked(VaultContents()), {}, {}, {}, {}) } }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(s(R.string.companion_label)))
        rule.onNodeWithText(s(R.string.companion_label)).assertIsDisplayed()
        rule.onAllNodesWithText(s(R.string.companion_label_example)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.companion_label_short)).assertCountEquals(0)
        // 빈 상태 본문은 제목을 되풀이하지 않는다
        shown(s(R.string.companion_empty_body))
        rule.onAllNodesWithText(s(R.string.companions_body)).assertCountEquals(0)
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

    /** 서류가 둘 이상이면 맨 아래 지우기 버튼마다 서류 이름이 보인다(어느 것을 지우는지) */
    @Test
    fun severalDocsNameTheirDeleteButtons() {
        val other = doc.copy(id = "d2", formId = "OTHER", confirmationNo = null)
        rule.setContent {
            ReadyPortTheme {
                PresentContent(
                    PresentUi(
                        locked = false,
                        travelers = listOf(Traveler("self", s(R.string.present_self))),
                        docs = listOf(DocView(doc, "태국 입국 카드 (TDAC)", null, null, null), DocView(other, "건강 신고서", null, null, null)),
                    ),
                    {}, {}, {}, {},
                )
            }
        }
        shown(s(R.string.present_delete_named, "태국 입국 카드 (TDAC)"))
        shown(s(R.string.present_delete_named, "건강 신고서"))
        rule.onAllNodesWithText(s(R.string.present_delete)).assertCountEquals(0)
    }
}
