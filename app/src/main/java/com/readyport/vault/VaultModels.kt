package com.readyport.vault

import kotlinx.serialization.Serializable

/**
 * 보관함 내용 전체. 한 파일로 암호화해 저장한다.
 * 모든 클래스의 toString은 개인정보를 찍지 않는다 (작업 규칙 3).
 */
@Serializable
data class VaultContents(
    val version: Int = 1,
    val passport: PassportRecord? = null,
    val bookings: List<BookingRecord> = emptyList(),
) {
    override fun toString() = "VaultContents(passport=${passport != null}, bookings=${bookings.size})"
}

@Serializable
data class PassportRecord(
    val surname: String,
    val givenNames: String,
    val documentNumber: String,
    val nationality: String,
    val issuingState: String,
    /** ISO-8601 (yyyy-MM-dd) */
    val birthDate: String,
    val sex: String,
    val expiryDate: String,
    /** "mrz" = 촬영해서 읽음, "manual" = 직접 입력 */
    val source: String,
    /** MRZ 체크디지트를 모두 통과했는지 */
    val mrzVerified: Boolean,
    val savedAt: String,
) {
    override fun toString() = "PassportRecord(source=$source, verified=$mrzVerified)"
}

@Serializable
data class BookingRecord(
    val id: String,
    /** "flight" / "lodging" / "other" */
    val kind: String,
    val title: String,
    val reference: String? = null,
    val flightNumbers: List<String> = emptyList(),
    /** ISO-8601 날짜들 */
    val dates: List<String> = emptyList(),
    val checkIn: String? = null,
    val checkOut: String? = null,
    val savedAt: String,
) {
    override fun toString() = "BookingRecord(kind=$kind)"
}
