package com.readyport.ui.components

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.LruCache
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.get
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 앱에 넣은 사진(위키미디어 공용, 자유 라이선스). 출처는 assets/photo_credits.json → 설정 › 사진 출처 */
object Photos {
    @DrawableRes val Home = R.drawable.photo_home
    @DrawableRes val Airport = R.drawable.photo_airport
    @DrawableRes val Packing = R.drawable.photo_packing

    /** 나라 대표 경치. 사진이 없는 나라는 null → 남색 바탕 */
    @DrawableRes
    fun country(code: String): Int? = when (code) {
        "TH" -> R.drawable.photo_th
        "JP" -> R.drawable.photo_jp
        "SG" -> R.drawable.photo_sg
        "MY" -> R.drawable.photo_my
        "ID" -> R.drawable.photo_id
        else -> null
    }

    /** photo_credits.json의 id(home, th, airport …) → drawable. 모르는 id는 null */
    @DrawableRes
    fun byId(id: String): Int? = when (id) {
        "home" -> Home
        "airport" -> Airport
        "packing" -> Packing
        else -> country(id.uppercase())
    }
}

@Serializable
data class PhotoCredit(
    val id: String,
    val title: String,
    val author: String,
    val license: String,
    @SerialName("license_url") val licenseUrl: String = "",
    @SerialName("source_url") val sourceUrl: String,
    val changes: String = "",
)

private val creditJson = Json { ignoreUnknownKeys = true }

fun loadPhotoCredits(context: Context): List<PhotoCredit> = runCatching {
    context.assets.open("photo_credits.json").use { creditJson.decodeFromString<List<PhotoCredit>>(it.readBytes().decodeToString()) }
}.getOrDefault(emptyList())

/**
 * 사진 틀 v2 (DESIGN_SPEC 3.7): 사진 전체에 검정 0.18 틴트만 깐다. 글자는 꼭 [PhotoTextArea](자체 스크림) 안이나
 * 자체 바탕(PhotoChip, 검정 0.35 원형 버튼) 위에만 둔다 — 0.18 틴트만 있는 윗부분에는 글자를 두지 않는다.
 * 안에서 fillMaxWidth()를 적용한다(호출하는 쪽 modifier와 weight를 써도 폭이 잘리지 않게).
 * 크기는 [content]가 정한다 — 글자를 키우면 사진 칸도 함께 커진다.
 * 사진은 [rememberPhoto]로 그릴 폭(창 폭 × [widthFraction], 2열 타일은 0.5)에 맞춰 줄여 백그라운드에서 디코드·캐시한다(재검토 R10).
 * 어두운 사진(해 질 녘 왓아룬·마리나 베이 등)은 그릴 때만 밝힌다([photoLiftFilter], 재검토 R19 — 사진 파일은 그대로).
 */
@Composable
fun PhotoBox(
    @DrawableRes photo: Int?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    minHeight: Dp = 200.dp,
    alignment: Alignment = Alignment.Center,
    widthFraction: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    PhotoBox(
        painter = rememberPhotoPainter(photo, widthFraction),
        modifier = modifier,
        shape = shape,
        minHeight = minHeight,
        alignment = alignment,
        content = content,
    )
}

/** [painter]로 그리는 사진 틀 (흰 단색 최악 경우 캡처 등). null이면 남색 바탕 */
@Composable
fun PhotoBox(
    painter: Painter?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    minHeight: Dp = 200.dp,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.Navy)
            .heightIn(min = minHeight),
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(Modifier.matchParentSize().background(Tokens.PhotoTint))
        content()
    }
}

/** PhotoTextArea 위쪽의 글자 없는 구간 — 이 구간에서 스크림이 0 → 0.60으로 올라간다 */
private val ScrimRamp = 24.dp

/**
 * 사진 위 글자 영역(Box 아래에 붙음). 영역 자체에 세로 그라데이션(위 24dp는 0→0.60, 그 아래 0.60→0.88)을 깔고
 * 첫 글자 앞 위쪽 여백을 24dp 이상 강제한다 → 글자는 항상 알파 0.60 이상 위(흰 사진 최악에도 흰 글자 5.74:1).
 * 글자가 커져 영역이 커지면 스크림도 함께 커진다.
 */
@Composable
fun BoxScope.PhotoTextArea(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PhotoTextColumn(modifier.align(Alignment.BottomStart), content)
}

