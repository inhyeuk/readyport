package com.readyport.ui

import com.readyport.pack.BundledPacks
import com.readyport.pack.PackKeys
import com.readyport.pack.PackRemote
import com.readyport.pack.PackRepository
import com.readyport.pack.PackVerifier
import com.readyport.ui.country.CountryContent
import com.readyport.ui.country.CountryUi
import com.readyport.ui.home.HomeContent
import com.readyport.ui.home.HomeCountry
import com.readyport.ui.home.HomeUi
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.pack.HelpUi
import com.readyport.ui.present.PresentContent
import com.readyport.ui.present.PresentUi
import com.readyport.ui.trip.TripListContent
import com.readyport.ui.trip.TripListUi
import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.WalletState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDate

/**
 * 저장소에 커밋된 **실제 서명된 내장 팩**(src/main/assets/packs)을, 앱에 내장된 운영 공개키로 읽는다.
 * 네트워크는 항상 실패 → 비행기 모드와 같은 조건.
 */
object TestPacks {
    var remoteCalls = 0
        private set

    val repo = PackRepository(
        bundled = BundledPacks { path -> File("src/main/assets/packs/$path").takeIf { it.isFile }?.readBytes() },
        localDir = Files.createTempDirectory("packs").toFile(),
        remote = PackRemote { remoteCalls++; throw IOException("airplane mode") },
        verifier = PackVerifier(PackKeys.TRUSTED),
        io = Dispatchers.Unconfined,
    )

    val index get() = runBlocking { repo.index()!! }
    val thailand get() = runBlocking { repo.pack("TH")!! }

    fun homeUi() = HomeUi(
        countries = index.value.countries.map { c ->
            val pack = if (c.pack) runBlocking { repo.pack(c.code) }?.value else null
            val visa = pack?.requirements?.firstOrNull { it.nationality == "KR" && it.purpose == "tourism" }
            HomeCountry(
                c.code, c.nameKo, c.nameEn,
                visa = visa,
                hasForm = pack?.requiredForms?.isNotEmpty() == true,
                ready = pack != null,
                sourceName = visa?.let { pack.source(it.source)?.name },
            )
        },
    )

    fun countryUi(code: String = "TH", favorite: Boolean = false) = runBlocking {
        val loaded = repo.pack(code)!!
        CountryUi(
            loaded = loaded,
            favorite = favorite,
            autofillForms = loaded.value.forms.filter { repo.recipe(it.id) != null }.map { it.id }.toSet(),
            returnLinks = index.value.returnLinks,
            returnFacts = index.value.returnFacts,
            indexSources = index.value.sources.associate { it.id to it.name },
        )
    }

    val tdacRecipe get() = runBlocking { repo.recipe("TH_TDAC")!! }

    fun helpUi() = HelpUi(
        countries = index.value.countries.filter { it.pack },
        selected = thailand,
        common = index.value.commonEmergency,
        // 공통 항목의 출처 ID를 이름으로 푼다(첫 출처 이름을 붙이면 다른 출처 이름이 붙을 수 있다 — DESIGN_SPEC 1.2 #1)
        commonSourceName = index.value.commonEmergency.firstOrNull()?.source
            ?.let { id -> index.value.sources.firstOrNull { it.id == id }?.name },
        ttsAvailable = false,
        indexSources = index.value.sources.associate { it.id to it.name },
    )
}

/** Hilt 없이 루트를 띄우는 화면 대역 */
val FakeSlots = ScreenSlots(
    wallet = { onAddPassport, onAddBooking, _, _, _ ->
        WalletContent(
            state = WalletState.Locked(hasData = false),
            deviceSecure = true,
            autoDestroy = true,
            today = LocalDate.of(2026, 9, 28),
            onUnlock = {}, onLock = {}, onReset = {},
            onAddPassport = onAddPassport, onDeletePassport = {},
            onAddBooking = onAddBooking, onDeleteBooking = {},
            onAutoDestroyChange = {},
        )
    },
    home = { actions -> HomeContent(TestPacks.homeUi(), actions, today = LocalDate.of(2026, 9, 28)) },
    country = { code, actions -> CountryContent(TestPacks.countryUi(code), actions) },
    help = { HelpContent(TestPacks.helpUi(), {}, {}, {}) },
    // 내 여행 탭 첫 화면 = 여행 목록(빈 목록 — 저장소 없이 띄운다)
    trips = { onOpen, onAdd, openPast -> TripListContent(TripListUi(loaded = true), onOpen, onAdd, openPast) },
    present = { PresentContent(PresentUi(locked = true), {}, {}, {}, {}) },
)
