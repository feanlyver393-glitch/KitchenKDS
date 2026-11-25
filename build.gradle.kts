plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Room 注解处理走 KSP（kapt 在 Kotlin 2.0 已不推荐）
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
