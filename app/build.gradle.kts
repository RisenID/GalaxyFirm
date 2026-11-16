import java.security.KeyStore

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
}

fun firstAlias(file: File, password: String): String =
    KeyStore.getInstance(KeyStore.getDefaultType()).run {
        file.inputStream().use { load(it, password.toCharArray()) }
        aliases().nextElement()
    }

android {
    namespace = "dev.risenid.galaxyfirm"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.risenid.galaxyfirm"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
    }

    val keystorePath = providers.gradleProperty("TESSERA_KEYSTORE").orNull
    val keystorePassword = providers.gradleProperty("TESSERA_KEYSTORE_PASSWORD").orNull
    if (keystorePath != null && keystorePassword != null) {
        val keystoreFile = file(keystorePath)
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = providers.gradleProperty("TESSERA_KEY_ALIAS").orNull ?: firstAlias(keystoreFile, keystorePassword)
                keyPassword = providers.gradleProperty("TESSERA_KEY_PASSWORD").orNull ?: keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

configurations.all {
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.fragment", module = "fragment")
    exclude(group = "androidx.fragment", module = "fragment-ktx")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
    exclude(group = "com.google.android.material", module = "material")
}

dependencies {
    implementation("io.github.tribalfs:oneui-design:0.9.20+oneui8")
    implementation("io.github.oneuiproject:icons:1.1.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
