plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.brivel.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.brivel"
        minSdk = 28  // Health Connect
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1  // cada versión del CI, mayor que la anterior
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    // El CI firma con una clave fija (secreto de GitHub): si no, cada versión de prueba choca con la anterior al instalarla
    signingConfigs {
        getByName("debug") {
            System.getenv("BRIVEL_KEYSTORE")?.let {
                storeFile = file(it)
                storePassword = System.getenv("BRIVEL_KEYSTORE_PASSWORD")
                keyAlias = "brivel"
                keyPassword = System.getenv("BRIVEL_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // shortcut: firmada con la clave de depuración para probar en el móvil; una clave propia antes de publicar
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    testImplementation(kotlin("test-junit"))  // tests de la JVM de lo que no necesita un móvil (src/test)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.health.connect)
    debugImplementation(libs.compose.ui.tooling)
}
