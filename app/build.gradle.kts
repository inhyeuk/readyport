// 앱 모듈 빌드 스크립트. 라이브러리 버전은 gradle/libs.versions.toml에서만 해석한다.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Firebase 프로젝트 readyport-app (Spark)
    alias(libs.plugins.google.services)
}

// Play 업로드 키: 저장소 밖(~/.readyport/keys/readyport_upload.properties, 또는 환경변수 READYPORT_UPLOAD_PROPS)에만 둔다.
// 파일이 없으면(CI 등) 출시 빌드는 서명 없이 만들어진다. 앱 서명 키는 Google이 관리(Play App Signing).
val uploadProps = Properties().apply {
    val f = file(System.getenv("READYPORT_UPLOAD_PROPS") ?: "${System.getProperty("user.home")}/.readyport/keys/readyport_upload.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

// Google 지도 SDK 키 (사장님이 발급, 사장님 결정 2026-10-09). 저장소에 넣지 않는다 — 아래 순서로 처음 찾은 값을 쓴다.
//   1) gradle 속성 MAPS_API_KEY (-PMAPS_API_KEY=… 또는 ~/.gradle/gradle.properties)
//   2) ~/.readyport/keys/maps.properties 의 MAPS_API_KEY=
//   3) 프로젝트 local.properties 의 MAPS_API_KEY= (git에 안 올라감)
// 비어 있으면 BuildConfig.MAPS_ENABLED=false — 앱이 지도를 그리지 않고 '구글 지도에서 열기' 링크만 보인다.
val mapsApiKey: String = (findProperty("MAPS_API_KEY") as String?)?.trim()?.takeIf { it.isNotEmpty() }
    ?: listOf(
        file("${System.getProperty("user.home")}/.readyport/keys/maps.properties"),
        rootProject.file("local.properties"),
    ).firstNotNullOfOrNull { f ->
        if (!f.isFile) return@firstNotNullOfOrNull null
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("MAPS_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
    }
    ?: ""

android {
    // 패키지명은 Play 출시 후 바꿀 수 없다 (2026-09-28 운영자 확정)
    namespace = "com.readyport"
    // 최신 AndroidX(Compose BOM 2026.09)가 compileSdk 37을 요구한다
    compileSdk = 37

    defaultConfig {
        applicationId = "com.readyport"
        minSdk = 26
        // Play 요구: 2026-08-31부터 신규·업데이트 API 36 이상 (developer.android.com 2026-09-28 확인)
        targetSdk = 36
        versionCode = 9
        versionName = "0.6.1"
        // 지도 키는 매니페스트 com.google.android.geo.API_KEY 로만 들어간다(소스·저장소에 남지 않음)
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "MAPS_ENABLED", mapsApiKey.isNotEmpty().toString())
    }

    signingConfigs {
        if (uploadProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(uploadProps.getProperty("storeFile"))
                storePassword = uploadProps.getProperty("storePassword")
                keyAlias = uploadProps.getProperty("keyAlias")
                keyPassword = uploadProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // Robolectric으로 Compose 화면을 JVM에서 검증한다
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric SDK 36 샌드박스가 JDK 내부 FileDescriptor에 접근한다
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
            // 캡처(captureToImage)를 하드웨어 렌더러로 그려 Modifier.shadow 그림자가 보이게 한다 (DESIGN_SPEC 8장 0단계)
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            // 갤러리 캡처는 긴 화면(쉬운 모드 200%에서 1만 8천 dp)을 한 장으로 이어 붙인다 — 기본 힙으로는 모자란다
            it.maxHeapSize = "2g"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // 위키백과 전체 보기를 앱 안 탭(Custom Tab)으로 — 2026-10-09 사장님 결정
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.biometric)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.korean)

    implementation(libs.tink.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    // 게시판: 익명 로그인 + (운영자가 켰을 때만) 사진·동영상 Storage — 같은 Firebase BoM, 새 네트워크 라이브러리 아님
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    // App Check: 출시 빌드는 Play Integrity, 디버그 빌드는 디버그 공급자 (src/release, src/debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // 관광지 지도 (Google 지도 SDK). 키가 없으면 그리지 않는다 — 위 mapsApiKey
    implementation(libs.maps.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
