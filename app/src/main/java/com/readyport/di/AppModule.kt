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
import com.readyport.attractions.AttractionsFallback
import com.readyport.attractions.AttractionsRepository
import com.readyport.attractions.SavedAttractionsRepository
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

/** 여행 계획 요청 — 끝나지 않은 내 요청 id·마지막 확인 시각만(도착 알림용, 이 휴대폰에만) */
private val Context.planStore: DataStore<Preferences> by preferencesDataStore(name = "plan")

/** 공지 '다시 보지 않기' 기록 (id@version·날짜만, 백업 제외 규칙 그대로) */
private val Context.noticeMarksStore: DataStore<Preferences> by preferencesDataStore(name = "notices")

/** 관광지 찜 (키 "<CC>/<id>"·찜한 날만 — 이 휴대폰에만, 백업 규칙이 전체 제외. SPEC_v5 §6.5) */
private val Context.savedAttractionsStore: DataStore<Preferences> by preferencesDataStore(name = "saved_attractions")

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

    /**
     * 관광지 (SPEC_v5 §4.1): 국가 팩과 같은 내장본·받은 본 폴더를 쓰되 **관광지 전용 키로만** 검증한다.
     * 서명본이 없는 나라는 debug 빌드에서만 샘플(AttractionsFallback), release는 '곧 추가돼요'.
     */
    @Provides
    @Singleton
    fun attractionsRepository(@ApplicationContext context: Context): AttractionsRepository = AttractionsRepository(
        bundled = AssetBundledPacks(context.assets),
        localDir = File(context.noBackupFilesDir, "packs"),
        remote = HttpPackRemote(),
        verifier = PackVerifier(PackKeys.ATTRACTIONS),
        fallback = { country -> AttractionsFallback.read(context, country) },
        io = Dispatchers.IO,
    )

    @Provides
    @Singleton
    fun savedAttractions(@ApplicationContext context: Context): SavedAttractionsRepository =
        SavedAttractionsRepository(context.savedAttractionsStore)

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

    /** 여행 계획 요청 (비공개, docs/ARIA_OPS.md 12.11): Firestore plan_requests·plan_quota·plan_results + 게시판과 같은 나이 확인·익명 로그인 */
    @Provides
    @Singleton
    fun planRepository(@ApplicationContext context: Context, board: BoardRepository): com.readyport.plan.PlanRepository =
        com.readyport.plan.PlanRepository(
            backend = com.readyport.plan.FirestorePlanBackend(),
            board = board,
            local = com.readyport.plan.DataStorePlanLocalStore(context.planStore),
        )

    /** 관광지 레디포트 평점·확인 중 표시 (docs/ARIA_OPS.md 12.9·12.10) */
    @Provides
    @Singleton
    fun ratingRepository(board: BoardRepository): com.readyport.attractions.rating.RatingRepository =
        com.readyport.attractions.rating.RatingRepository(com.readyport.attractions.rating.FirestoreRatingBackend(), board)

    /** 관광지 Google 별점 (지도와 같은 키 — 키가 없는 빌드는 꺼진다) */
    @Provides
    @Singleton
    fun googleRatingClient(@ApplicationContext context: Context): com.readyport.attractions.rating.GoogleRatingClient =
        com.readyport.attractions.rating.GoogleRatingClient(
            apiKey = if (com.readyport.BuildConfig.MAPS_ENABLED) com.readyport.BuildConfig.MAPS_API_KEY else "",
            packageName = context.packageName,
            certSha1 = { com.readyport.attractions.rating.AppSigning.sha1(context) },
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
