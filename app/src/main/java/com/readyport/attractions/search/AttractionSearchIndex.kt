package com.readyport.attractions.search

import com.readyport.attractions.Attraction
import com.readyport.attractions.AttractionsCatalog
import com.readyport.attractions.Category
import com.readyport.attractions.CategorySynonyms
import com.readyport.attractions.ExcludedArea
import com.readyport.attractions.ExcludedTopics
import com.readyport.text.KoreanNormalize

/** 결과 줄 아래 '이유 줄'의 종류 (§6.7, 조사 없는 틀) */
enum class ReasonKind { Alias, English, Local, Region, Station, Category, Mention, Body }

/** 이유 줄: [text] 가운데 [bold] 범위를 굵게 */
data class SearchReason(val kind: ReasonKind, val text: String, val bold: IntRange?)

data class SearchHit(val attraction: Attraction, val score: Int, val tiebreak: Double, val reason: SearchReason?)

data class SearchResult(
    val query: String,
    val hits: List<SearchHit>,
    /** 초성으로 찾았는지 — 결과 위에 '초성으로 찾았어요' */
    val chosung: Boolean,
    /** 질의가 종류 이름·동의어와 같으면 그 종류 — '종류: 산·자연으로 보기 ›' */
    val categoryHint: Category?,
    /** 결과가 0이고 안 싣는 것(업체·상품)이면 (낱말, 대신 볼 종류) */
    val excludedTopic: Pair<String, Category?>?,
    /** 아직 싣지 않은 지역 이름 */
    val upcoming: String?,
    /** 여행경보 때문에 싣지 않은 지역 */
    val excludedArea: ExcludedArea?,
)

/**
 * 관광지 검색 (SPEC_v5 §7) — 순수 Kotlin. 나라당 수십~백여 곳이라 키를 칠 때마다 전수 채점해도 1ms 수준이다.
 * 점수: 이름(완전 100·앞 80·중간 60) > 별칭(앞 70·중간 55) > 지역 40 > 가까운 역 35 > 종류 30 > 설명 10.
 * 질의 점수 = max(공백 없앤 전체 질의 점수, 낱말마다 가장 높은 필드 점수의 최소값). 1글자 질의는 이름·종류만. 2자 이상 모두 자음이면 초성 검색.
 * 검색 범위(종류·찜)는 부르는 쪽이 결과에 건다 — 넓혀 찾기 개수를 세려고 여기서는 나라 전체를 채점한다.
 */
class AttractionSearchIndex(private val catalog: AttractionsCatalog) {

    private enum class Kind(val exact: Int, val prefix: Int, val contains: Int, val cho: Boolean) {
        Title(100, 80, 60, true),
        Alias(70, 70, 55, true),
        Region(40, 40, 40, true),
        Access(35, 35, 35, false),
        Category(30, 30, 30, false),
        Body(10, 10, 10, false),
    }

    private class Field(
        val kind: Kind,
        val reason: ReasonKind?,
        val display: String,
        /** 이유 줄 앞말(`종류: `) 없이 굵게 할 글자를 고를 때 쓰는 원문. 지역·역은 이름 그대로 */
        val norm: KoreanNormalize.Normalized,
        val cho: String,
    )

    private class Entry(val attraction: Attraction, val fields: List<Field>)

    private val entries: List<Entry> = catalog.attractions.map { a -> Entry(a, fieldsOf(a)) }

    private val categoryWords: Map<String, Category> = Category.entries.flatMap { c ->
        CategorySynonyms.wordsOf(c).map { KoreanNormalize.norm(it).text to c }
    }.toMap()

    private fun field(kind: Kind, reason: ReasonKind?, text: String): Field {
        val n = KoreanNormalize.norm(text)
        return Field(kind, reason, text, n, KoreanNormalize.chosung(n.text))
    }

