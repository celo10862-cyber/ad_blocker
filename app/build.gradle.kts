import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystoreFile = rootProject.file("release-keystore.jks")
val releasePropsFile = rootProject.file("release-signing.properties")
val hasAutoReleaseSigning = releaseKeystoreFile.exists() && releasePropsFile.exists()

val releaseProps = Properties().apply {
    if (releasePropsFile.exists()) {
        releasePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.netshield.adblocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.netshield.adblocker"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("autoRelease") {
            if (hasAutoReleaseSigning) {
                storeFile = releaseKeystoreFile
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
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
            if (hasAutoReleaseSigning) {
                signingConfig = signingConfigs.getByName("autoRelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
