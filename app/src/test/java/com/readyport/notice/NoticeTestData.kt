package com.readyport.notice

import com.google.crypto.tink.subtle.Ed25519Sign
import com.readyport.pack.PackVerifier
import java.util.Base64

/** 공지 테스트 공용: 테스트 전용 Ed25519 키로 서명한 공지 묶음 (운영 키·운영 공지와 무관) */
object NoticeTestData {
    val keys: Ed25519Sign.KeyPair = Ed25519Sign.KeyPair.newKeyPair()
    val parser = NoticeParser(PackVerifier(mapOf("test-1" to keys.publicKey)))

    fun sign(payload: String, kid: String = "test-1", key: ByteArray = keys.privateKey): String {
        val sig = Base64.getEncoder().encodeToString(Ed25519Sign(key).sign(payload.encodeToByteArray()))
        return """{"kid":"$kid","alg":"Ed25519","sig":"$sig"}"""
    }

    fun notice(
        id: String,
        type: String = "normal",
        priority: Int = 0,
        start: String = "2026-10-01T09:00:00+09:00",
        end: String? = "2026-12-31T23:59:00+09:00",
        category: String = "service",
        title: String = if (category == "promo") "(광고) 가을 소식 $id" else "공지 $id",
        body: String = "본문 $id 이에요.",
        extra: String = "",
        version: Int = 1,
        audience: String = "\"all\"",
    ): String = buildString {
        append("""{"id":"$id","version":$version,"type":"$type","category":"$category","title_ko":"$title","body_ko":"$body",""")
        append(""""start":"$start",""")
        if (end != null) append(""""end":"$end",""")
        if (extra.isNotEmpty()) append("$extra,")
        append(""""priority":$priority,"audience":[$audience]}""")
    }

    fun payload(vararg notices: String, generated: String = "2026-10-08T03:00:00Z", schema: Int = 1) =
        """{"schema_version":$schema,"generated_at":"$generated","notices":[${notices.joinToString(",")}]}"""

    fun doc(vararg notices: String, generated: String = "2026-10-08T03:00:00Z"): NoticeDoc {
        val p = payload(*notices, generated = generated)
        return parser.parse(p, sign(p))!!
    }
}
