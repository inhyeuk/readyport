package com.readyport.ui.notice

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.readyport.ui.components.Illus
import com.readyport.ui.components.IllusImage
import com.readyport.ui.components.IllusTone
import com.readyport.ui.components.IllusTones
import com.readyport.ui.components.illusPanelBrush
import com.readyport.ui.components.keepWords
import com.readyport.ui.theme.Pretendard
import com.readyport.ui.theme.ReadyPortTheme
import com.readyport.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 첫 공지 `about-readyport`의 카드뉴스 두 장 (운영자 요청 2026-10-08: 앱 소개 + 개인정보는 휴대폰에만).
 * 앱과 똑같은 글꼴(Pretendard)·토큰·일러스트(Illus)로 그린다 → build/notice-cards/ 아래 PNG (1080×1350, 4:5).
 * 커밋본은 hosting/public/notices/ 에 **같은 이름**으로 복사한다(main 머지 때 notices.yml 이 Hosting 에 올린다).
 * 문구는 [AboutCards] 한 곳 — 공지의 대체 글(notices/notices.json alt_ko)이 카드 글을 모두 담는지 테스트가 본다.
 * 문구는 앱이 실제로 하는 일만: 여권 정보는 Keystore AES-256-GCM으로 휴대폰 안에만(vault/), 운영자 서버로 보내는 것은
 * 익명 리포트·찜 수·토픽 구독뿐(cloud/CloudSync), 귀국 단계의 `여권 정보를 지울까요?` 버튼 하나로 지운다.
 * 절대 새지 않는다고 말하지 않는다(잃어버린·루팅한 휴대폰, 악성 앱) — 대신 정직한 각주 `휴대폰 잠금은 꼭 걸어 두세요`.
 * 그림은 공지 대화상자에서 카드 폭(360dp 폰에서 약 328dp)으로 줄어 보인다 — 400dp 캔버스의 글자가 0.82배가 된다
 * (21sp 굵은 줄 → 약 17sp, 16sp 보조 줄 → 약 13sp). 그래서 글은 적게, 크게.
 */
object AboutCards {
    const val FILE_ABOUT = "about-readyport-1.png"
    const val FILE_PRIVACY = "about-readyport-2.png"

    const val BRAND = "레디포트"
    const val ABOUT_TITLE = "레디포트는\n이런 앱이에요"
    val ABOUT_POINTS = listOf(
        "계획부터 복귀까지 8단계" to "여행을 만들면 할 일을 알려 드려요",
        "입국 카드 칸은 앱이 채워요" to "제출은 직접 눌러요",
        "9개 나라 · 24개 공항 안내" to "모두 공식 출처와 확인 날짜까지",
    )
    const val NOT_AFFILIATED = "정부 기관과 제휴하지 않은 앱이에요"

    const val PRIVACY_TITLE = "여권 정보는\n이 휴대폰에만"
    const val PRIVACY_KEY = "레디포트 서버에는\n여러분의 여권 정보가\n아예 없어요"
    const val PRIVACY_KEY_EMPHASIS = "아예 없어요"
    val PRIVACY_POINTS = listOf(
        "휴대폰 안에서 암호화해 보관해요",
        "서버 해킹으로 새어 나갈 일이 없어요",
        "여행이 끝나면 버튼 하나로 지울 수 있어요",
    )
    const val PRIVACY_FOOTNOTE = "휴대폰 잠금은 꼭 걸어 두세요"

    /** 대체 글에 들어가야 하는 카드 글 */
    fun aboutLines() = listOf(ABOUT_TITLE) + ABOUT_POINTS.flatMap { listOf(it.first, it.second) } + NOT_AFFILIATED
    fun privacyLines() = listOf(PRIVACY_TITLE, PRIVACY_KEY) + PRIVACY_POINTS + PRIVACY_FOOTNOTE
}

/** 그림 속 줄바꿈은 어절 단위로 (Robolectric의 줄바꿈 설정에 기대지 않고 API 33 미만 보정을 언제나) */
private fun words(text: String) = keepWords(text, sdk = Build.VERSION_CODES.S)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Pretendard, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** 카드 머리: Navy → AccentDeep, 아래 모서리 둥글게, 오른쪽 위 옅은 원 두 개(꾸밈) + (있으면) 오른쪽 그림 */
@Composable
private fun CardHeader(icon: Bitmap, title: String, art: (@Composable () -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(listOf(Tokens.Navy, Tokens.AccentDeep))),
    ) {
        Box(Modifier.offset(x = 292.dp, y = (-52).dp).size(160.dp).clip(CircleShape).background(Tokens.White12))
        Box(Modifier.offset(x = 344.dp, y = 84.dp).size(84.dp).clip(CircleShape).background(Tokens.White12))
        Row(
            Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(9.dp))
                    Text(AboutCards.BRAND, style = style(18, 22, FontWeight.Bold), color = Tokens.White85)
                }
                Text(words(title), style = style(30, 37, FontWeight.Bold), color = Tokens.Surface)
            }
            art?.invoke()
        }
    }
}

