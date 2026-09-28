package com.readyport.vault

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import java.io.File

sealed interface WalletState {
    /** 잠김. [hasData]는 파일이 있는지만 본다(복호화 없이) */
    data class Locked(val hasData: Boolean) : WalletState
    data class Unlocked(val contents: VaultContents) : WalletState
    /** 인증이 필요하거나 키가 사라진 경우 */
    data class Failed(val reason: Reason) : WalletState {
        enum class Reason { NeedsAuth, KeyLost, Corrupted }
    }
}

/**
 * 지갑(여권·예약 서류) 보관함. 기기 안 한 파일에 암호화해 저장하고, 서버로 보내지 않는다 (PRD 7.1).
 * 잠금을 풀면 내용을 메모리에만 두고, [lock]하면 메모리에서도 지운다.
 */
class WalletRepository(
    private val cipher: VaultCipher,
    private val file: File,
    private val io: CoroutineDispatcher,
    private val onKeyLost: () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow<WalletState>(WalletState.Locked(hasData = file.exists()))
    val state: StateFlow<WalletState> = _state.asStateFlow()

    val contentsOrNull: VaultContents? get() = (state.value as? WalletState.Unlocked)?.contents

    /** 기기 인증(생체·화면 잠금)을 마친 뒤 부른다 */
    suspend fun unlock(): WalletState = mutex.withLock {
        val next = withContext(io) {
            if (!file.exists()) return@withContext WalletState.Unlocked(VaultContents())
            try {
                val plain = cipher.decrypt(file.readBytes())
                WalletState.Unlocked(json.decodeFromString(VaultContents.serializer(), plain.decodeToString()))
            } catch (e: Exception) {
                classify(e)
            }
        }
        _state.value = next
        next
    }

    fun lock() {
        _state.value = WalletState.Locked(hasData = file.exists())
    }

    enum class SaveResult { Saved, Locked, NeedsAuth, Failed }

    /** 내용을 바꿔 저장한다. 인증 시간이 지났으면 [SaveResult.NeedsAuth] — 메모리 내용은 그대로 둔다 */
    suspend fun update(transform: (VaultContents) -> VaultContents): SaveResult = mutex.withLock {
        val current = (_state.value as? WalletState.Unlocked)?.contents ?: return SaveResult.Locked
        val next = transform(current)
        val result = withContext(io) {
            try {
                val blob = cipher.encrypt(json.encodeToString(VaultContents.serializer(), next).encodeToByteArray())
                file.parentFile?.mkdirs()
                // 쓰다가 꺼져도 기존 파일이 깨지지 않게 임시 파일에 쓰고 바꿔 끼운다
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeBytes(blob)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
                SaveResult.Saved
            } catch (e: UserNotAuthenticatedException) {
                SaveResult.NeedsAuth
            } catch (e: Exception) {
                val failed = classify(e)
                if (failed.reason == WalletState.Failed.Reason.KeyLost) _state.value = failed
                SaveResult.Failed
            }
        }
        if (result == SaveResult.Saved) _state.value = WalletState.Unlocked(next)
        result
    }

    /** 보관함 전체 삭제 (설정의 '전체 삭제', 여행 종료 후 자동 파기). 복호화가 필요 없다 */
    suspend fun wipe() = mutex.withLock {
        withContext(io) {
            file.delete()
            File(file.parentFile, file.name + ".tmp").delete()
        }
        _state.value = WalletState.Locked(hasData = false)
    }

    private fun classify(e: Exception): WalletState.Failed = when (e) {
        is UserNotAuthenticatedException -> WalletState.Failed(WalletState.Failed.Reason.NeedsAuth)
        is KeyPermanentlyInvalidatedException -> {
            // 화면 잠금을 없애는 등으로 키가 사라지면 복구할 방법이 없다. 다시 등록하도록 안내한다
            onKeyLost()
            WalletState.Failed(WalletState.Failed.Reason.KeyLost)
        }
        else -> WalletState.Failed(WalletState.Failed.Reason.Corrupted)
    }
}
