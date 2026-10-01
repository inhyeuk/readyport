package com.readyport.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AirplaneTicket
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.NoMeals
import androidx.compose.material.icons.outlined.Outlet
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.ui.graphics.vector.ImageVector
import com.readyport.autofill.ValueOrigin
import com.readyport.prep.ImportStatus

/**
 * 팩·앱의 기존 ID → 아이콘 (DESIGN_SPEC 5장 표의 코드 버전).
 * 팩 스키마에 icon 필드를 넣지 않는다(D11) — 모르는 ID는 정해진 기본 아이콘으로.
 * 스타일: Icons.Outlined.* + 방향 있는 것은 Icons.AutoMirrored.Outlined.* (D12)
 */
object IconKeys {

    /** 여행 단계 6칸: 준비 · 출국 · 도착 · 여행 중 · 귀국 · 정리 (5.2, D14) */
    val stages: List<ImageVector> = listOf(
        Icons.Outlined.Backpack, Icons.Outlined.FlightTakeoff, Icons.Outlined.FlightLand,
        Icons.Outlined.Explore, Icons.Outlined.Cottage, Icons.Outlined.TaskAlt,
    )

    fun stage(index: Int): ImageVector = stages.getOrElse(index) { Icons.Outlined.Info }

    /** 긴급 연락처 emergency.id (5.6). 모르는 ID는 Call */
    fun emergency(id: String): ImageVector = when (id) {
        "tourist_police", "kl_tourist_police", "police" -> Icons.Outlined.LocalPolice
        "medical" -> Icons.Outlined.LocalHospital
        "ambulance", "ambulance2" -> Icons.Outlined.Emergency
        "fire" -> Icons.Outlined.LocalFireDepartment
        "fire_ambulance", "ambulance_fire", "all" -> Icons.Outlined.Sos
        "sea" -> Icons.Outlined.Sailing
        "jnto_hotline", "consular_call_center" -> Icons.Outlined.SupportAgent
        "embassy" -> Icons.Outlined.AccountBalance
        else -> Icons.Outlined.Call
    }

    /** 현지어 문장 phrase.id (5.6). 모르는 ID는 null(아이콘 없음) */
    fun phrase(id: String): ImageVector? = when (id) {
        "restroom" -> Icons.Outlined.Wc
        "lost" -> Icons.Outlined.WrongLocation
        "hospital" -> Icons.Outlined.LocalHospital
        "slowly" -> Icons.Outlined.Hearing
        "address" -> Icons.Outlined.Place
        "police" -> Icons.Outlined.LocalPolice
        "help" -> Icons.Outlined.PanTool
        else -> null
    }

    /** 나라 팩 section.id (5.4). 모르는 ID는 Info */
    fun section(id: String): ImageVector = when (id) {
        "entry" -> Icons.Outlined.FlightLand
        "payment" -> Icons.Outlined.Payments
        "safety" -> Icons.Outlined.GppMaybe
        else -> Icons.Outlined.Info
    }

    /** 꼭 챙길 물건 essentials.id (5.7). 모르는 ID는 Checklist */
    fun essential(id: String): ImageVector = when (id) {
        "passport" -> Icons.Outlined.Badge
        "power_bank" -> Icons.Outlined.BatteryChargingFull
        "plug_adapter" -> Icons.Outlined.Outlet
        "voltage_check" -> Icons.Outlined.ElectricBolt
        "travel_insurance" -> Icons.Outlined.HealthAndSafety
        "payment" -> Icons.Outlined.CreditCard
        "medicine" -> Icons.Outlined.Medication
        else -> Icons.Outlined.Checklist
    }

    /** 쇼핑 분류 category (5.5). null = 전체 */
    fun shoppingCategory(category: String?): ImageVector = when (category) {
        null -> Icons.Outlined.Apps
        "food" -> Icons.Outlined.Restaurant
        "souvenir" -> Icons.Outlined.Redeem
        "daily" -> Icons.Outlined.ShoppingBasket
        else -> Icons.Outlined.Apps
    }

    /** 한국 반입 판정 (5.5) */
    fun importStatus(status: ImportStatus): ImageVector = when (status) {
        ImportStatus.Allowed -> Icons.Outlined.CheckCircle
        ImportStatus.Caution -> Icons.Outlined.ReportProblem
        ImportStatus.Prohibited -> Icons.Outlined.Block
    }

    /**
     * 귀국 전 확인 사실 행: 출처 ID → 아이콘·톤 (4.15). 문장은 팩 원문 그대로 쓰고 값은 앱에 두지 않는다(D11).
     */
    fun returnFact(source: String): Pair<ImageVector, BadgeTone> = when (source) {
        "customs_allowance" -> Icons.Outlined.Savings to BadgeTone.Accent
        "apqa_plant" -> Icons.Outlined.Eco to BadgeTone.Caution
        "apqa_livestock" -> Icons.Outlined.NoMeals to BadgeTone.Danger
        else -> Icons.Outlined.Info to BadgeTone.Neutral
    }

    /** 입국 카드 확인 화면의 출처별 그룹 (5.7) */
    fun formOrigin(origin: ValueOrigin): ImageVector = when (origin) {
        ValueOrigin.Passport -> Icons.Outlined.Badge
        ValueOrigin.Flight -> Icons.AutoMirrored.Outlined.AirplaneTicket
        ValueOrigin.Lodging -> Icons.Outlined.Hotel
        ValueOrigin.User, ValueOrigin.None -> Icons.Outlined.EditNote
    }

    /**
     * 입국 카드 선택지 값 (5.7: 여행 목적·숙소 종류).
     * 매핑 없는 값(MY my_state, stay_type other 등)은 null — 옆 RadioButton과 원이 두 개로 보이지 않게 아이콘을 두지 않는다.
     */
    fun option(value: String): ImageVector? = when (value) {
        "tourism" -> Icons.Outlined.BeachAccess
        "business" -> Icons.Outlined.Work
        "meeting" -> Icons.Outlined.Groups
        "medical" -> Icons.Outlined.LocalHospital
        "education" -> Icons.Outlined.School
        "hotel" -> Icons.Outlined.Hotel
        "guest_house" -> Icons.Outlined.House
        "hostel" -> Icons.Outlined.Bed
        "apartment" -> Icons.Outlined.Apartment
        "friend" -> Icons.Outlined.People
        else -> null
    }

    /** 예약 서류 종류 kind (5.7) */
    fun bookingKind(kind: String): ImageVector = when (kind) {
        "flight" -> Icons.AutoMirrored.Outlined.AirplaneTicket
        "lodging" -> Icons.Outlined.Hotel
        else -> Icons.Outlined.Description
    }

    /** 출처·최종 확인 줄 아이콘 */
    val source: ImageVector get() = Icons.AutoMirrored.Outlined.FactCheck
}
