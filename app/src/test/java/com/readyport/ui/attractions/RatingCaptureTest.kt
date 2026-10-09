package com.readyport.ui.attractions

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.R
import com.readyport.attractions.AdvisoryState
import com.readyport.attractions.AttTestData
import com.readyport.attractions.rating.GoogleRating
import com.readyport.attractions.rating.MyVote
import com.readyport.attractions.rating.RatingStats
import com.readyport.board.BoardAge
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * 관광지 상세의 평점 칸(Google · 레디포트 이용자 · 내 별점)과 '확인 중' 띠 캡처 — app/build/screenshots/rating_*.png.
 * 별점 숫자는 화면 확인용 가짜 값이다(실제 Google 값이 아니다). 지도는 그리지 않는다(키 없음 환경).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w393dp-h851dp-xxhdpi")
class RatingCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val outDir = File("build/screenshots").apply { mkdirs() }
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun capture(name: String) {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(easy: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            ReadyPortTheme(easyMode = easy) {
                CompositionLocalProvider(LocalMapEnv provides MapEnv(keyPresent = false, online = true, playServices = false)) {
                    Box(Modifier.background(Tokens.Ground)) { content() }
                }
            }
        }
    }

    private fun scrollTo(text: String) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private val sample = AttTestData.debugSample()

    private fun detail(rating: RatingUi?, flagged: Boolean = false): AttractionDetailUi {
        val catalog = AttTestData.catalog(sample)
        val a = catalog.attraction("sensoji")!!
        return AttractionDetailUi(
            loading = false, country = "JP", countryName = "일본", catalog = catalog, attraction = a, region = catalog.region(a.regionId),
            sameRegion = AttractionsListModel.sameRegion(catalog, a, AdvisoryState.Normal), sourceNames = catalog.sources.mapValues { it.value.name },
            flagged = flagged, rating = rating,
        )
    }

    private val google = GoogleRating(4.5, 12_345, "https://maps.google.com/?cid=1")
    private val ownText get() = s(R.string.rating_own_stats, "4.3", 12)

    @Test fun bothRatingsAndPrompt() {
        val opened = mutableListOf<String>()
        show { AttractionDetailContent(detail(RatingUi(stats = RatingStats(4.3, 12), google = google, mine = MyVote.None)), AttractionDetailActions(openLink = { opened += it })) }
        scrollTo(s(R.string.rating_note))
        rule.onNode(hasContentDescription(s(R.string.rating_google_cd, "4.5", "1.2만"))).assertExists()
        rule.onNodeWithText(ownText).assertExists()
        rule.onNodeWithText(s(R.string.rating_prompt)).assertExists()
        capture("rating_01_google_and_own")
        rule.onNodeWithText(s(R.string.rating_google_open)).performClick()
        assertEquals(listOf("https://maps.google.com/?cid=1"), opened)
    }

    @Test fun hiddenWhenNoStatsAndNoGoogle() {
        show { AttractionDetailContent(detail(RatingUi(mine = MyVote.None)), AttractionDetailActions()) }
        scrollTo(s(R.string.rating_prompt))
        rule.onAllNodesWithText(ownText).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.rating_google_open)).assertCountEquals(0)
        capture("rating_02_hidden_stats")
    }

    @Test fun noRatingSectionWhenUnavailable() {
        show { AttractionDetailContent(detail(null), AttractionDetailActions()) }
        rule.onAllNodesWithText(s(R.string.rating_title)).assertCountEquals(0)
        rule.onAllNodesWithText(s(R.string.rating_prompt)).assertCountEquals(0)
    }

    @Test fun pickingStarsVotes() {
        val votes = mutableListOf<Int>()
        show { AttractionDetailContent(detail(RatingUi(mine = MyVote.None)), AttractionDetailActions(vote = { votes += it })) }
        scrollTo(s(R.string.rating_prompt))
        rule.onNodeWithText(s(R.string.rating_prompt)).performClick()
        rule.onNodeWithText(s(R.string.rating_pick_title)).assertExists()
        capture("rating_03_picking")
        rule.onNode(hasContentDescription(s(R.string.rating_star_cd, 4))).performClick()
        assertEquals(listOf(4), votes)
    }

    @Test fun myVoteCanBeChangedOrRemoved() {
        var removed = 0
        show { AttractionDetailContent(detail(RatingUi(stats = RatingStats(4.3, 12), mine = MyVote.Given(4), message = R.string.rating_saved)), AttractionDetailActions(removeVote = { removed++ })) }
        scrollTo(s(R.string.rating_remove))
        rule.onNode(hasContentDescription(s(R.string.rating_mine, 4))).assertExists()
        rule.onNodeWithText(s(R.string.rating_change)).assertExists()
        capture("rating_04_my_vote")
        rule.onNodeWithText(s(R.string.rating_remove)).performClick()
        assertEquals(1, removed)
    }

    @Test fun minorsSeeExplanationOnly() {
        show { AttractionDetailContent(detail(RatingUi(google = google, age = BoardAge.Status.Minor(YearMonth.of(2030, 1)))), AttractionDetailActions()) }
        scrollTo(s(R.string.rating_minor))
        rule.onAllNodesWithText(s(R.string.rating_prompt)).assertCountEquals(0)
        capture("rating_05_minor")
    }

    @Test fun ageCheckButtonWhenUnknown() {
        var asked = 0
        show { AttractionDetailContent(detail(RatingUi(age = BoardAge.Status.NeedsCheck)), AttractionDetailActions(checkAge = { asked++ })) }
        scrollTo(s(R.string.rating_age_check))
        rule.onNodeWithText(s(R.string.board_age_check_button)).performClick()
        assertEquals(1, asked)
    }

    @Test fun flaggedBand() {
        show { AttractionDetailContent(detail(RatingUi(), flagged = true), AttractionDetailActions()) }
        rule.onNodeWithText(s(R.string.attraction_flag_title)).assertExists()
        capture("rating_06_flag_band")
    }

    @Test fun noBandWhenNotFlagged() {
        show { AttractionDetailContent(detail(RatingUi()), AttractionDetailActions()) }
        rule.onAllNodesWithText(s(R.string.attraction_flag_title)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun ratingEasy200() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(easy = true) { AttractionDetailContent(detail(RatingUi(stats = RatingStats(4.3, 12), google = google, mine = MyVote.None)), AttractionDetailActions()) }
        scrollTo(s(R.string.rating_title))
        capture("rating_07_easy200")
    }
}
