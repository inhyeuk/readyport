package com.readyport.pack

import com.google.crypto.tink.subtle.Ed25519Verify
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** 서명 파일(<파일>.sig) 형식. tools/packs/build_packs.py 가 만든다 */
@Serializable
data class PackSignature(val kid: String, val alg: String, val sig: String)

/**
 * 국가 팩·인덱스의 Ed25519 서명 검증 (PRD 7.5).
 * 앱에 내장한 공개키로만 검증한다. 검증에 실패한 데이터는 절대 적용하지 않는다.
 */
class PackVerifier(private val trustedKeys: Map<String, ByteArray>) {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: String) : Result
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun verify(data: ByteArray, signatureFile: ByteArray): Result {
        val sig = runCatching { json.decodeFromString(PackSignature.serializer(), signatureFile.decodeToString()) }
            .getOrElse { return Result.Invalid("bad signature file") }
        if (sig.alg != "Ed25519") return Result.Invalid("unsupported alg")
        val key = trustedKeys[sig.kid] ?: return Result.Invalid("unknown kid")
        val bytes = runCatching { Base64.getDecoder().decode(sig.sig) }.getOrElse { return Result.Invalid("bad base64") }
        return try {
            Ed25519Verify(key).verify(bytes, data)
            Result.Valid
        } catch (e: java.security.GeneralSecurityException) {
            Result.Invalid("signature mismatch")
        }
    }
}

/** 앱에 내장한 신뢰 공개키. 키를 바꿀 때는 새 kid를 추가하고, 옛 키는 모든 팩을 다시 서명한 뒤 지운다 */
object PackKeys {
    val TRUSTED: Map<String, ByteArray> = mapOf(
        // 2026-09-28 생성. 비밀키는 운영자 PC ~/.readyport/keys/ 와 (예정) GitHub Actions secret 에만 있다
        "rp-2026-1" to Base64.getDecoder().decode("53StIn9TRFnSws+oKOfqq9D0Qazxxyw0mqlID1xhZpg="),
    )
}
