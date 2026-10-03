package com.readyport.trip

import com.readyport.pack.ChecklistTemplateItem
import com.readyport.pack.CountryPack
import com.readyport.pack.EssentialRule
import com.readyport.pack.PackIndex
import com.readyport.pack.PassportValidityRule
import com.readyport.pack.Requirement
import com.readyport.pack.VisaApply
import com.readyport.prep.Essentials
import java.time.LocalDate

/** 항목 종류 — 화면은 종류마다 표시(앱이 확인했어요·출처·내 항목)를 다르게 한다 */
enum class ItemKind { Auto, Pack, Generic, Custom }

/** 앱이 스스로 확인한 결과 (자동 항목만) */
enum class AutoState { Done, NotDone, Unknown }

/** 항목을 누르면 갈 곳 */
enum class ChecklistAction(val key: String) {
    OpenPassport("open_passport"),
    OpenWallet("open_wallet"),
    OpenForm("open_form"),
    OpenHelp("open_help"),
    OpenPresent("open_present"),
    OpenTransport("open_transport"),
    OpenShopping("open_shopping"),
    OpenEssentials("open_essentials"),
    OpenReturn("open_return"),
    DestroyPassport("destroy_passport"),
    OpenLink("open_link"),
    /** 나라 화면 입국·비자의 `공항에 도착하면` 묶음으로 (그 여행의 도착 공항을 골라 둔 채) */
    OpenAirport("open_airport"),
    /** 예약 서류 가져오기 (예약 단계의 집 — 2026-10-03) */
    OpenBooking("open_booking"),
    /** 묵는 곳 (예약 단계의 `묵는 곳` 카드로 — 2026-10-03) */
    OpenStays("open_stays"),
    ;

    companion object {
        fun of(key: String?): ChecklistAction? = entries.firstOrNull { it.key == key }
    }
}

/** 출처 한 줄(이름을 못 찾으면 null — 화면이 `공식 안내`로) */
data class ItemSource(val name: String?, val lastVerified: String)

data class ItemLink(val url: String, val label: String?)

/** 항목별 구조화 값 — 화면이 앱 문구(strings)와 합쳐 그린다 */
sealed interface ItemDetail {
    /**
     * 여권 남은 기간. [saved] = 여권이 저장돼 있는지(모르면 null), [check] = 결과(없으면 아직 모름).
     * [reissue] = 모자랄 때 여는 외교부 여권 재발급 안내(색인 출처), [official] = 기준을 모를 때 여는 외교부 해외안전여행 나라 페이지(팩 출처)
     */
    data class Passport(
        val saved: Boolean?,
        val check: PassportCheck?,
        val rule: PassportValidityRule?,
        val reissue: ItemLink? = null,
        val official: ItemLink? = null,
    ) : ItemDetail

    /** 입국 카드. [windowFrom]~[windowTo] = 내는 기간(팩 일수 + 출발일로 계산), 기간이 없으면 null */
    data class Form(val formId: String, val formName: String, val windowFrom: LocalDate?, val windowTo: LocalDate?, val feeKo: String) : ItemDetail

    data class Visa(val requirement: Requirement, val apply: VisaApply?) : ItemDetail

    data class Phone(val label: String, val number: String) : ItemDetail

    data class Essential(val rule: EssentialRule) : ItemDetail

    /** 귀국 사실(관세청·검역본부) 전체 */
    data class Return(val facts: List<Pair<String, ItemSource>>) : ItemDetail

    /** 여권 정보 지우기 — [hasPassport] = 지울 여권 정보가 있는지(모르면 null) */
    data class Destroy(val hasPassport: Boolean?) : ItemDetail

    data class Offline(val packVersion: String?) : ItemDetail

    /** 도착 공항 순서. [code]·[name] = 이 여행의 도착 공항(고르지 않았으면 null — 화면은 공항을 고르라고 안내) */
    data class AirportGuide(val country: String, val code: String?, val name: String?) : ItemDetail
}

