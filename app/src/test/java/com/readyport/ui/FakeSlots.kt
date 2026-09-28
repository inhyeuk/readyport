package com.readyport.ui

import com.readyport.pack.BundledPacks
import com.readyport.pack.PackKeys
import com.readyport.pack.PackRemote
import com.readyport.pack.PackRepository
import com.readyport.pack.PackVerifier
import com.readyport.ui.pack.CountryRow
import com.readyport.ui.pack.ExploreContent
import com.readyport.ui.pack.ExploreUi
import com.readyport.ui.pack.GuideContent
import com.readyport.ui.pack.HelpContent
import com.readyport.ui.pack.HelpUi
import com.readyport.ui.pack.PackStatus
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

    fun exploreUi(favorites: Set<String> = emptySet()) = ExploreUi(
        rows = index.value.countries.map { c ->
            val loaded = if (c.pack) runBlocking { repo.pack(c.code) } else null
            CountryRow(
                c, c.code in favorites,
                when {
                    !c.pack -> PackStatus.NotReady
                    loaded != null -> PackStatus.Saved(loaded.value.lastVerified)
                    else -> null
                },
            )
        },
    )

    fun helpUi() = HelpUi(
        countries = index.value.countries.filter { it.pack },
        selected = thailand,
        common = index.value.commonEmergency,
        commonSourceName = index.value.sources.firstOrNull()?.name,
        ttsAvailable = false,
    )
}

/** Hilt 없이 루트를 띄우는 화면 대역 */
val FakeSlots = ScreenSlots(
    wallet = { onAddPassport, onAddBooking ->
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
    explore = { onOpenGuide -> ExploreContent(TestPacks.exploreUi(), {}, onOpenGuide, {}) },
    guide = { country -> GuideContent(runBlocking { TestPacks.repo.pack(country)!! }) },
    help = { HelpContent(TestPacks.helpUi(), {}, {}, {}) },
)
