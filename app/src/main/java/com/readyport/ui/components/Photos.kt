package com.readyport.ui.components

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.ui.theme.Tokens
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 앱에 넣은 사진(위키미디어 공용, 자유 라이선스). 출처는 assets/photo_credits.json → 설정 › 사진 출처 */
object Photos {
    @DrawableRes val Home = R.drawable.photo_home
    @DrawableRes val Airport = R.drawable.photo_airport
    @DrawableRes val Packing = R.drawable.photo_packing
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
 * 사진 위에 글자를 올리는 틀. 아래쪽을 어둡게 덮어 흰 글자가 잘 읽히게 한다(명도 대비).
 * 크기는 [content]가 정한다 — 글자를 키우면 사진 칸도 함께 커진다.
 */
@Composable
fun PhotoBox(
    @DrawableRes photo: Int?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    minHeight: Dp = 200.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(shape).background(Tokens.Navy).heightIn(min = minHeight)) {
        if (photo != null) {
            Image(
                painter = painterResource(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(Modifier.matchParentSize().background(PhotoScrim))
        content()
    }
}

/** 위는 사진이 보이고 아래 글자 자리는 충분히 어둡게 (흰 글자 대비 4.5:1 이상) */
private val PhotoScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.28f),
    0.35f to Color.Black.copy(alpha = 0.42f),
    1f to Color.Black.copy(alpha = 0.86f),
)

/** 사진 위 작은 표시 (흰 바탕 + 진한 글자) */
@Composable
fun PhotoChip(text: String) {
    Surface(color = Color.White.copy(alpha = 0.92f), contentColor = Tokens.Ink, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