data class ChecklistItem(
    val id: String,
    /**
     * 할 일의 종류(묶는 축, [JourneyStage]). 내 항목이면 null.
     */
    val stage: JourneyStage?,
    /**
     * 언제까지(기한 축, [DueWindow]) — 기한·늦음·알림만 이 값을 쓴다. 내 항목이면 null.
     */
    val due: DueWindow?,
    val kind: ItemKind,
    val icon: String,
    val title: String,
    val body: String? = null,
    val source: ItemSource? = null,
    val checked: Boolean = false,
    /** 자동 항목에서 앱이 본 것 */
    val auto: AutoState? = null,
    /** 사람이 앱 판단과 다르게 정했는지 */
    val overridden: Boolean = false,
    val action: ChecklistAction? = null,
    val link: ItemLink? = null,
    /** 이 날짜 전에는 할 수 없다(입국 카드 기간 등) — 그 전에는 체크할 수 없다 */
    val opensOn: LocalDate? = null,
    /** 이 날짜까지 해 두면 좋다(기한 칸 끝) */
    val dueBy: LocalDate? = null,
    /** 기한이 지났는데 아직 안 했다(부드럽게 표시) */
    val overdue: Boolean = false,
    /** 지금 꼭 해야 한다(출발 당일 입국 카드) — 이것만 빨간색 */
    val urgent: Boolean = false,
    val detail: ItemDetail? = null,
) {
    fun locked(today: LocalDate): Boolean = opensOn != null && today.isBefore(opensOn)
}

/** 한 여행의 체크리스트 */
data class ChecklistData(val items: List<ChecklistItem> = emptyList(), val custom: List<ChecklistItem> = emptyList()) {
    val all: List<ChecklistItem> get() = items + custom
    val total: Int get() = all.size
    val done: Int get() = all.count { it.checked }

    /** 이 단계 항목 (묶는 축) */
    fun stage(stage: JourneyStage): List<ChecklistItem> = items.filter { it.stage == stage }

    /** 항목이 있는 단계만, 여행 순서대로 */
    val stages: List<JourneyStage> get() = JourneyStage.entries.filter { s -> items.any { it.stage == s } }

    /** 이 기한 칸 항목 (알림·기한) */
    fun due(due: DueWindow): List<ChecklistItem> = items.filter { it.due == due }
}

/**
 * 체크리스트 만들기 — 순수 함수(테스트가 나라별로 돌린다).
 * - 틀(index.json `checklist`)의 순서를 따른다. 사실이 필요한 항목은 팩·색인 값에서 가져오고, 값이 없는 나라에서는 만들지 않는다(사실을 지어내지 않는다).
 * - 나라 팩 `checklist` 항목은 그 단계 끝에 붙는다(문장·출처는 팩 섹션 그대로).
 * - 묶는 축은 `stage`([JourneyStage]), 기한 축은 `phase`([DueWindow])다 — 단계 값이 없는 예전 팩은 기한 이름에서 단계를 고른다.
 * - 체크 = 사람이 정한 값([TripChecks.marks]) → 없으면 앱이 확인한 값(자동 항목) → 없으면 안 함.
 */
object Checklist {

    fun essentialItemId(essentialId: String) = "essential.$essentialId"

    fun countryItemId(id: String) = "country.$id"

    data class Input(
        val trip: Trip,
        val index: PackIndex?,
        val pack: CountryPack?,
        val checks: TripChecks = TripChecks(),
        /** 여권이 저장돼 있는지(지갑을 마지막으로 열었을 때). 모르면 null */
        val passportSaved: Boolean? = null,
        val today: LocalDate,
    )

    /** 팩 한 줄의 두 축 */
    private data class Axes(val stage: JourneyStage, val due: DueWindow)

