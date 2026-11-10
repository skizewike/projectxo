plugins {
    alias(libs.plugins.android.application) apply false
    // Закрепляет версию Kotlin для встроенной поддержки Kotlin в AGP 9 (должна совпадать с плагином Compose).
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
