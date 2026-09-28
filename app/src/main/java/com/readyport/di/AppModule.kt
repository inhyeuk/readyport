package com.readyport.di

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.readyport.autofill.FieldReporter
import com.readyport.autofill.QueuedFieldReporter
import com.readyport.cloud.CloudSync
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
