package com.readyport.ui.components

import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 앱에 넣은 사진(위키미디어 공용, 자유 라이선스). 출처는 assets/photo_credits.json → 설정 › 사진 출처 */
object Photos {
    @DrawableRes val Home = R.drawable.photo_home
    @DrawableRes val Airport = R.drawable.photo_airport
    @DrawableRes val Packing = R.drawable.photo_packing

    /** 베트남 호이안 — 사용 중단(DESIGN_SPEC 3.7 ②). 사용처가 0이 되면 drawable·크레딧과 함께 지운다 */
    @DrawableRes val Market = R.drawable.photo_market

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
        "market" -> Market
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
 */
@Composable
fun PhotoBox(
    @DrawableRes photo: Int?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    minHeight: Dp = 200.dp,
    alignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    PhotoBox(
        painter = photo?.let { painterResource(it) },
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
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(LocalDimens.current.iconSmall))
            Text(text, style = MaterialTheme.typography.labelMedium)
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
        painter = photo?.let { painterResource(it) },
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) Icon(icon, contentDescription = null, tint = OnDark.content, modifier = Modifier.size(dimens.icon))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnDark.content,
                        modifier = Modifier.semantics { heading() },
                    )
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
                    Text(
                        nameKo,
                        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
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
 * 96dp 이하 썸네일(아바타, 사진 출처, 나라 선택)용 축소 디코딩(BitmapFactory inSampleSize) — 메모리 절약.
 * 전체 크기 사진은 화면당 히어로 한 번 + 나라 타일만.
 */
@Composable
fun rememberThumbnail(@DrawableRes res: Int?, sizeDp: Dp): ImageBitmap? {
    val resources = LocalResources.current
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }.coerceAtLeast(1)
    return remember(res, px, resources) {
        if (res == null) return@remember null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, res, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
            BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
    }
}
