import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Ключ подписи для Google Play лежит только на компьютере владельца: app/keystore.properties
// (storeFile, storePassword, keyAlias, keyPassword). В репозиторий не попадает (.gitignore).
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

room {
    // Схемы базы сохраняются в репозиторий: по ним пишутся и проверяются миграции.
    schemaDirectory("$projectDir/schemas")
}

android {
    // Язык выбирается в приложении: Play должен отдавать все языки сразу, а не по языку телефона.
    bundle {
        language {
            enableSplit = false
        }
    }

    namespace = "com.projectxo.app"
    compileSdk = 37

    defaultConfig {
        // Временный идентификатор. До первой загрузки в Google Play заменить (docs/DECISIONS.md, В3).
        applicationId = "com.projectxo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.6.3"
        // Адрес общего посредника «Еды по фото» (server/food-proxy, Р70). Пусто: работает только свой ключ пользователя.
        val proxy = (project.findProperty("foodAiProxyUrl") as String?).orEmpty().trim()
        buildConfigField("String", "FOOD_AI_PROXY_URL", "\"$proxy\"")
        // Адрес сервера общих привычек (server/habit-rooms, Р79). Пусто: раздел «Вместе» скрыт.
        val rooms = (project.findProperty("sharedRoomsUrl") as String?).orEmpty().trim()
        buildConfigField("String", "SHARED_ROOMS_URL", "\"$rooms\"")
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // Оптимизация размера (Р80): в APK только русский и английский (строки библиотек на 80+ языках не нужны,
    // интерфейс всё равно на двух), без служебных файлов лицензий и отладки корутин.
    androidResources {
        localeFilters += listOf("ru", "en")
    }
    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "/META-INF/*.kotlin_module",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
        )
    }
    // Сведения о зависимостях нужны только Google Play; в APK для тестировщиков их не кладём.
    dependenciesInfo {
        includeInApk = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Робоэлектрик-тесты тяжёлые: стандартных 512 МБ уже не хватает.
        unitTests.all { it.maxHeapSize = "2g" }
    }

    // Схемы базы нужны тесту миграции: Robolectric читает assets отладочной сборки (в выпуск не попадают).
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
