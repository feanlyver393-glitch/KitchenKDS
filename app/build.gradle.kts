plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kitchenkds"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kitchenkds"
        minSdk = 24
        targetSdk = 34
        // V5.1.0 / versionCode 9：在线更新（OTA）首个构建。
        // 每次发版只改这两个值，GitHub Release 的 tag 用 v<versionName> 即可被 AppUpdater 正确识别。
        versionCode = 10
        versionName = "5.2.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // OTA 需要读 BuildConfig.VERSION_NAME / VERSION_CODE
        buildConfig = true
    }

    androidResources {
        // 提示音必须不被压缩：MediaPlayer 通过 openRawResourceFd 读取时依赖未压缩的 offset/length。
        // （wav/mp3/ogg 本来就在 aapt2 的白名单里，这里显式声明，防止换 AGP 版本后回归）
        noCompress += listOf("wav", "mp3", "ogg", "m4a")
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    // 导出 Room schema，后续加字段做迁移时才有基准可比对
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ── Room 本地数据库（V2 核心）──
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── Preferences DataStore（V3：尺寸 / 主题偏好持久化）──
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // USB 转串口（CH340 / CP210x / PL2303 / FTDI 通用驱动）
    implementation("com.github.mik3y:usb-serial-for-android:3.7.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
