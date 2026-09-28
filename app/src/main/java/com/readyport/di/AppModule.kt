package com.readyport.di

import android.content.Context
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
