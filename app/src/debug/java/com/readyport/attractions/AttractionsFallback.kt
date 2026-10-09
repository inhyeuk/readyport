package com.readyport.attractions

import android.content.Context

/**
 * debug 빌드 전용: 서명된 관광지 파일이 없는 나라에 화면 확인용 샘플(assets/attractions_samples/<CC>.json, 서명 없음)을 준다.
 * release 소스셋의 같은 이름 객체는 언제나 null이다(샘플은 설치 파일에 들어가지 않는다). AppCheckInstaller와 같은 방식.
 */
object AttractionsFallback {
    fun read(context: Context, country: String): ByteArray? =
        runCatching { context.assets.open("attractions_samples/$country.json").use { it.readBytes() } }.getOrNull()
}
