pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // usb-serial-for-android 只发布在 jitpack（官方 README 指定）
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "KitchenKDS"
include(":app")