@Composable
private fun CardFrame(content: @Composable ColumnScope.() -> Unit) {
    ReadyPortTheme(easyMode = false) {
        Column(Modifier.fillMaxSize().background(Tokens.Ground), content = content)
    }
}

/** 흰 카드 줄 (모서리 22 + 옅은 1dp 테두리 — 앱의 흰 정보 카드와 같은 경계) */
private fun Modifier.whiteCard(radius: Int = 22) =
    clip(RoundedCornerShape(radius.dp)).background(Tokens.Surface).border(1.dp, Tokens.LineSoft, RoundedCornerShape(radius.dp))

/** ① 레디포트는 이런 앱이에요 — 그림 패널 + 굵은 한 줄 + 보조 한 줄 세 개, 맨 아래 비제휴 한 줄 */
@Composable
fun AboutCardArt(icon: Bitmap) {
    val arts: List<Pair<ImageVector, IllusTone>> = listOf(
        Illus.Plan to IllusTones.Blue,
        Illus.Entry to IllusTones.Blue,
        Illus.Arrival to IllusTones.Teal,
    )
    CardFrame {
        CardHeader(icon, AboutCards.ABOUT_TITLE) { PlaneArt() }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AboutCards.ABOUT_POINTS.forEachIndexed { i, (bold, sub) ->
                val (image, tone) = arts[i]
                Row(
                    Modifier.fillMaxWidth().whiteCard().padding(horizontal = 14.dp, vertical = 12.dp).testTag("point-$i"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(illusPanelBrush(tone, rich = true)),
                        contentAlignment = Alignment.Center,
                    ) {
                        IllusImage(image, Modifier.size(48.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(words(bold), style = style(21, 27, FontWeight.Bold), color = Tokens.Ink)
                        Text(words(sub), style = style(16, 22, FontWeight.Medium), color = Tokens.InkSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        FootLine(AboutCards.NOT_AFFILIATED)
    }
}

/** 맨 아래 작은 한 줄 (Info 아이콘 + 글) */
@Composable
private fun FootLine(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 18.dp, top = 6.dp).testTag("foot"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(words(text), style = style(15, 20, FontWeight.Medium), color = Tokens.InkSecondary)
    }
}

/** 머리 오른쪽 그림(①): 흰 테두리 원 + 금색 원 + 이륙하는 비행기 — ②의 휴대폰 그림과 짝 */
@Composable
private fun PlaneArt() {
    Box(
        Modifier
            .padding(bottom = 4.dp)
            .size(96.dp)
            .clip(CircleShape)
            .background(Tokens.White12)
            .border(3.dp, Tokens.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Tokens.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FlightTakeoff, contentDescription = null, tint = Tokens.Navy, modifier = Modifier.size(32.dp))
        }
    }
}

/** 머리 오른쪽 그림: 흰 테두리 휴대폰 + 화면 가운데 금색 자물쇠 원 (앱 아이콘의 금색) */
@Composable
private fun PhoneLockArt() {
    Box(Modifier.padding(bottom = 4.dp).size(width = 70.dp, height = 104.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Tokens.White12)
                .border(3.dp, Tokens.Surface, RoundedCornerShape(16.dp)),
        )
        // 위 스피커 자리
        Box(Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(width = 18.dp, height = 4.dp).clip(CircleShape).background(Tokens.Surface))
        Box(Modifier.align(Alignment.Center).size(46.dp).clip(CircleShape).background(Tokens.Gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Tokens.Navy, modifier = Modifier.size(26.dp))
        }
    }
}

/** ② 여권 정보는 이 휴대폰에만 — 가운데 큰 약속(서버에는 여권 정보가 아예 없어요) + 세 줄 + 정직한 각주 */
@Composable
fun PrivacyCardArt(icon: Bitmap) {
    CardFrame {
        CardHeader(icon, AboutCards.PRIVACY_TITLE) { PhoneLockArt() }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 가운데 약속 — 흰 카드 + 2dp Accent 테두리, 서버(구름) 끄기 그림, `아예 없어요`만 Accent
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Tokens.Surface)
                    .border(2.dp, Tokens.Accent, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 13.dp)
                    .testTag("key"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Tokens.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Tokens.Surface, modifier = Modifier.size(34.dp))
                }
                val key = AboutCards.PRIVACY_KEY
                val cut = key.indexOf(AboutCards.PRIVACY_KEY_EMPHASIS)
                Text(
                    buildAnnotatedString {
                        append(words(key.substring(0, cut)))
                        withStyle(SpanStyle(color = Tokens.Accent)) { append(words(AboutCards.PRIVACY_KEY_EMPHASIS)) }
                    },
                    style = style(21, 29, FontWeight.Bold),
                    color = Tokens.Ink,
                    modifier = Modifier.weight(1f),
                )
            }
            val icons = listOf(Icons.Outlined.Lock, Icons.Outlined.GppGood, Icons.Outlined.DeleteOutline)
            Column(
                Modifier.fillMaxWidth().whiteCard().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                AboutCards.PRIVACY_POINTS.forEachIndexed { i, line ->
                    Row(
                        Modifier.testTag("point-$i"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Tokens.SuccessBg), contentAlignment = Alignment.Center) {
                            Icon(icons[i], contentDescription = null, tint = Tokens.SuccessText, modifier = Modifier.size(21.dp))
                        }
                        Text(words(line), style = style(17, 23, FontWeight.SemiBold), color = Tokens.Ink, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        FootLine(AboutCards.PRIVACY_FOOTNOTE)
    }
}

/** 앱 아이콘 (Play 등록 아이콘과 같은 파일) */
internal fun appIcon(): Bitmap = BitmapFactory.decodeFile(File("../design/icons/play-store/readyport_play_512.png").path)!!

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "ko-rKR-w400dp-h500dp-432dpi")
class NoticeCardArtTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(name: String, points: Int, content: @Composable () -> Unit): File {
        rule.setContent { content() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/notice-cards").apply { mkdirs() }
        val file = File(dir, name).also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        assertEquals("$name 크기(4:5)", "1080x1350", "${bmp.width}x${bmp.height}")
        // 모든 줄이 카드 안에 그려지고 맨 아래 한 줄과 겹치지 않는다. 본문이 넘치면 맨 아래 한 줄이 밀려나 높이가 0이 된다
        val foot = rule.onNodeWithTag("foot").getBoundsInRoot()
        assertTrue("$name 맨 아래 한 줄이 밀려남(본문이 넘침): $foot", foot.bottom - foot.top >= 18.dp && foot.bottom <= 500.dp)
        repeat(points) { i ->
            val b = rule.onNodeWithTag("point-$i", useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$name 줄 $i 이 잘림: $b / $foot", b.bottom > b.top && b.bottom <= foot.top)
        }
        return file
    }

    @Test fun aboutCard() {
        val icon = appIcon()
        render(AboutCards.FILE_ABOUT, AboutCards.ABOUT_POINTS.size) { AboutCardArt(icon) }
    }

    @Test fun privacyCard() {
        val icon = appIcon()
        render(AboutCards.FILE_PRIVACY, AboutCards.PRIVACY_POINTS.size) { PrivacyCardArt(icon) }
    }
}

/** 공지 원본이 카드 그림 두 장을 가리키고, 대체 글에 카드 글이 모두 들어 있으며, 커밋한 그림이 있는지 (Hosting 배포본) */
class AboutNoticeTest {
    private val source = File("../notices/notices.json").readText()

    private fun flat(s: String) = s.replace("\n", " ")

    @Test fun altTextCarriesEveryCardLine() {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(source) as kotlinx.serialization.json.JsonObject
        val about = (root["notices"] as kotlinx.serialization.json.JsonArray)
            .map { it as kotlinx.serialization.json.JsonObject }
            .first { (it["id"] as kotlinx.serialization.json.JsonPrimitive).content == "about-readyport" }
        val images = (about["images"] as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
        fun field(o: kotlinx.serialization.json.JsonObject, k: String) = (o[k] as kotlinx.serialization.json.JsonPrimitive).content
        assertEquals("https://readyport-app.web.app/notices/${AboutCards.FILE_ABOUT}", field(images[0], "url"))
        assertEquals("https://readyport-app.web.app/notices/${AboutCards.FILE_PRIVACY}", field(images[1], "url"))
        AboutCards.aboutLines().forEach { assertTrue(it, flat(field(images[0], "alt_ko")).contains(flat(it))) }
        AboutCards.privacyLines().forEach { assertTrue(it, flat(field(images[1], "alt_ko")).contains(flat(it))) }
    }

    @Test fun committedImagesExistAndAreSmall() {
        for (name in listOf(AboutCards.FILE_ABOUT, AboutCards.FILE_PRIVACY)) {
            val f = File("../hosting/public/notices/$name")
            assertTrue("$name 이 없어요 — build/notice-cards 에서 복사", f.isFile)
            assertTrue("$name ${f.length()} bytes — 300KB 이하로", f.length() <= 300_000)
            // PNG 머리(IHDR)의 폭·높이 (안드로이드 없이 읽는다)
            val b = f.readBytes()
            fun int(at: Int) = ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
                ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)
            assertEquals("$name 크기", "1080x1350", "${int(16)}x${int(20)}")
        }
    }
}
