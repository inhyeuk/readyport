package com.readyport.stay

// =====================================================================================
// 숙소 좌표 읽기 (2026-10-03, 다듬기 S2)
//
// 운영자 요청: *"좌표 입력도 넣고 …"*
//
// 사람이 **실제로 붙여 넣는 모양**만 받아들인다. 앱 안에 지도는 없다(지도 SDK·API 키·네트워크 호출 없음) —
// 지도에서 자리를 집고 싶으면 구글 지도에서 링크를 복사해 붙여 넣는다. 화면은 읽어 낸 값을 **그대로 보여 주고**,
// 못 읽으면 비워 둔다. 잘못 읽는 것보다 모르겠다고 말하는 게 낫다(지어내지 않는다).
//
// 받는 모양
//  ① 숫자 두 개:            `37.5665, 126.9780` · `37.5665 126.9780` · `37, 126`
//  ② 방위 글자:              `37.5665° N, 126.9780° E` · `37.5665N 126.9780E` (S·W는 음수)
//  ③ 구글 지도 주소의 `@`:   `https://www.google.com/maps/@37.5665,126.9780,17z`
//  ④ 주소의 물음표 값:       `?q=37.5665,126.9780` · `&query=…` · `&destination=…` · `&ll=…` · `&center=…`
//  ⑤ 구글 지도 긴 주소:      `…!3d37.5665!4d126.9780…`
//
// 안 받는 모양(모르겠다고 말한다): 숫자 하나 · 숫자 셋 이상 · 범위를 넘는 값(위도 ±90, 경도 ±180) ·
// 도분초(`37°33'59"N`) · 그 밖의 글자.
// 이 파일은 순수 함수만 둔다(안드로이드 없이 JVM 테스트).
// =====================================================================================

/** 읽어 낸 좌표 한 쌍 */
data class LatLng(val lat: Double, val lng: Double)

object Coordinates {

    /** 위도 한계 */
    private const val LAT_MAX = 90.0

    /** 경도 한계 */
    private const val LNG_MAX = 180.0

    /** 소수점 여섯 자리(약 11cm) — 더 적어도 의미가 없다 */
    private const val DECIMALS = 6

    /** 부호 없는 십진수 하나 */
    private const val NUM = "[+-]?\\d{1,3}(?:\\.\\d+)?"

    /** `@37.5665,126.9780` (뒤에 `,17z` 같은 꼬리가 붙어도 된다) */
    private val atPattern = Regex("@($NUM)\\s*,\\s*($NUM)")

    /** `!3d37.5665!4d126.9780` */
    private val placePattern = Regex("!3d($NUM)!4d($NUM)")

    /** `q=37.5665,126.9780` · `query=` · `destination=` · `ll=` · `center=` (값이 좌표로 시작할 때만) */
    private val paramPattern = Regex("(?:^|[?&])(?:q|query|destination|ll|center|sll|daddr)=($NUM)\\s*,\\s*($NUM)", RegexOption.IGNORE_CASE)

    /** 숫자(+방위 글자) 하나 */
    private val numberPattern = Regex("([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*°?\\s*([NSEWnsew])?")

    /**
     * 사람이 적거나 붙여 넣은 글자에서 좌표를 읽는다. 못 읽으면 **null**(지어내지 않는다).
     * 읽는 순서: 구글 지도 주소의 틀(`@`·`!3d!4d`·물음표 값) → 그 밖에는 숫자 두 개.
     */
    fun parse(raw: String?): LatLng? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        // 주소 안에는 줌 값(`17z`)·사진 번호 같은 숫자가 더 있어서 '숫자 두 개' 규칙을 쓸 수 없다 — 틀을 먼저 본다
        atPattern.find(text)?.let { return of(it.groupValues[1], it.groupValues[2]) }
        placePattern.find(text)?.let { return of(it.groupValues[1], it.groupValues[2]) }
        paramPattern.find(text)?.let { return of(it.groupValues[1], it.groupValues[2]) }
        // 주소처럼 보이는데 틀을 못 찾았으면 더 뒤지지 않는다(주소 안 숫자를 좌표로 잘못 읽지 않게)
        if (text.contains("://") || text.contains("maps.app.goo.gl") || text.contains("google.")) return null
        val found = numberPattern.findAll(text).toList()
        if (found.size != 2) return null
        // 숫자 말고 다른 글자가 끼어 있으면(주소·메모) 좌표로 보지 않는다 — 쉼표·공백·방위 글자·도 기호만 허용
        val leftover = numberPattern.replace(text, "").filterNot { it.isWhitespace() || it == ',' || it == ';' || it == '(' || it == ')' }
        if (leftover.isNotEmpty()) return null
        val first = signed(found[0].groupValues[1], found[0].groupValues[2])
        val second = signed(found[1].groupValues[1], found[1].groupValues[2])
        // 방위 글자가 있으면 적힌 순서가 뒤바뀌어도 위도·경도를 가릴 수 있다
        val latFirst = found[0].groupValues[2].uppercase() !in setOf("E", "W")
        return if (latFirst) build(first, second) else build(second, first)
    }

    private fun of(lat: String, lng: String): LatLng? = build(lat.toDoubleOrNull(), lng.toDoubleOrNull())

    private fun signed(number: String, hemisphere: String): Double? {
        val v = number.toDoubleOrNull() ?: return null
        return when (hemisphere.uppercase()) {
            "S", "W" -> -v
            else -> v
        }
    }

    private fun build(lat: Double?, lng: Double?): LatLng? {
        if (lat == null || lng == null) return null
        if (!lat.isFinite() || !lng.isFinite()) return null
        if (lat < -LAT_MAX || lat > LAT_MAX || lng < -LNG_MAX || lng > LNG_MAX) return null
        return LatLng(round(lat), round(lng))
    }

    private fun round(v: Double): Double {
        var factor = 1.0
        repeat(DECIMALS) { factor *= 10 }
        return Math.round(v * factor) / factor
    }

    /** 화면·입력칸에 보여 줄 글자 (`37.5665, 126.978`) — 소수점 뒤 쓸모없는 0은 뗀다 */
    fun format(lat: Double, lng: Double): String = "${number(lat)}, ${number(lng)}"

    fun format(coords: LatLng): String = format(coords.lat, coords.lng)

    /** 저장된 좌표를 입력칸 글자로 (둘 중 하나라도 없으면 빈 글자) */
    fun text(lat: Double?, lng: Double?): String = if (lat == null || lng == null) "" else format(lat, lng)

    /** `37.5665` — 정수면 `37` */
    fun number(v: Double): String {
        val s = java.math.BigDecimal(v).setScale(DECIMALS, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
        return s.toPlainString()
    }
}
