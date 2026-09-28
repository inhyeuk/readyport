// 루트 빌드 스크립트: 플러그인 선언만 담당한다. 버전은 gradle/libs.versions.toml에서만 해석한다.
// AGP 9부터 Kotlin이 내장되어 org.jetbrains.kotlin.android 플러그인을 쓰지 않는다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}
