package com.readyport.prep

import com.readyport.pack.EssentialRule
import com.readyport.pack.PowerInfo
import com.readyport.pack.ShoppingItem

/**
 * 꼭 챙길 물건 고르기 (PRD 5.10·11.2). 순수 함수.
 * 순서는 팩에 적힌 추천 순서 그대로 — 제휴 여부로 바꾸지 않는다.
 */
object Essentials {

    /**
     * 어댑터가 필요한지. 한국 플러그가 맞는다고 확인된 나라만 false.
     * 확인되지 않은 나라(null)는 어댑터를 권한다 — 챙겨 가서 손해 볼 일은 없다(안전한 쪽).
     * 여행지를 모르면 null.
     */
    fun plugDiffers(dest: PowerInfo?): Boolean? {
        if (dest == null) return null
        return dest.krPlugFits != true
    }

    /**
     * 전압 차이가 10%를 넘으면 true (예: 220 V ↔ 100 V). 220 V ↔ 230 V는 같은 계열로 본다(설계 기준).
     * 숫자를 읽지 못하면 null.
     */
    fun voltageDiffers(home: PowerInfo?, dest: PowerInfo?): Boolean? {
        val h = home?.voltage?.let(::volts) ?: return null
        val d = dest?.voltage?.let(::volts) ?: return null
        return kotlin.math.abs(d - h).toDouble() / h > 0.10
    }

    private fun volts(s: String): Int? = Regex("""(\d{2,3})""").find(s)?.groupValues?.get(1)?.toInt()

    /**
     * 여행지에 맞는 항목만. 여행지를 모르면(여행 없음) 조건이 있는 항목은 빼고 'always'만.
     */
    fun select(rules: List<EssentialRule>, home: PowerInfo?, dest: PowerInfo?): List<EssentialRule> = rules.filter { r ->
        when (r.condition) {
            "always" -> true
            "plug_differs" -> plugDiffers(dest) == true
            "voltage_differs" -> voltageDiffers(home, dest) == true
            else -> false
        }
    }

    /** 제휴 링크인지. '제휴' 라벨은 이 경우에만 붙인다 */
    fun isAffiliate(rule: EssentialRule) = rule.link?.type == "affiliate"
}

/** 쇼핑 장바구니 항목 키: "TH/item-id" (DataStore에 저장, 개인정보 아님) */
object CartKey {
    fun of(country: String, itemId: String) = "$country/$itemId"
    fun country(key: String) = key.substringBefore('/')
    fun item(key: String) = key.substringAfter('/')
}

enum class ImportStatus(val code: String) {
    Allowed("allowed"), Caution("caution"), Prohibited("prohibited");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: Caution
    }
}

val ShoppingItem.import: ImportStatus get() = ImportStatus.of(importStatus)
