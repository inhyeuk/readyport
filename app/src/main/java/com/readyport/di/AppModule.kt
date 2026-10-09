package com.readyport.di

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.readyport.autofill.FieldReporter
import com.readyport.autofill.QueuedFieldReporter
import com.readyport.cloud.CloudSync
import com.readyport.board.BoardBackend
import com.readyport.board.BoardRepository
import com.readyport.board.DataStoreBoardLocalStore
import com.readyport.board.FirestoreBoardBackend
import com.readyport.ui.video.NetworkThumbnails
import com.readyport.notice.FirestoreNoticeRemote
import com.readyport.notice.NoticeParser
import com.readyport.notice.NoticeRepository
import com.readyport.notice.NoticeStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.readyport.autofill.SafeClipboard
import com.readyport.pack.AssetBundledPacks
import com.readyport.pack.HttpPackRemote
import com.readyport.pack.PackKeys
import com.readyport.pack.PackRepository
import com.readyport.pack.PackVerifier
import com.readyport.pack.PackVersionSource
import com.readyport.pack.RemoteConfigVersions
import com.readyport.vault.AesGcmCipher
import com.readyport.vault.KeystoreKeys
import com.readyport.vault.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

/** 게시판 기록 (규칙 동의·닉네임 사본·차단 목록·답글 확인 시각 — 이 휴대폰에만) */
private val Context.boardStore: DataStore<Preferences> by preferencesDataStore(name = "board")

/** 공지 '다시 보지 않기' 기록 (id@version·날짜만, 백업 제외 규칙 그대로) */
private val Context.noticeMarksStore: DataStore<Preferences> by preferencesDataStore(name = "notices")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun contentResolver(@ApplicationContext context: Context): android.content.ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun packRepository(@ApplicationContext context: Context): PackRepository = PackRepository(
        bundled = AssetBundledPacks(context.assets),
        localDir = File(context.noBackupFilesDir, "packs"),
        remote = HttpPackRemote(),
        verifier = PackVerifier(PackKeys.TRUSTED),
        io = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun packVersions(): PackVersionSource = RemoteConfigVersions(FirebaseRemoteConfig.getInstance())

    @Provides
    @Singleton
    fun queuedFieldReporter(@ApplicationContext context: Context): QueuedFieldReporter =
        QueuedFieldReporter(File(context.noBackupFilesDir, "reports/field_reports.jsonl")) { CloudSync.request(context) }

    @Provides
    @Singleton
    fun fieldReporter(queue: QueuedFieldReporter): FieldReporter = queue

    /** 공지사항: Firestore notices/current(서명) → 기기 안 사본 noBackupFilesDir/notices/ (docs/NOTICES_PUSH.md) */
    @Provides
    @Singleton
    fun noticeRepository(@ApplicationContext context: Context): NoticeRepository = NoticeRepository(
        remote = FirestoreNoticeRemote(),
        dir = File(context.noBackupFilesDir, "notices"),
        parser = NoticeParser(PackVerifier(PackKeys.TRUSTED)),
        io = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun noticeStore(@ApplicationContext context: Context): NoticeStore = NoticeStore(context.noticeMarksStore)

    /** 게시판 (docs/BOARD.md): Firestore + 익명 로그인 + (켰을 때만) Storage */
    @Provides
    @Singleton
    fun boardBackend(): BoardBackend = FirestoreBoardBackend()

    @Provides
    @Singleton
    fun boardRepository(@ApplicationContext context: Context, backend: BoardBackend): BoardRepository = BoardRepository(
        backend = backend,
        local = DataStoreBoardLocalStore(context.boardStore),
        // 사진·동영상 올리기가 켜졌을 때만 Storage 그림 주소를 불러오기 허용 목록에 넣는다
        onMediaEnabled = { NetworkThumbnails.boardMediaEnabled = it },
        // 여권 보관함 파일(WalletRepository와 같은 자리) — 있으면 나이를 알기 전까지 '확인 필요'
        walletHasData = { File(context.noBackupFilesDir, "vault/vault.bin").exists() },
    )

    @Provides
    @Singleton
    fun safeClipboard(@ApplicationContext context: Context) = SafeClipboard(context)

    @Provides
    @Singleton
    fun keystoreKeys(@ApplicationContext context: Context) = KeystoreKeys(context)

    @Provides
    @Singleton
    fun walletRepository(@ApplicationContext context: Context, keys: KeystoreKeys): WalletRepository =
        WalletRepository(
            cipher = AesGcmCipher(keys::getOrCreate),
            // noBackupFilesDir: 자동 백업 대상이 아닌 폴더 (백업 규칙과 이중으로 막는다)
            file = File(context.noBackupFilesDir, "vault/vault.bin"),
            io = Dispatchers.IO,
            onKeyLost = keys::deleteKey,
        )
}
