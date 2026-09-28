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
    /** 입국 서류별 확인한 값·상태. 키 = form_id (예: TH_TDAC) */
    val forms: Map<String, FormRecord> = emptyMap(),
) {
    override fun toString() = "VaultContents(passport=${passport != null}, bookings=${bookings.size}, forms=${forms.keys})"
}

/**
 * 입국 서류 하나의 값. [values]는 사용자가 고르거나 적은 값과, 서류 값을 고친 것(레시피 key → 값).
 * 여권·예약 서류에서 오는 값은 여기에 복사하지 않고 매번 원본에서 가져온다.
 */
@Serializable
data class FormRecord(
    val formId: String,
    val values: Map<String, String> = emptyMap(),
    /** draft / confirmed / submitted */
    val status: String = "draft",
    val updatedAt: String,
    val submittedAt: String? = null,
) {
    override fun toString() = "FormRecord(formId=$formId, status=$status, values=${values.size})"
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
