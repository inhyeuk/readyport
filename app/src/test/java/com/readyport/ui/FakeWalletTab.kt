package com.readyport.ui

import com.readyport.ui.wallet.WalletContent
import com.readyport.vault.WalletState
import java.time.LocalDate

/** Hilt 없이 지갑 탭을 띄우기 위한 대역: 잠긴 빈 지갑 */
val FakeWalletTab: WalletTabSlot = { onAddPassport, onAddBooking ->
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
}