    fun build(input: Input): ChecklistData {
        val trip = input.trip
        val pack = input.pack
        val index = input.index
        val names = index?.sources.orEmpty().associate { it.id to it.name } + pack?.sources.orEmpty().associate { it.id to it.name }
        fun src(id: String?, date: String?) = if (id == null || date == null) null else ItemSource(names[id], date)
        val requirement = pack?.requirements?.firstOrNull { it.nationality == "KR" && it.purpose == "tourism" }
        // 꼭 내야 하는 입국 카드만 할 일로 센다 — 베트남 PAI처럼 의무가 아닌 신고(forms[].optional)는 기한 있는 할 일이 아니다
        val form = pack?.requiredForms?.firstOrNull()
        val essentials = Essentials.select(index?.essentials.orEmpty(), index?.homePower, pack?.power)
        val built = mutableListOf<ChecklistItem>()

        index?.checklist.orEmpty().forEach { t ->
            val axes = axesOf(t.stage, t.phase) ?: return@forEach
            if (t.condition == "has_form" && form == null) return@forEach
            val item = when (t.kind) {
                "essential" -> essentialItem(t, axes, essentials, ::src)
                "auto" -> autoItem(t, axes, input, requirement, form, ::src)
                "pack" -> packItem(t, axes, input, requirement, ::src)
                "generic" -> t.titleKo?.let {
                    ChecklistItem(t.id, axes.stage, axes.due, ItemKind.Generic, t.icon, it, t.bodyKo, action = ChecklistAction.of(t.action))
                }
                else -> null
            } ?: return@forEach
            built += item
        }
        // 나라 팩 항목: 문장·출처는 그 섹션 그대로
        val packSections = pack?.sections.orEmpty()
        pack?.checklist.orEmpty().forEach { c ->
            val axes = axesOf(c.stage, c.phase) ?: return@forEach
            val section = packSections.firstOrNull { it.id == c.section } ?: return@forEach
            if (c.textKo !in section.bodyKo) return@forEach
            built += ChecklistItem(
                countryItemId(c.id), axes.stage, axes.due, ItemKind.Pack, c.icon, c.titleKo, c.textKo,
                src(section.source, section.lastVerified),
            )
        }
        // 단계 순서(단계 안에서는 틀 순서 유지) + 체크·기한 계산
        val ordered = JourneyStage.entries.flatMap { s -> built.filter { it.stage == s } }
            .map { finish(it, trip, input.checks, input.today) }
        val custom = input.checks.custom.map { c ->
            ChecklistItem(c.id, null, null, ItemKind.Custom, "custom", c.text, checked = input.checks.marks[c.id] == true)
        }
        return ChecklistData(ordered, custom)
    }

    /** 팩 두 축 읽기: 기한은 `phase`, 단계는 `stage`(없으면 예전 이름에서). 둘 중 하나라도 모르는 값이면 항목을 만들지 않는다 */
    private fun axesOf(stageKey: String?, dueKey: String?): Axes? {
        val due = DueWindow.of(dueKey) ?: return null
        val stage = JourneyStage.resolve(stageKey, dueKey) ?: return null
        return Axes(stage, due)
    }

    private fun essentialItem(
        t: ChecklistTemplateItem,
        axes: Axes,
        essentials: List<EssentialRule>,
        src: (String?, String?) -> ItemSource?,
    ): ChecklistItem? {
        val id = t.from?.removePrefix("essential:") ?: return null
        val rule = essentials.firstOrNull { it.id == id } ?: return null
        return ChecklistItem(
            id = essentialItemId(id),
            stage = axes.stage,
            due = axes.due,
            kind = if (rule.source != null) ItemKind.Pack else ItemKind.Generic,
            icon = t.icon,
            title = t.titleKo ?: rule.nameKo,
            body = t.bodyKo ?: rule.reasonKo,
            source = src(rule.source, rule.lastVerified),
            action = ChecklistAction.of(t.action),
            link = rule.link?.let { ItemLink(it.url, it.labelKo) },
            detail = ItemDetail.Essential(rule),
        )
    }

