// 루트 빌드 스크립트: 플러그인 선언만 담당한다. 버전은 gradle/libs.versions.toml에서만 해석한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
