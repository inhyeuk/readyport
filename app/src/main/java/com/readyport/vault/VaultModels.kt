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
    /** 가족 모드: 보호자 폰이 대신 관리하는 동행자 (PRD 3.3) */
    val companions: List<TravelCompanion> = emptyList(),
    /** 받은 입국 서류(QR·확인서 화면). 그림은 따로 암호화한 파일(blobId) */
    val entryDocs: List<EntryDoc> = emptyList(),
    /** 묵는 곳 — 날짜별로 여러 곳이 될 수 있다 (2026-10-03). 주소가 있어 예약 서류와 같은 암호화 보관함에 둔다 */
    val stays: List<StayRecord> = emptyList(),
) {
    override fun toString() =
        "VaultContents(passport=${passport != null}, bookings=${bookings.size}, forms=${forms.keys}, " +
            "companions=${companions.size}, docs=${entryDocs.size}, stays=${stays.size})"

    /** 여행이 끝나면 여권 정보만 지운다. 받은 서류 그림·예약 서류는 남긴다 (PRD 4.2 ⑧) */
    fun withoutPassportInfo(): VaultContents = copy(
        passport = null,
        companions = companions.map { it.copy(passport = null) },
        forms = emptyMap(),
    )
}

@Serializable
data class TravelCompanion(
    val id: String,
    /** 부르는 이름 (예: 첫째, 어머니). 여권 이름과 따로 */
    val label: String,
    val passport: PassportRecord? = null,
    /** "본인 또는 보호자가 동의했나요?" 확인 (PRD 3.3, 8.2) */
    val consentConfirmed: Boolean,
    val addedAt: String,
) {
    override fun toString() = "TravelCompanion(passport=${passport != null})"
}

@Serializable
data class EntryDoc(
    val id: String,
    val formId: String,
    /** "self" 또는 동행자 id */
    val travelerId: String,
    /** 암호화된 그림 파일 이름 */
    val blobId: String,
    val mimeType: String = "image/png",
    val confirmationNo: String? = null,
    val arrivalDate: String? = null,
    val flightNo: String? = null,
    /** capture = 제출 완료 화면 저장, import = 메일·다른 앱에서 받음 */
    val source: String,
    val savedAt: String,
) {
    override fun toString() = "EntryDoc(formId=$formId, source=$source)"
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

/**
 * 묵는 곳 하나 (2026-10-03 — 운영자 요청 *"날짜별로 숙소가 달라질 수 있으니"*).
 * 한 여행에 0개 이상이고, 밤마다 다른 숙소여도 된다(11/3~11/5 호텔 A, 11/5~11/7 호텔 B).
 * 주소는 개인정보에 가까워 예약 서류와 같은 암호화 보관함에만 둔다 — 로그·알림·서버로 나가지 않는다.
 */
@Serializable
data class StayRecord(
    val id: String,
    /** 어느 여행의 숙소인지 ([com.readyport.trip.Trip.id]). 예전 예약 서류에서 옮겨 온 숙소는 null — 날짜가 맞는 여행에서 보인다 */
    val tripId: String? = null,
    val name: String,
    /** 예약 확인서에 적힌 주소 그대로(현지 글자) — 기사님께 보여 주고, 지도에서 찾고, 입국 카드 주소 칸에 넣는다 */
    val addressLocal: String = "",
    /** 그 주소가 어디인지 한국어로 적어 둔 메모 (예: 수쿰윗 11번 골목, BTS 나나역 5분) */
    val addressKo: String? = null,
    /** ISO-8601 (yyyy-MM-dd) */
    val checkIn: String? = null,
    val checkOut: String? = null,
    val reference: String? = null,
    /** 숙소 종류 — 입국 카드 선택지와 같은 값 ([com.readyport.stay.StayType]) */
    val type: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val phone: String? = null,
    val memo: String? = null,
    val savedAt: String,
) {
    /** 개인정보를 찍지 않는다 (작업 규칙 3) */
    override fun toString() = "StayRecord(hasAddress=${addressLocal.isNotBlank()}, hasDates=${checkIn != null || checkOut != null})"
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