    private fun fieldsOf(a: Attraction): List<Field> = buildList {
        add(field(Kind.Title, null, a.nameKo))
        a.koParen?.let { add(field(Kind.Title, null, it)) }
        if (a.nameEn.isNotBlank()) add(field(Kind.Title, ReasonKind.English, a.nameEn))
        a.local?.let { add(field(Kind.Title, ReasonKind.Local, it)) }
        a.localShort?.let { add(field(Kind.Title, ReasonKind.Local, it)) }
        (a.aliasesKo + a.aliasesEn).forEach { add(field(Kind.Alias, ReasonKind.Alias, it)) }
        catalog.region(a.regionId)?.let { r ->
            add(field(Kind.Region, ReasonKind.Region, r.nameKo))
            r.aliasesKo.forEach { add(field(Kind.Region, ReasonKind.Region, it)) }
            if (r.groupKo.isNotBlank()) add(field(Kind.Region, ReasonKind.Region, r.groupKo))
        }
        a.areaKo?.let { add(field(Kind.Region, ReasonKind.Region, it)) }
        a.nearestKo?.let { add(field(Kind.Access, ReasonKind.Station, it)) }
        a.nearestLocal?.let { add(field(Kind.Access, ReasonKind.Station, it)) }
        CategorySynonyms.wordsOf(a.category).forEach { add(field(Kind.Category, ReasonKind.Category, it)) }
        a.tags.forEach { t ->
            CategorySynonyms.tagLabel[t]?.let { add(field(Kind.Category, ReasonKind.Category, it)) }
            CategorySynonyms.tagSynonyms[t].orEmpty().forEach { add(field(Kind.Category, ReasonKind.Category, it)) }
        }
        a.mentionsKo.forEach { add(field(Kind.Body, ReasonKind.Mention, it)) }
        (listOf(a.summaryKo) + a.bodyKo + a.tips.map { it.text }).filter { it.isNotBlank() }
            .forEach { add(field(Kind.Body, ReasonKind.Body, it)) }
    }

    /** 패턴의 한 칸: 그 글자 그대로, 또는 그 초성으로 시작하는 음절 */
    private sealed interface Slot {
        data class Exact(val c: Char) : Slot
        data class Initial(val c: Char) : Slot
    }

    private class Match(val score: Int, val field: Field, val start: Int, val end: Int)

    fun search(rawQuery: String): SearchResult {
        val words = rawQuery.trim().split(Regex("""\s+""")).map { KoreanNormalize.normQuery(it) }.filter { it.isNotEmpty() }
        val whole = words.joinToString("")
        if (whole.isEmpty()) return SearchResult(rawQuery, emptyList(), false, null, null, null, null)
        val chosung = words.all { KoreanNormalize.isAllConsonants(it) }
        val oneChar = whole.length == 1
        val hits = entries.mapNotNull { e ->
            val wholeMatch = best(e, whole, oneChar)
            val perWord = if (words.size > 1) words.map { best(e, it, it.length == 1) } else emptyList()
            val andScore = if (perWord.isNotEmpty() && perWord.all { it != null }) perWord.minOf { it!!.score } else 0
            val wholeScore = wholeMatch?.score ?: 0
            val score = maxOf(wholeScore, andScore)
            if (score <= 0) return@mapNotNull null
            val deciding = if (wholeScore >= andScore) wholeMatch else perWord.filterNotNull().maxByOrNull { it.score }
            val avg = if (perWord.isNotEmpty()) perWord.filterNotNull().map { it.score }.average() else wholeScore.toDouble()
            SearchHit(e.attraction, score, avg, deciding?.let { reasonOf(it) })
        }.sortedWith(compareByDescending<SearchHit> { it.score }.thenByDescending { it.tiebreak }.thenBy { it.attraction.rankOrder })
        val categoryHint = categoryWords[whole]
        val topic = if (hits.isEmpty()) {
            ExcludedTopics.words.entries.firstOrNull { (w, _) ->
                val n = KoreanNormalize.norm(w).text
                whole == n || whole.contains(n)
            }?.let { it.key to it.value }
        } else {
            null
        }
        return SearchResult(rawQuery, hits, chosung && hits.isNotEmpty(), categoryHint, topic, upcomingFor(whole), excludedFor(whole))
    }

