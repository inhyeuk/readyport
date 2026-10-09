package com.readyport.ui.attractions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.readyport.R
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.Category
import com.readyport.ui.components.BadgeTone
import com.readyport.ui.components.ComingSoonGroup
import com.readyport.ui.components.Illus
import com.readyport.ui.components.KoText
import com.readyport.ui.components.ListGroup
import com.readyport.ui.components.ListRow
import com.readyport.ui.components.NavTile
import com.readyport.ui.components.SectionHeader
import com.readyport.ui.components.TileGrid
import com.readyport.ui.components.TileSpec
import com.readyport.ui.components.rememberGridColumns
import com.readyport.ui.components.textIconSize
import com.readyport.ui.theme.LocalDimens
import com.readyport.ui.theme.Tokens

/**
 * 나라 화면 › 여행 정보 갈래의 관광지 상태 (§6.1). 한 진입 안에서 NotYet → Available로 바뀌지 않는다(레이아웃 점프 금지 — 부르는 쪽이 고정).
 * 받기(Downloadable·Downloading) 상태는 아직 없다 — 하루 한 번 받기(PackSyncWorker)가 찜한 나라의 새 파일을 받아 두면 다음 진입에 Available.
 */
sealed interface AttractionsEntryUi {
    data class Available(
        /** 타일로 그릴 종류(2곳 이상, 많은 순 최대 5개, §2.2 순서)와 곳 수 */
        val categories: List<Pair<Category, Int>>,
        val total: Int,
        /** 곳이 있는 종류 수 ('모든 종류 보기 (n종류 · m곳)') */
        val kinds: Int,
        val savedCount: Int,
    ) : AttractionsEntryUi

    data object NotYet : AttractionsEntryUi

    companion object {
        fun of(catalog: AttractionsCatalog?, savedCount: Int): AttractionsEntryUi {
            if (catalog == null || catalog.attractions.isEmpty()) return NotYet
            return Available(
                categories = AttractionsListModel.entryCategories(catalog),
                total = catalog.attractions.size,
                kinds = catalog.categoryCounts().size,
                savedCount = savedCount,
            )
        }
    }
}

/** 나라 화면에서 관광지 목록으로 가는 길 (종류 null = 모든 종류) */
fun interface OpenAttractions {
    fun open(category: Category?, focusSearch: Boolean, savedOnly: Boolean)
}

/**
 * 여행 정보 맨 위 '관광지' 묶음 (⟦결정 D2⟧ A): 머리 → 찾기 칸 모양 버튼 → 찜한 관광지 n곳 → 종류 그림 타일.
 * 2열: 종류 타일 k개 + '모든 종류'를 빈 칸 없이(k+1이 짝수면 그리드 마지막 칸, 홀수면 아래 폭 전체 줄).
 * 1열(쉬운 모드·큰 글자): 폭 전체 줄 최대 4개 + '모든 종류 보기 (n종류 · m곳)'.
 */
@Composable
fun AttractionsEntry(ui: AttractionsEntryUi.Available, open: OpenAttractions, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val columns = rememberGridColumns()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.gap)) {
        SectionHeader(
            stringResource(R.string.attractions_title),
            icon = Icons.Outlined.Attractions,
            tone = BadgeTone.Teal,
            subtitle = stringResource(R.string.attractions_entry_desc),
        )
        SearchButton { open.open(null, focusSearch = true, savedOnly = false) }
        if (ui.savedCount > 0 && columns > 1) SavedRow(ui.savedCount, open)
        val tiles = ui.categories.map { (c, n) ->
            TileSpec(
                label = stringResource(c.labelRes()),
                icon = c.icon(),
                onClick = { open.open(c, focusSearch = false, savedOnly = false) },
                supporting = stringResource(R.string.attractions_category_count, n),
                tone = BadgeTone.Teal,
                illustration = CategoryArt.of(c),
            )
        }
        val all = TileSpec(
            label = stringResource(R.string.attractions_all_categories),
            icon = Icons.Outlined.Category,
            onClick = { open.open(null, focusSearch = false, savedOnly = false) },
            supporting = stringResource(R.string.attractions_category_count, ui.total),
            tone = BadgeTone.Teal,
            illustration = Illus.Travel,
        )
        if (columns == 1) {
            tiles.take(SINGLE_COLUMN_MAX).forEach { NavTile(it, row = true) }
            ListGroup {
                ListRow(
                    stringResource(R.string.attractions_all_categories_row, ui.kinds, ui.total),
                    icon = Icons.Outlined.Category,
                    tone = BadgeTone.Teal,
                    onClick = { open.open(null, focusSearch = false, savedOnly = false) },
                )
            }
            if (ui.savedCount > 0) SavedRow(ui.savedCount, open)
        } else if ((tiles.size + 1) % 2 == 0) {
            TileGrid(tiles + all, columns = 2) { spec, cell -> NavTile(spec, modifier = cell) }
        } else {
            if (tiles.isNotEmpty()) TileGrid(tiles, columns = 2) { spec, cell -> NavTile(spec, modifier = cell) }
            NavTile(all, row = true)
        }
    }
}

private const val SINGLE_COLUMN_MAX = 4

@Composable
private fun SavedRow(count: Int, open: OpenAttractions) {
    ListGroup {
        ListRow(
            stringResource(R.string.attractions_saved_row, count),
            icon = Icons.Filled.Favorite,
            tone = BadgeTone.Teal,
            onClick = { open.open(null, focusSearch = false, savedOnly = true) },
        )
    }
}

/** 입력 칸 모양의 버튼(누르면 목록 화면의 검색 칸에 커서) — 이 화면에서 글자를 받지는 않는다 */
@Composable
private fun SearchButton(onClick: () -> Unit) {
    val dimens = LocalDimens.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = Tokens.Surface,
        border = BorderStroke(1.dp, Tokens.LineStrong),
        modifier = Modifier.fillMaxWidth().heightIn(min = dimens.buttonHeight).semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val style = MaterialTheme.typography.bodyLarge
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Tokens.InkSecondary, modifier = Modifier.size(textIconSize(dimens.icon, style)))
            KoText(stringResource(R.string.attractions_search), style, color = Tokens.InkSecondary)
        }
    }
}

/** 아직 싣지 않은 나라: 갈래 맨 끝 '곧 추가돼요' 한 장 */
@Composable
fun AttractionsComingSoon(modifier: Modifier = Modifier) {
    ComingSoonGroup(listOf(Icons.Outlined.Attractions to stringResource(R.string.attractions_coming_soon_item)), modifier)
}