    private fun autoItem(
        t: ChecklistTemplateItem,
        axes: Axes,
        input: Input,
        requirement: Requirement?,
        form: com.readyport.pack.FormInfo?,
        src: (String?, String?) -> ItemSource?,
    ): ChecklistItem? {
        val trip = input.trip
        val title = t.titleKo ?: return null
        return when (t.from) {
            "passport_validity" -> {
                val rule = requirement?.passportValidity
                val check = input.checks.passport?.takeIf { PassportValidity.isCurrent(it, trip, rule) }
                val auto = when {
                    input.passportSaved == false -> AutoState.Unknown
                    check?.status == PassportStatus.Ok -> AutoState.Done
                    check?.status == PassportStatus.Short -> AutoState.NotDone
                    else -> AutoState.Unknown
                }
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Auto, t.icon, title, t.bodyKo,
                    source = rule?.let { src(it.source, it.lastVerified) },
                    auto = auto,
                    action = ChecklistAction.of(t.action),
                    detail = ItemDetail.Passport(
                        saved = input.passportSaved,
                        check = if (input.passportSaved == false) null else check,
                        rule = rule,
                        reissue = input.index?.sources?.firstOrNull { it.id == "passport_reissue" }?.let { ItemLink(it.url, it.name) },
                        official = input.pack?.sources?.firstOrNull { it.url.contains("0404.go.kr/ntnSafetyInfo") }?.let { ItemLink(it.url, it.name) },
                    ),
                )
            }
            "passport_saved" -> ChecklistItem(
                t.id, axes.stage, axes.due, ItemKind.Auto, t.icon, title, t.bodyKo,
                auto = when (input.passportSaved) {
                    true -> AutoState.Done
                    false -> AutoState.NotDone
                    null -> AutoState.Unknown
                },
                action = ChecklistAction.of(t.action),
            )
            // 묵는 곳에 주소를 적어 두었는지 (2026-10-03) — 지갑을 열었을 때 본 결과만 쓴다(주소 글자는 장부에 없다)
            "stay_saved" -> ChecklistItem(
                t.id, axes.stage, axes.due, ItemKind.Auto, t.icon, title, t.bodyKo,
                auto = when (input.checks.stayAddress) {
                    true -> AutoState.Done
                    false -> AutoState.NotDone
                    null -> AutoState.Unknown
                },
                action = ChecklistAction.of(t.action),
            )
            "offline_pack" -> {
                val pack = input.pack ?: return null
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Auto, t.icon, title, t.bodyKo,
                    auto = AutoState.Done,
                    detail = ItemDetail.Offline(pack.lastVerified),
                )
            }
            "entry_form" -> {
                val f = form ?: return null
                val days = f.windowDaysIncludingArrival
                // 기간이 정해진 입국 카드는 그 기간이 열리는 날부터 할 수 있다(팩 일수 + 출발일 — 단계·알림과 같은 계산)
                val from = days?.takeIf { it >= 1 }?.let { trip.start.minusDays((it - 1).toLong()) }
                // 기한 축만 기간에 맞춰 옮긴다(묶는 축 = 서류 단계는 그대로) — 늦음·알림 계산이 2026-10-02와 같게
                val due = when {
                    days == null -> axes.due
                    days <= 3 -> DueWindow.ThreeDays
                    days <= 7 -> DueWindow.Week
                    else -> DueWindow.Month
                }
                ChecklistItem(
                    t.id, axes.stage, due, ItemKind.Auto, t.icon, title, f.windowKo,
                    source = src(f.source, f.lastVerified),
                    auto = if (input.checks.formSubmitted == true) AutoState.Done else AutoState.NotDone,
                    action = ChecklistAction.OpenForm,
                    opensOn = from,
                    detail = ItemDetail.Form(f.id, f.nameKo, from, from?.let { trip.start }, f.feeKo),
                )
            }
            "passport_destroyed" -> {
                val ended = input.today.isAfter(trip.end)
                val auto = when {
                    trip.wrappedUp -> AutoState.Done
                    ended && input.passportSaved == false -> AutoState.Done
                    input.passportSaved == null -> AutoState.Unknown
                    else -> AutoState.NotDone
                }
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Auto, t.icon, title, t.bodyKo,
                    auto = auto,
                    action = ChecklistAction.of(t.action),
                    // 여행이 끝나는 날부터 고를 수 있다(여행 중에 지우면 입국 카드·보여 주기에 쓸 여권 정보가 없어진다)
                    opensOn = trip.end,
                    detail = ItemDetail.Destroy(input.passportSaved),
                )
            }
            else -> null
        }
    }

    private fun packItem(
        t: ChecklistTemplateItem,
        axes: Axes,
        input: Input,
        requirement: Requirement?,
        src: (String?, String?) -> ItemSource?,
    ): ChecklistItem? {
        val title = t.titleKo ?: return null
        val pack = input.pack
        val index = input.index
        return when (t.from) {
            "visa" -> {
                val r = requirement ?: return null
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Pack, t.icon, title, r.summaryKo,
                    source = src(r.source, r.lastVerified),
                    link = r.apply?.officialUrl?.let { ItemLink(it, r.apply.nameKo) },
                    action = if (r.apply?.officialUrl != null) ChecklistAction.OpenLink else null,
                    detail = ItemDetail.Visa(r, r.apply),
                )
            }
            "advisory" -> {
                // 외교부 해외안전여행(0404) 나라 페이지가 출처에 있을 때만 — 그 링크로 단계를 다시 본다
                val mofa = pack?.sources?.firstOrNull { it.url.contains("0404.go.kr/ntnSafetyInfo") } ?: return null
                val date = pack.sections.firstOrNull { it.source == mofa.id }?.lastVerified ?: pack.lastVerified
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Pack, t.icon, title, t.bodyKo,
                    source = ItemSource(mofa.name, date),
                    link = ItemLink(mofa.url, null),
                    action = ChecklistAction.OpenLink,
                )
            }
            "consular" -> {
                val c = index?.commonEmergency?.firstOrNull { it.id == "consular_call_center" } ?: return null
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Pack, t.icon, title, c.noteKo,
                    source = src(c.source, c.lastVerified),
                    action = ChecklistAction.of(t.action),
                    detail = ItemDetail.Phone(c.labelKo, c.number),
                )
            }
            "emergency" -> {
                val e = pack?.emergency?.firstOrNull() ?: return null
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Pack, t.icon, title, e.noteKo,
                    source = src(e.source, e.lastVerified),
                    action = ChecklistAction.of(t.action),
                    detail = ItemDetail.Phone(e.labelKo, e.number),
                )
            }
            "airports" -> {
                // 팩에 공항 안내가 있을 때만 — 출처는 이 여행의 도착 공항(고르지 않았으면 첫 공항) 안내
                val airports = pack?.airports.orEmpty()
                if (airports.isEmpty()) return null
                val chosen = pack?.airport(input.trip.arrivalAirport)
                val shown = chosen ?: airports.first()
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Pack, t.icon, title, t.bodyKo,
                    source = src(shown.source, shown.lastVerified),
                    action = ChecklistAction.OpenAirport,
                    detail = ItemDetail.AirportGuide(input.trip.country, chosen?.code, chosen?.nameKo),
                )
            }
            "return_facts" -> {
                val facts = index?.returnFacts.orEmpty()
                if (facts.isEmpty()) return null
                val first = facts.first()
                ChecklistItem(
                    t.id, axes.stage, axes.due, ItemKind.Pack, t.icon, title, first.textKo,
                    source = src(first.source, first.lastVerified),
                    action = ChecklistAction.of(t.action),
                    detail = ItemDetail.Return(facts.map { it.textKo to (src(it.source, it.lastVerified) ?: ItemSource(null, it.lastVerified)) }),
                )
            }
            else -> null
        }
    }

    /** 체크(사람 → 앱 → 안 함)·기한·늦음·급함 — 기한 축([ChecklistItem.due])만 본다 */
    private fun finish(item: ChecklistItem, trip: Trip, checks: TripChecks, today: LocalDate): ChecklistItem {
        val mark = checks.marks[item.id]
        val autoChecked = item.auto == AutoState.Done
        val checked = mark ?: autoChecked
        val locked = item.locked(today)
        val due = item.due?.dueDate(trip)
        // 기간이 정해지지 않은 입국 카드(중국·일본처럼 미리 안 내도 되는 것)는 늦음·급함을 붙이지 않는다 — 앱이 필수인지 지어내지 않는다
        val optional = (item.detail as? ItemDetail.Form)?.windowFrom == null && item.detail is ItemDetail.Form
        val overdue = !checked && !locked && !optional && due != null && today.isAfter(due) && item.due.overdueApplies
        val urgent = !checked && !locked && !optional && item.action == ChecklistAction.OpenForm && today == trip.start
        return item.copy(
            checked = checked && !locked,
            overridden = mark != null && item.auto != null && item.auto != AutoState.Unknown && mark != autoChecked,
            dueBy = due,
            overdue = overdue,
            urgent = urgent,
        )
    }

    /** 이 기한 칸 항목을 이 날까지 해 두면 좋다 */
    fun dueBy(due: DueWindow, trip: Trip): LocalDate = due.dueDate(trip)

    /** 오늘 기준 기한 칸 (알림·'지금 챙길 것'이 쓰는 시간 축 — 2026-10-02 규칙 그대로) */
    fun currentDue(trip: Trip, today: LocalDate): DueWindow {
        val daysLeft = trip.start.toEpochDay() - today.toEpochDay()
        return when {
            daysLeft > 7 -> DueWindow.Month
            daysLeft > 3 -> DueWindow.Week
            daysLeft > 0 -> DueWindow.ThreeDays
            today == trip.start -> if (trip.arrivedAt != null) DueWindow.Arrival else DueWindow.DepartureDay
            today.isAfter(trip.end) -> DueWindow.Back
            !today.isBefore(trip.end.minusDays(1)) -> DueWindow.BeforeReturn
            today == trip.start.plusDays(1) -> DueWindow.Arrival
            else -> DueWindow.During
        }
    }

    /**
     * **지금 단계** (묶는 축) — 단계 막대가 가리키고, 트립 화면이 '지금 할 일'로 펼치는 단계.
     * - 떠난 뒤는 날짜가 정한다: 출발 당일 = 출국(도착을 알리면 입국) · 도착 다음 날 = 입국 · 그 뒤 = 여행 중 ·
     *   돌아오기 전날부터·돌아온 뒤 = 복귀.
     * - **떠나기 전**은 '아직 안 끝난 첫 준비 단계'다(계획 → 예약 → 서류 → 짐). 다 했으면 출국.
     *   날짜가 아니라 한 일로 정하므로, 2주 전에 예약을 끝내면 바로 다음 단계로 넘어간다.
     */
    fun currentStage(data: ChecklistData, trip: Trip, today: LocalDate): JourneyStage = when {
        today.isAfter(trip.end) -> JourneyStage.Return
        today.isBefore(trip.start) -> firstOpenPreparation(data, today) ?: JourneyStage.Departure
        today == trip.start -> if (trip.arrivedAt != null) JourneyStage.Arrival else JourneyStage.Departure
        !today.isBefore(trip.end.minusDays(1)) -> JourneyStage.Return
        today == trip.start.plusDays(1) -> JourneyStage.Arrival
        else -> JourneyStage.During
    }

    /** 떠나기 전 네 단계 중 아직 안 한 항목(할 수 있는 것)이 남은 첫 단계 */
    private fun firstOpenPreparation(data: ChecklistData, today: LocalDate): JourneyStage? =
        JourneyStage.Preparation.firstOrNull { s -> data.stage(s).any { !it.checked && !it.locked(today) } }

    /**
     * '지금 챙길 것' — 지금 기한 칸까지의 안 한 항목 중 [limit]개: 급한 것 → 늦은 것 → 기한 순(같으면 목록 순서).
     * 지금 칸까지 다 했으면 다음 칸에서 미리 할 수 있는 것을 보인다. 아직 열리지 않은 항목(입국 카드 기간 전)은 뺀다.
     * (기한 축으로 고른다 — 알림이 고르는 것과 같은 항목)
     */
    fun nowItems(data: ChecklistData, trip: Trip, today: LocalDate, limit: Int = 3): List<ChecklistItem> {
        val current = currentDue(trip, today)
        val open = data.items.filter { !it.checked && !it.locked(today) && it.due != null }
        val due = open.filter { it.due!! <= current }
            .sortedWith(compareBy<ChecklistItem>({ !it.urgent }, { !it.overdue }, { it.due }))
        val ahead = open.filter { it.due!! > current }
        return (due + ahead).take(limit)
    }

    /** 한 단계의 안 한 항목(할 수 있는 것) — 단계 머리의 '지금 할 일' */
    fun stageTodo(data: ChecklistData, stage: JourneyStage, today: LocalDate, limit: Int = 3): List<ChecklistItem> =
        data.stage(stage).filter { !it.checked && !it.locked(today) }
            .sortedWith(compareBy<ChecklistItem>({ !it.urgent }, { !it.overdue }))
            .take(limit)
}
