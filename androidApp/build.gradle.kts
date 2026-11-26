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
        // Versión 0.<menor>.<compilación>: la menor, a mano en VERSION; la compilación, la del CI (siempre sube)
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1
        versionCode = build
        versionName = "${rootProject.file("VERSION").readText().trim()}.$build"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true  // Robolectric dibuja con los recursos de verdad (fuentes, iconos)
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }  // guardar las capturas, no compararlas
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
    // Las pantallas de verdad dibujadas en la JVM, con capturas, sin emulador (PantallasTest.kt)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.ktor.client.mock)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.health.connect)
    debugImplementation(libs.compose.ui.tooling)
}
