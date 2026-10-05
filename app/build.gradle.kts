import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Credenciais de my.telegram.org: variáveis de ambiente (CI) ou local.properties (fora do git).
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun credencial(ambiente: String, propriedade: String, padrao: String): String =
    System.getenv(ambiente)?.takeIf { it.isNotBlank() } ?: local.getProperty(propriedade)?.takeIf { it.isNotBlank() } ?: padrao

android {
    namespace = "org.danielmarques.teletv"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.danielmarques.teletv"
        minSdk = 25
        targetSdk = 30
        versionCode = 7
        versionName = "0.4.1"
        buildConfigField("int", "TG_API_ID", credencial("TG_API_ID", "tg.apiId", "0"))
        buildConfigField("String", "TG_API_HASH", "\"${credencial("TG_API_HASH", "tg.apiHash", "")}\"")
        buildConfigField("String", "REPO", "\"olegantonov/teletv\"")
        ndk { abiFilters += "armeabi-v7a" }
    }

    signingConfigs {
        create("release") {
            // Preenchido pelo CI a partir dos secrets; ausente em build local comum.
            val loja = System.getenv("KEYSTORE_FILE")
            if (!loja.isNullOrBlank()) {
                storeFile = file(loja)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val temChave = !System.getenv("KEYSTORE_FILE").isNullOrBlank()
            signingConfig = signingConfigs.getByName(if (temChave) "release" else "debug")
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
        buildConfig = true
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("com.google.zxing:core:3.5.3")
}
