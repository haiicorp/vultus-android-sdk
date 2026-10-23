import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.vultus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vultus"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "VULTUS_LICENSE_CODE", "\"YOUR_LICENSE_CODE\"")
        buildConfigField("String", "VULTUS_HOLDER_KEY", "\"YOUR_HOLDER_KEY\"")
        buildConfigField("String", "VULTUS_CLIENT_KEY", "\"YOUR_CLIENT_KEY\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val useAar = providers.gradleProperty("useAar").map { it.toBoolean() }.orElse(false).get()
// SDK 를 Maven 좌표로 받는다 — 값이 SDK 버전이다(`gradle.properties` 의 `vultusMavenVersion`). 저장소 주소는 `settings.gradle.kts` 에 있다.
val vultusMavenVersion: String? = providers.gradleProperty("vultusMavenVersion").orNull

// Only when the SDK module is part of the build: copy its release AAR into app/libs.
// The standalone sample has no such module and uses the AAR already in app/libs.
if (findProject(":vultus") != null) {
    val vultus = project(":vultus")
    tasks.register<Copy>("copyAarToAppLibs") {
        group = "build"
        description = "Copies the freshly assembled :vultus release AAR into app/libs (dev only)."
        dependsOn(":vultus:assembleRelease")

        from(vultus.layout.buildDirectory.file("outputs/aar/${vultus.name}-release.aar"))
        into(project.layout.projectDirectory.dir("libs"))
        rename { "vultus-release.aar" }
    }
}

dependencies {
    if (vultusMavenVersion != null) {
        // SDK 가 쓰는 라이브러리(CameraX · ML Kit 등)는 SDK 의 POM 을 따라 함께 들어온다.
        implementation("io.haii:vultus-aos:$vultusMavenVersion")
    } else if (useAar) {
        // SDK AAR 은 `app/libs/` 에 둔다. SDK 모듈이 함께 빌드되면 그 AAR 을 먼저 복사한다(`copyAarToAppLibs`).
        val aars = fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar")))
        if (findProject(":vultus") != null) {
            aars.builtBy("copyAarToAppLibs")
        }
        implementation(aars)
    } else {
        implementation(project(":vultus"))
    }
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    // ml kit
    implementation(libs.google.mlkit.face.detection)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.camera2)

    // permission
    implementation(libs.google.accompanist.permissions)

    // test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