/** PhotoTextArea와 같은 스크림 글자 영역 — Box가 아닌 Column 안(사진 위 버튼 줄 아래 등)에서 쓴다 */
@Composable
fun PhotoTextColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dimens = LocalDimens.current
    val rampPx = with(LocalDensity.current) { ScrimRamp.toPx() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val h = size.height
                if (h <= 0f) return@drawBehind
                val ramp = (rampPx / h).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        ramp to Color.Black.copy(alpha = 0.60f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                )
            }
            .padding(start = dimens.cardPadding, end = dimens.cardPadding, top = ScrimRamp + 4.dp, bottom = dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** 사진 위 작은 표시: 흰 92% 바탕 + Ink 글자(검정 위 합성 최악 13.4:1) + 선택 아이콘 */
@Composable
fun PhotoChip(text: String, icon: ImageVector? = null) {
    Surface(color = Tokens.PhotoChipBg, contentColor = Tokens.Ink, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val style = MaterialTheme.typography.labelMedium
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(textIconSize(LocalDimens.current.iconSmall, style)))
            KoText(text, style)
        }
    }
}

/**
 * 사진 머리 + 흰 몸통 카드 (기존 home.PhotoTopCard를 옮김). 제목은 사진 위 스크림 영역 안(heading).
 */
@Composable
fun PhotoHeaderCard(
    @DrawableRes photo: Int?,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    minHeight: Dp = 150.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    PhotoHeaderCard(
        painter = rememberPhotoPainter(photo, 1f),
        title = title,
        modifier = modifier,
        icon = icon,
        minHeight = minHeight,
        content = content,
    )
}

/** [painter]로 그리는 사진 머리 카드 (흰 단색 사진 최악 경우 캡처 등). null이면 남색 바탕 */
@Composable
fun PhotoHeaderCard(
    painter: Painter?,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    minHeight: Dp = 150.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalDimens.current
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.fillMaxWidth().cardShadow(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Tokens.Surface, contentColor = Tokens.Ink),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        PhotoBox(painter, shape = RectangleShape, minHeight = minHeight) {
            PhotoTextArea {
                // 아이콘은 제목 **첫 줄** 가운데에 맞춘다(두 줄 제목의 세로 가운데에 뜨지 않게 — 4.2, BUNDLE_A_NOTES 요청 2)
                val style = MaterialTheme.typography.titleLarge
                val iconSize = textIconSize(dimens.icon, style)
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = OnDark.content,
                            modifier = Modifier.padding(top = firstLineIconOffset(style, iconSize)).size(iconSize),
                        )
                    }
                    KoText(title, style, Modifier.weight(1f, fill = false), color = OnDark.content, heading = true)
                }
            }
        }
        Column(Modifier.padding(dimens.cardPadding), verticalArrangement = Arrangement.spacedBy(dimens.inner), content = content)
    }
}

/** 사진 위 칩 하나. [text]에는 기존 `home_chip_*` 완성 문장을 그대로 넣는다(값·라벨로 쪼개지 않음, D16) */
@Immutable
data class ChipSpec(val icon: ImageVector, val text: String)

/**
 * 나라 사진 타일. 2열이면 minHeight 176·titleLarge·칩 1개, [large](1열)면 200dp(쉬운 모드 220)·displaySmall.
 * TalkBack 이름 = [openLabel](`home_country_open`, 변경 금지), 칩 정보는 stateDescription.
 * mergeDescendants만 쓴다 — clearAndSetSemantics 금지(칩 Text가 트리에 남아야 home_chip_visa_free 테스트 통과).
 * [enabled] = false면(안내 준비 중) 누를 수 없다.
 */
@Composable
fun CountryPhotoTile(
    nameKo: String,
    nameEn: String,
    @DrawableRes photo: Int?,
    chips: List<ChipSpec>,
    onClick: () -> Unit,
    openLabel: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val dimens = LocalDimens.current
    val state = chips.joinToString(", ") { it.text }
    PhotoBox(
        photo,
        minHeight = if (large) (if (dimens.easyMode) 220.dp else 200.dp) else 176.dp,
        widthFraction = if (large) 1f else 0.5f,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = openLabel
                if (state.isNotEmpty()) stateDescription = state
            },
    ) {
        PhotoTextArea {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    KoText(
                        nameKo,
                        if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
                        color = OnDark.content,
                    )
                    Text(
                        nameEn,
                        style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        color = OnDark.content,
                    )
                }
                if (enabled) Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = null, tint = OnDark.content)
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { PhotoChip(it.text, it.icon) }
                }
            }
        }
    }
}

/**
 * 96dp 이하 썸네일(아바타, 사진 출처, 나라 선택)용 축소 디코딩 — [rememberPhoto]와 같은 캐시·백그라운드 디코드(재검토 R10).
 */
