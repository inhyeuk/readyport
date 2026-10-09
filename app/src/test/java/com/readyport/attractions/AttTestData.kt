package com.readyport.attractions

import com.google.crypto.tink.subtle.Ed25519Sign
import java.io.File
import java.util.Base64

/**
 * 관광지 테스트 픽스처 — 이름·지역·좌표는 모두 가짜(SPEC_v5 §4.9). 실제 관광지 이름을 쓰지 않는다.
 */
object AttTestData {
    fun region(
        id: String,
        order: Int,
        name: String = "가짜 지역 $order",
        kind: String = "base",
        base: List<String> = emptyList(),
        hub: Pair<Double, Double> = 35.0 to 135.0,
        airports: List<String> = emptyList(),
        level: String = "1",
        aliases: List<String> = emptyList(),
        group: String = "가짜 권역",
        watch: List<String> = emptyList(),
        note: String? = null,
    ) = RegionDto(
        id = id, order = order, nameKo = name, nameEn = "Fake $order", aliasesKo = aliases, groupKo = group, kind = kind,
        baseRegions = base, noteKo = note, hub = HubDto("가짜역", hub.first, hub.second, "Q1"), airports = airports,
        advisory = AdvisoryDto(level, "mofa_xx", "2026-10-01"), advisoryWatchKo = watch,
    )

    fun place(
        id: String,
        region: String,
        name: String,
        category: String = "heritage",
        level: String = "1",
        en: String = "Fake Place",
        aliases: List<String> = emptyList(),
        aliasesEn: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        nearest: String? = null,
        rank: Int = 1,
        summary: String = "가짜 장소예요.",
        body: List<String> = listOf("가짜 설명 첫 문장이에요.", "가짜 설명 둘째 문장이에요."),
        tags: List<String> = emptyList(),
        modes: List<String> = listOf("metro"),
        local: String? = null,
        days: List<String> = listOf("mon"),
        status: String = "open",
        facts: String = "facility",
    ) = AttractionDto(
        id = id,
        names = NamesDto(ko = name, en = en, local = local, localShort = local, source = "wd"),
        aliasesKo = aliases, aliasesEn = aliasesEn, mentionsKo = mentions,
        region = region, category = category,
        tags = tags.map { TagDto(it, "official", "2026-10-01") },
        geo = GeoDto("entrance", 35.01, 135.0, "wd", "2026-10-01"),
        access = AccessDto(modes = modes, nearestKo = nearest, source = "official", lastVerified = "2026-10-01"),
        officialUrl = "https://example.org/fake",
        summaryKo = summary, bodyKo = body,
        tipsKo = listOf(SourcedTextDto("가짜 팁이에요.", "official", "2026-10-01")),
        facts = FactsDto(kind = facts, entry = "paid", booking = "none", regularClosed = days, source = "official", lastVerified = "2026-10-01"),
        status = StatusDto(status, null, "official", "2026-10-01"),
        advisory = AdvisoryDto(level, "mofa_xx", "2026-10-01"),
        rank = RankDto(rank),
        source = "wd", lastVerified = "2026-10-01",
    )

    fun doc(
        regions: List<RegionDto>,
        attractions: List<AttractionDto>,
        country: String = "XX",
        version: String = "2026.10.09-1",
        release: String = "published",
        upcoming: List<NamedAreaDto> = emptyList(),
        excluded: List<ExcludedAreaDto> = emptyList(),
        retired: List<RetiredDto> = emptyList(),
        compact: Boolean = false,
        unmapped: List<UnmappedAirportDto> = emptyList(),
        basis: AdvisoryBasisDto? = null,
        schema: Int = 1,
        sample: Boolean = false,
    ) = AttractionsDoc(
        docType = "attractions", schemaVersion = schema, country = country, version = version, release = release, sample = sample,
        compactCountry = compact, advisoryBasis = basis, unmappedAirports = unmapped, upcomingRegions = upcoming,
        excludedAreas = excluded, regions = regions, attractions = attractions, retired = retired,
        sources = listOf(AttractionSourceDto("wd", "Wikidata", "https://www.wikidata.org/", "skeleton", "CC0")),
    )

    fun bytes(doc: AttractionsDoc): ByteArray = AttractionsJson.encodeToString(AttractionsDoc.serializer(), doc).encodeToByteArray()

    fun catalog(doc: AttractionsDoc): AttractionsCatalog = AttractionsMapper.map(doc)!!

    /** 기본 픽스처: base 2곳 + daytrip 1곳, 종류 셋 */
    fun basic(): AttractionsDoc = doc(
        regions = listOf(
            region("xx_one", 10, name = "가나시", aliases = listOf("가나 시티"), airports = listOf("AAA")),
            region("xx_two", 20, name = "마바시", airports = listOf("BBB"), hub = 36.0 to 136.0),
            region("xx_day", 30, name = "사아섬", kind = "daytrip", base = listOf("xx_one"), note = "가나시에서 하루 다녀오는 곳"),
        ),
        attractions = listOf(
            place("fake-temple", "xx_one", "샘플 사원", aliases = listOf("새벽 절"), en = "Sample Temple", nearest = "가나중앙역", rank = 1),
            place("fake-market", "xx_one", "가짜 야시장", category = "market_street", en = "Fake Night Market", rank = 2),
            place("fake-hill", "xx_two", "다라원", category = "nature", en = "Dara Garden", rank = 1, mentions = listOf("무명봉")),
            place("fake-park", "xx_two", "달기 공원", category = "theme_park", en = "Dalgi Park", rank = 2, tags = listOf("hot_spring")),
            place("fake-isle", "xx_day", "사아 해변", category = "sea_island", en = "Saa Beach", modes = listOf("boat")),
        ),
        upcoming = listOf(NamedAreaDto("나중시", listOf("나중 시티"))),
        excluded = listOf(ExcludedAreaDto("위험섬", emptyList(), "3")),
        unmapped = emptyList(),
    )

    // ---------- 서명 ----------
    val attKeys: Ed25519Sign.KeyPair = Ed25519Sign.KeyPair.newKeyPair()
    val packKeys: Ed25519Sign.KeyPair = Ed25519Sign.KeyPair.newKeyPair()

    fun sign(data: ByteArray, kp: Ed25519Sign.KeyPair = attKeys, kid: String = "rp-att-test-1"): ByteArray {
        val sig = Base64.getEncoder().encodeToString(Ed25519Sign(kp.privateKey).sign(data))
        return """{"kid":"$kid","alg":"Ed25519","sig":"$sig"}""".encodeToByteArray()
    }

    /** debug 샘플(실제 이름 + 화면 확인용 글, 서명 없음) */
    fun debugSampleBytes(country: String = "JP"): ByteArray = File("src/debug/assets/attractions_samples/$country.json").readBytes()

    fun debugSample(country: String = "JP"): AttractionsDoc =
        AttractionsJson.decodeFromString(AttractionsDoc.serializer(), debugSampleBytes(country).decodeToString())
}
