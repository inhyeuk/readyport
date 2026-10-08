package com.readyport.pack

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.readyport.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 찜한 나라의 팩을 받아 둔다 (PRD 5.7, ARCHITECTURE 9.8).
 * Remote Config 버전이 바뀐 것만 받는다(무료 전송 한도 절약, 9.3).
 */
@HiltWorker
class PackSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val packs: PackRepository,
    private val versions: PackVersionSource,
    private val settings: SettingsRepository,
    private val boardReplies: com.readyport.board.BoardReplyCheck,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 게시판 답글 확인 — 챙길 일 알림을 꺼 둔 사람도 하루 한 번(이 작업은 언제나 돈다). 실패해도 팩 받기는 그대로
        runCatching { boardReplies.run() }
        if (!versions.refresh()) return Result.retry()
        var networkError = false
        versions.indexVersion()?.let { if (packs.updateIndex(it) == UpdateResult.NetworkError) networkError = true }
        for (country in settings.current().favorites) {
            val v = versions.packVersion(country) ?: continue
            // 서명·스키마가 틀린 팩은 버리고 다음 주기를 기다린다(재시도해도 같다)
            if (packs.updatePack(country, v) == UpdateResult.NetworkError) networkError = true
            // 그 나라 입국 서류의 자동 입력 레시피도 함께
            for (form in packs.pack(country)?.value?.forms.orEmpty()) {
                val rv = versions.recipeVersion(form.id) ?: continue
                if (packs.updateRecipe(form.id, rv) == UpdateResult.NetworkError) networkError = true
            }
        }
        return if (networkError) Result.retry() else Result.success()
    }
}

object PackSync {
    private const val NOW = "pack-sync-now"
    private const val DAILY = "pack-sync-daily"

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    /** 찜을 바꾸거나 설정을 바꿨을 때. 조건(와이파이)이 맞을 때 바로 받는다 */
    fun requestNow(context: Context, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<PackSyncWorker>()
            .setConstraints(constraints(wifiOnly))
            .addTag(NOW)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** 하루 한 번 새 안내 확인 */
    fun scheduleDaily(context: Context, wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<PackSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints(wifiOnly))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    const val TAG_NOW = NOW
}