@Composable
fun rememberThumbnail(@DrawableRes res: Int?, sizeDp: Dp): ImageBitmap? {
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }.coerceAtLeast(1)
    return rememberPhoto(res, px, square = true)
}

/**
 * [rememberPhoto]를 Painter로 (아직 디코드 전이면 null → 남색 바탕). 어두운 사진은 **그릴 때만** 밝힌다([photoLiftFilter], 재검토 R19 —
 * 사진 파일은 그대로). PhotoBox·PhotoHeaderCard·CountryPhotoTile이 모두 이 길로 그리므로 홈 히어로·나라 타일·나라 히어로·
 * 사진 머리 카드가 같은 보정을 받는다.
 */
@Composable
private fun rememberPhotoPainter(@DrawableRes photo: Int?, widthFraction: Float): Painter? {
    val bitmap = rememberPhoto(photo, photoTargetPx(widthFraction)) ?: return null
    return remember(bitmap) {
        val inner = BitmapPainter(bitmap)
        photoLiftFilter(bitmap)?.let { FilteredPainter(inner, it) } ?: inner
    }
}

// ---------------- 어두운 사진 렌더 보정 (재검토 R19 — 사진 파일은 그대로) ----------------

/** 이 평균 밝기(0~255)보다 어두운 사진만 밝힌다 */
private const val PHOTO_TARGET_LUMA = 128f

/** 가장 어두운 사진에도 이 이상은 밝히지 않는다(하늘·불빛이 하얗게 날아가지 않게) */
private const val PHOTO_MAX_DEFICIT = 0.35f

/**
 * 썸네일(원형 사진 등)에 쓸 밝기 보정 색 필터 — `Image(bitmap, colorFilter = rememberPhotoLift(bitmap))`.
 * 사진 틀(PhotoBox·PhotoHeaderCard·CountryPhotoTile)은 안에서 이미 보정하므로 따로 부르지 않는다.
 */
@Composable
fun rememberPhotoLift(bitmap: ImageBitmap?): ColorFilter? = remember(bitmap) { bitmap?.let(::photoLiftFilter) }

/**
 * 사진 평균 밝기를 격자 몇백 점으로 재서 목표보다 어두우면 밝히는 색 행렬 (밝으면 null).
 * 어두운 정도(0~0.35)에 맞춰 대비를 조금(최대 ×1.21) 올리고 그림자를 조금(최대 +25) 띄운다 — 선형이라 색이 바뀌지 않는다.
 * 글자 대비는 영향받지 않는다: 사진 위 글자는 PhotoTextArea 스크림 위에 있고, 그 대비(5.74:1)는 흰 사진 최악을 가정해 계산했다.
 * (지금 번들 사진 중 홈·일본·말레이시아·싱가포르·태국이 보정 대상 — 사진 출처 화면 설명에 적어 둔다)
 */
