package com.readyport.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.ui.theme.ReadyPortTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 0단계 공용 부품의 동작 (DESIGN_SPEC 4.1·4.5·4.13·4.16). 글자 폭을 실제로 재도록 NATIVE 그래픽 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp")
class ComponentsBehaviorTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun keyIndexRecordsAppScreenItemsAndScrolls() {
        val keys = KeyIndex()
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                state = rememberLazyListState()
                scope = rememberCoroutineScope()
                AppScreen(title = "제목", speech = "제목", state = state, keyIndex = keys) {
                    sectionGap("gap")
                    repeat(100) { i -> item(key = "row-$i") { Text("줄 $i") } }
                }
            }
        }
        rule.waitForIdle()
        // AppScreen이 넣는 header·easy-actions와 sectionGap Spacer까지 순서대로 기록된다
        assertEquals(0, keys.indexOf("header"))
        assertEquals(1, keys.indexOf("easy-actions"))
        assertEquals(2, keys.indexOf("gap"))
        assertEquals(3 + 30, keys.indexOf("row-30"))
        assertEquals(null, keys.indexOf("없음"))
        var moved = false
        rule.runOnIdle { scope.launch { moved = state.scrollToKey(keys, "row-30") } }
        rule.waitForIdle()
        assertTrue(moved)
        assertEquals(keys.indexOf("row-30"), state.firstVisibleItemIndex)
    }

    @Test
    fun gridColumnsFollowEasyModeAndWidth() {
        var basic = 0
        var easy = 0
        rule.setContent {
            ReadyPortTheme(easyMode = false) { basic = rememberGridColumns() }
            ReadyPortTheme(easyMode = true) { easy = rememberGridColumns() }
        }
        rule.waitForIdle()
        // 393dp 폭, 글자 100%: 2열 한 칸 = (393 − 40 − 12) / 2 = 170dp ≥ 150 → 2열
        assertEquals(2, basic)
        assertEquals(1, easy)
    }

    @Test
    fun minTouchFollowsDimens() {
        var easyMode by mutableStateOf(false)
        rule.setContent {
            ReadyPortTheme(easyMode = easyMode) {
                Box(Modifier.testTag("t").minTouch()) { Box(Modifier.size(10.dp)) }
            }
        }
        rule.onNodeWithTag("t").assertHeightIsAtLeast(48.dp)
        easyMode = true
        rule.waitForIdle()
        rule.onNodeWithTag("t").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun comingSoonItemsAreDisabledAndNotClickable() {
        val soon = context.getString(R.string.passport_chip_soon)
        rule.setContent {
            ReadyPortTheme {
                ComingSoonGroup(listOf(Icons.Outlined.Nfc to soon, Icons.Outlined.InstallMobile to context.getString(R.string.prepare_apps_title)))
            }
        }
        rule.onNodeWithText(soon).assertIsNotEnabled()
        rule.onNodeWithText(context.getString(R.string.coming_soon_group)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun countryPhotoTileKeepsLabelAndChipText() {
        val open = context.getString(R.string.home_country_open, "태국")
        val chip = context.getString(R.string.home_chip_visa_free, 90)
        rule.setContent {
            ReadyPortTheme {
                CountryPhotoTile("태국", "Thailand", Photos.country("TH"), listOf(ChipSpec(Icons.Outlined.EventAvailable, chip)), onClick = {}, openLabel = open)
            }
        }
        rule.onNodeWithContentDescription(open)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, chip))
        // clearAndSetSemantics를 쓰지 않아 칩 글자가 트리에 남는다 (D16)
        rule.onAllNodesWithText(chip).onFirst().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
    }

    @Test
    fun sourceListKeepsSingleFooterFormat() {
        val ref = SourceRef("외교부 해외안전여행 · 태국", "2026.09.28")
        rule.setContent { ReadyPortTheme { SourceList(listOf(ref, ref)) } }
        rule.onNodeWithText(context.getString(R.string.source_footer, ref.name, ref.verified)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
    }

    /** 검증 반영: trailing이 먼저 폭을 다 가져가 제목이 한 음절씩 쪼개지지 않게, 모자라면 제목 아래 줄로 (4.7, 7장 7번) */
    @Test
    fun cardTrailingMovesBelowTitleOnlyWhenItWouldSqueezeIt() {
        // 17 직접 입력 카드 그대로, 쉬운 모드(제목 24sp·태그 18sp·배지 52dp): 둘을 한 줄에 두면 제목이 꺾인다
        val title = context.getString(R.string.form_choose_yourself)
        val longTag = context.getString(R.string.form_missing_count, 2)
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                androidx.compose.foundation.layout.Column {
                    CardNewsCard(
                        title = title, icon = Icons.Outlined.EventAvailable, style = NewsStyle.SurfaceCaution,
                        trailing = { StatusTag(longTag, StatusKind.Required) },
                    )
                    CardNewsCard(title = "짧게", icon = Icons.Outlined.EventAvailable, trailing = { StatusTag("2", StatusKind.Info) })
                }
            }
        }
        val titleBox = rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val tagBox = rule.onNodeWithText(longTag).fetchSemanticsNode().boundsInRoot
        // 옆에 두면 제목이 꺾이므로 태그는 제목 아래
        assertTrue("태그가 제목 아래로 내려가야 함: title=$titleBox tag=$tagBox", tagBox.top >= titleBox.bottom)
        // 제목은 한 줄 그대로(쪼개지지 않음): 쉬운 모드 titleLarge 줄 높이 32sp의 1.5배 미만
        assertTrue("제목이 여러 줄로 쪼개짐: $titleBox", titleBox.height < 48 * rule.density.density)
        // 짧은 제목 + 작은 태그는 같은 줄
        val shortTitle = rule.onNodeWithText("짧게").fetchSemanticsNode().boundsInRoot
        val shortTag = rule.onNodeWithText("2").fetchSemanticsNode().boundsInRoot
        assertTrue("짧은 태그는 제목 옆: title=$shortTitle tag=$shortTag", shortTag.top < shortTitle.bottom && shortTag.left > shortTitle.right)
    }

    @Test
    fun listRowCustomTrailingDropsBelowWhenTitleWouldWrap() {
        // 쉬운 모드: 제목은 폭 전체면 한 줄이지만 배지(반입 불가)를 옆에 두면 꺾인다 → 배지가 제목 아래로
        val title = "망고 말린 것은 반입 불가"
        val badge = context.getString(R.string.import_prohibited)
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                androidx.compose.foundation.layout.Column {
                    ListRow(title, icon = Icons.Outlined.EventAvailable, trailing = RowTrailing.Custom { ImportVerdictBadge(com.readyport.prep.ImportStatus.Prohibited) })
                    ListRow("사과", icon = Icons.Outlined.EventAvailable, trailing = RowTrailing.Custom { ImportVerdictBadge(com.readyport.prep.ImportStatus.Allowed) })
                }
            }
        }
        // 누를 수 없는 행은 mergeDescendants라 글자 노드는 병합 전 트리에서 찾는다
        val t = rule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithText(badge, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("배지가 제목 아래로: title=$t badge=$b", b.top >= t.bottom)
        assertTrue("제목이 한 줄 그대로: $t", t.height < 45 * rule.density.density)
        // 짧은 제목은 배지가 옆
        val st = rule.onNodeWithText("사과", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val sb = rule.onNodeWithText(context.getString(R.string.import_allowed), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("짧은 제목은 배지가 옆: title=$st badge=$sb", sb.left > st.right && sb.top < st.bottom)
    }

    @Test
    fun expandableDetailSwitchesLabelToCollapse() {
        val more = context.getString(R.string.action_more)
        val less = context.getString(R.string.action_less)
        rule.setContent { ReadyPortTheme { ExpandableDetail { Text("안쪽 글") } } }
        rule.onNodeWithText(more).performClick()
        rule.onNodeWithText(less).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.state_expanded)))
        rule.onNodeWithText("안쪽 글").assertExists()
    }

    /** 검증 반영: 쉬운 모드라도 3칸 행에 놓인 SelectTile은 세로 배치(라벨 가운데) — 칸 폭 기준 (4.16) */
    @Test
    fun selectTileFollowsItsGridNotTheScreen() {
        rule.setContent {
            ReadyPortTheme(easyMode = true) {
                TileGrid(listOf("항공권", "숙소", "기타"), columns = 3) { label, cell ->
                    SelectTile(label, Icons.Outlined.EventAvailable, label == "숙소", {}, cell.testTag("tile-$label"))
                }
            }
        }
        val tile = rule.onNodeWithTag("tile-숙소").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithText("숙소", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tolerance = 2 * rule.density.density
        assertTrue("세로 배치면 라벨이 칸 가운데: tile=$tile label=$label", kotlin.math.abs(tile.center.x - label.center.x) < tolerance)
    }

    @Test
    fun stepListNumbersLiveInCirclesNotInText() {
        rule.setContent {
            ReadyPortTheme { StepList(listOf(Step(context.getString(R.string.today_departure_step1)), Step(context.getString(R.string.today_departure_step2)))) }
        }
        // D17: 문자열에는 "N. " 접두가 없다 — 번호는 원 안의 별도 글자
        assertTrue(!context.getString(R.string.today_departure_step2).startsWith("2."))
        rule.onNodeWithText(context.getString(R.string.today_departure_step2)).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
        rule.onAllNodesWithText("2").onFirst().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
    }
}