    private fun areaMatches(name: String, aliases: List<String>, q: String): Boolean =
        (listOf(name) + aliases).any { n ->
            val norm = KoreanNormalize.norm(n).text
            norm.isNotEmpty() && (norm == q || (q.length >= 2 && norm.startsWith(q)) || q.startsWith(norm))
        }

    private fun upcomingFor(q: String): String? = catalog.upcoming.firstOrNull { areaMatches(it.nameKo, it.aliasesKo, q) }?.nameKo

    private fun excludedFor(q: String): ExcludedArea? = catalog.excluded.firstOrNull { areaMatches(it.nameKo, it.aliasesKo, q) }

    /** 낱말 하나의 가장 높은 필드 일치 */
    private fun best(e: Entry, word: String, oneChar: Boolean): Match? {
        val consonants = KoreanNormalize.isAllConsonants(word)
        val base = patternOf(word)
        var bestMatch: Match? = null
        fun consider(m: Match?) {
            if (m != null && (bestMatch == null || m.score > bestMatch!!.score)) bestMatch = m
        }
        for (f in e.fields) {
            if (oneChar && f.kind != Kind.Title && f.kind != Kind.Category) continue
            if (consonants) {
                if (f.kind.cho) consider(matchChosung(f, word))
            } else {
                consider(matchPattern(f, base))
            }
        }
        if (bestMatch == null && !consonants) {
            // 받침을 다음 음절 초성으로 옮겨 다시: '각' → '가ㄱ', '닭' → '달ㄱ'
            KoreanNormalize.splitLastFinal(word)?.let { (head, moved) ->
                val alt = head.map { Slot.Exact(it) } + Slot.Initial(moved)
                for (f in e.fields) {
                    if (oneChar && f.kind != Kind.Title && f.kind != Kind.Category) continue
                    consider(matchPattern(f, alt))
                }
            }
        }
        return bestMatch
    }

    private fun patternOf(word: String): List<Slot> = word.mapIndexed { i, c ->
        if (i == word.lastIndex && KoreanNormalize.isCompatConsonant(c) && word.length > 1) Slot.Initial(c) else Slot.Exact(c)
    }

    private fun unitMatches(u: Slot, c: Char): Boolean = when (u) {
        is Slot.Exact -> u.c == c
        is Slot.Initial -> c == u.c || KoreanNormalize.initialOf(c) == u.c
    }

    private fun matchPattern(f: Field, pattern: List<Slot>): Match? {
        val t = f.norm.text
        if (pattern.isEmpty() || pattern.size > t.length) return null
        for (start in 0..(t.length - pattern.size)) {
            if (pattern.indices.all { k -> unitMatches(pattern[k], t[start + k]) }) {
                val end = start + pattern.size
                val score = when {
                    start == 0 && end == t.length -> f.kind.exact
                    start == 0 -> f.kind.prefix
                    else -> f.kind.contains
                }
                return Match(score, f, start, end)
            }
        }
        return null
    }

    private fun matchChosung(f: Field, word: String): Match? {
        val i = f.cho.indexOf(word)
        if (i < 0) return null
        val end = i + word.length
        val score = when {
            i == 0 && end == f.cho.length -> f.kind.exact
            i == 0 -> f.kind.prefix
            else -> f.kind.contains
        }
        return Match(score, f, i, end)
    }

    private fun reasonOf(m: Match): SearchReason? {
        val kind = m.field.reason ?: return null
        val src = m.field.norm.sourceRange(m.start, m.end)
        val text = m.field.display
        return if (kind == ReasonKind.Body) {
            // 설명 글은 일치한 곳 앞뒤만 잘라 보인다(말줄임 없이 낱말 경계에서)
            val from = text.lastIndexOf(' ', (src.first - SNIPPET).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
                .coerceAtMost(src.first)
            val to = text.indexOf(' ', (src.last + 1 + SNIPPET).coerceAtMost(text.length)).let { if (it < 0) text.length else it }
                .coerceAtLeast(src.last + 1)
            SearchReason(kind, text.substring(from, to), (src.first - from)..(src.last - from))
        } else {
            SearchReason(kind, text, src)
        }
    }

    private companion object {
        const val SNIPPET = 10
    }
}