internal fun photoLiftFilter(bitmap: ImageBitmap): ColorFilter? {
    val luma = runCatching { meanLuma(bitmap) }.getOrNull() ?: return null
    val deficit = ((PHOTO_TARGET_LUMA - luma) / PHOTO_TARGET_LUMA).coerceIn(0f, PHOTO_MAX_DEFICIT)
    if (deficit < 0.02f) return null
    val gain = 1f + deficit * 0.6f
    val lift = deficit * 70f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                gain, 0f, 0f, 0f, lift,
                0f, gain, 0f, 0f, lift,
                0f, 0f, gain, 0f, lift,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/** 24×16 격자 점의 평균 밝기(0~255, Rec. 601 가중치) */
private fun meanLuma(bitmap: ImageBitmap): Float {
    val bmp = bitmap.asAndroidBitmap()
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return PHOTO_TARGET_LUMA
    var sum = 0f
    var n = 0
    for (gy in 0 until 16) {
        for (gx in 0 until 24) {
            val p = bmp[(gx * 2 + 1) * w / 48, (gy * 2 + 1) * h / 32]
            sum += 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            n++
        }
    }
    return sum / n
}

/** 안쪽 Painter를 색 필터와 함께 그린다 (PhotoBox의 Image는 colorFilter를 받지 않으므로 Painter 쪽에서) */
private class FilteredPainter(private val inner: Painter, private val filter: ColorFilter) : Painter() {
    override val intrinsicSize: Size get() = inner.intrinsicSize

    override fun DrawScope.onDraw() {
        with(inner) { draw(size, colorFilter = filter) }
    }
}

// ---------------- 사진 디코드 (재검토 R10) ----------------

/**
 * 번들 사진 디코드 캐시 (앱 전체 하나, 재검토 R10). painterResource는 컴포지션 중 메인 스레드에서 1080×675 webp를 통째로 디코드하고
 * LazyColumn 항목이 다시 들어올 때마다 다시 디코드했다(홈 8장 ≈ 장당 2.9MB). 이제는
 * ① 그릴 폭에 맞춰 inSampleSize로 줄여(2열 타일은 보통 1/2) ② 백그라운드(IO)에서 디코드하고 ③ (사진, 배율)별로 LruCache에 둔다.
 * 캐시에 있으면 첫 프레임부터 바로 그린다. 디코드가 끝나기 전에는 PhotoBox 바탕(Navy)만 보인다 — 크기는 사진이 아니라 내용이 정하므로 배치가 흔들리지 않는다.
 */
object PhotoCache {
    /** 최대 24MB(또는 앱 힙의 1/8 중 작은 값) — 홈 한 화면 분량의 사진이 들어간다 */
    private val maxKb: Int = (minOf(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8) / 1024).toInt().coerceAtLeast(1024)

    private val bitmaps = object : LruCache<Long, ImageBitmap>(maxKb) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    /** 사진 원본 크기(헤더만 읽음 — 디코드 없음) */
    private val bounds = HashMap<Int, IntArray>()

    /**
     * JVM 테스트(Robolectric)에서는 바로 디코드한다 — 캡처·감사가 백그라운드 디코드를 기다리지 않아 사진 대신 남색 칸이 찍히지 않게.
     * (실기기·에뮬레이터는 언제나 백그라운드)
     */
    internal val decodeImmediately: Boolean = Build.FINGERPRINT == "robolectric"

    private fun key(@DrawableRes res: Int, sample: Int): Long = (res.toLong() shl 8) or sample.toLong()

    /**
     * 그릴 크기에 맞는 inSampleSize (2의 거듭제곱): 결과 폭이 [targetPx] 이상, [square]면 높이도 이상(원형 썸네일로 잘라도 흐리지 않게).
     */
    fun sampleFor(resources: Resources, @DrawableRes res: Int, targetPx: Int, square: Boolean = false): Int {
        val size = synchronized(bounds) {
            bounds.getOrPut(res) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(resources, res, o)
                intArrayOf(o.outWidth, o.outHeight)
            }
        }
        var sample = 1
        if (targetPx > 0) while (size[0] / (sample * 2) >= targetPx && (!square || size[1] / (sample * 2) >= targetPx)) sample *= 2
        return sample
    }

    fun cached(@DrawableRes res: Int, sample: Int): ImageBitmap? = synchronized(bitmaps) { bitmaps.get(key(res, sample)) }

    /** 디코드해서 캐시에 넣는다 (어느 스레드에서나) */
    fun load(resources: Resources, @DrawableRes res: Int, sample: Int): ImageBitmap? {
        cached(res, sample)?.let { return it }
        val bmp = runCatching {
            BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull() ?: return null
        synchronized(bitmaps) { bitmaps.put(key(res, sample), bmp) }
        return bmp
    }
}

/**
 * 번들 사진을 [targetWidthPx] 폭에 맞게 줄여 디코드한 그림 (재검토 R10). 캐시에 있으면 바로, 없으면 백그라운드에서 디코드한 뒤 돌려준다
 * (그동안 null). [targetWidthPx]가 0이면 원본 크기. [square]: 정사각형으로 잘라 쓰는 썸네일(가로·세로 모두 맞춤).
 */
@Composable
fun rememberPhoto(@DrawableRes res: Int?, targetWidthPx: Int, square: Boolean = false): ImageBitmap? {
    if (res == null) return null
    val resources = LocalResources.current
    val sample = remember(res, targetWidthPx, square, resources) { PhotoCache.sampleFor(resources, res, targetWidthPx, square) }
    PhotoCache.cached(res, sample)?.let { return it }
    if (PhotoCache.decodeImmediately) return remember(res, sample, resources) { PhotoCache.load(resources, res, sample) }
    val state = produceState<ImageBitmap?>(initialValue = null, res, sample, resources) {
        value = withContext(Dispatchers.IO) { PhotoCache.load(resources, res, sample) }
    }
    return state.value
}

/** 창 폭 × [fraction](px) — 사진을 그릴 대략의 폭 (2열 타일 0.5) */
@Composable
private fun photoTargetPx(fraction: Float): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width
    return (widthPx * fraction).toInt()
}
