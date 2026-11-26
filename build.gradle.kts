// Apps nativas sin servidor (docs/VISION.md §7): núcleo compartido en Kotlin Multiplatform, Android en Compose, iOS en SwiftUI
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt)
}

// Análisis estático del Kotlin de las apps y el núcleo (./gradlew detekt). Lo que ya había está en la línea base:
// solo falla lo nuevo, y lo arreglado hay que quitarlo de ella (./gradlew detektBaseline la regenera).
detekt {
    source.setFrom("shared/src", "androidApp/src")
    config.setFrom("config/detekt.yml")
    buildUponDefaultConfig = true
    baseline = file("config/detekt-baseline.xml")
    parallel = true
}
