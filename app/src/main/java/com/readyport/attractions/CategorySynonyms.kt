package com.readyport.attractions

/**
 * 종류·태그 낱말 사전 (SPEC_v5 §2.2·§2.4·§2.5) — 검색이 쓴다. 화면 라벨은 res/values/strings_attractions.xml에도 같은 글자로 있고,
 * EnumsContractTest가 둘이 같은지, enums.json의 모든 종류·태그에 라벨이 있는지 대조한다.
 */
object CategorySynonyms {
    /** 종류 라벨(7자 이내, §2.1) */
    val label: Map<Category, String> = mapOf(
        Category.Heritage to "역사·유적",
        Category.Nature to "산·자연",
        Category.SeaIsland to "바다·섬",
        Category.CityView to "도시·전망",
        Category.MarketStreet to "시장·쇼핑거리",
        Category.Museum to "박물관·미술관",
        Category.ThemePark to "테마파크·체험",
    )

    /** 종류 동의어 (§2.5) */
    val synonyms: Map<Category, List<String>> = mapOf(
        Category.Heritage to listOf("유적", "역사", "절", "사원", "성당", "궁", "성", "사적지"),
        Category.Nature to listOf("산", "자연", "폭포", "국립공원", "동굴", "호수", "협곡"),
        Category.SeaIsland to listOf("바다", "해변", "비치", "섬", "호핑"),
        Category.CityView to listOf("전망", "야경", "랜드마크", "전망대"),
        Category.MarketStreet to listOf("쇼핑", "시장", "야시장", "먹거리", "거리"),
        Category.Museum to listOf("박물관", "미술관", "전시", "기념관"),
        Category.ThemePark to listOf("액티비티", "체험", "놀이공원", "테마파크", "동물원", "수족관"),
    )

    /** 태그 라벨 (§2.4). 검색의 '종류' 필드에도 들어간다 */
    val tagLabel: Map<Tag, String> = mapOf(
        Tag.Unesco to "세계유산",
        Tag.Indoor to "실내(비 와도 좋아요)",
        Tag.FreeEntry to "입장 무료",
        Tag.BookingRequired to "예약 필수",
        Tag.DressCode to "복장 규정 있음",
        Tag.Night to "밤에 열어요",
        Tag.Stairs to "계단·오르막 많음",
        Tag.StepFree to "휠체어로 다닐 수 있어요",
        Tag.CableCar to "케이블카 있음",
        Tag.MountainView to "산 위·전망",
        Tag.Seafront to "바다 앞",
        Tag.ForeignerPrice to "외국인 요금 따로",
        Tag.HotSpring to "온천",
    )

    /** 태그 동의어 */
    val tagSynonyms: Map<Tag, List<String>> = mapOf(
        Tag.HotSpring to listOf("온천", "노천탕"),
    )

    /** 종류 하나를 부르는 모든 낱말(라벨 + 동의어) */
    fun wordsOf(category: Category): List<String> = listOf(label.getValue(category)) + synonyms[category].orEmpty()
}

/**
 * 안 싣는 것 사전 (§2.5) — 업체·상품·활동. 검색 결과가 0이고 질의가 이 낱말과 같거나 포함하면 안내한다.
 * 값 = 대신 찾아 볼 종류(없으면 null).
 */
object ExcludedTopics {
    val words: Map<String, Category?> = linkedMapOf(
        "스노클링" to Category.SeaIsland,
        "다이빙" to Category.SeaIsland,
        "서핑" to Category.SeaIsland,
        "호핑투어" to Category.SeaIsland,
        "투어" to null,
        "마사지" to null,
        "맛집" to Category.MarketStreet,
        "식당" to Category.MarketStreet,
    )
}
