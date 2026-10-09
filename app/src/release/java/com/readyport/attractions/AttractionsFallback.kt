package com.readyport.attractions

import android.content.Context

/** release 빌드: 샘플 없음 — 서명본이 없는 나라는 언제나 '곧 추가돼요'. debug 소스셋에 같은 이름의 샘플 구현이 있다 */
object AttractionsFallback {
    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun read(context: Context, country: String): ByteArray? = null
}
