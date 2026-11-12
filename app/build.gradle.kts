plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.premiumcar.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.premiumcar.launcher"
        // A central se apresenta como Android 13, mas há indícios de que a API real
        // seja bem menor (ver HARDWARE.md). minSdk baixo garante que o APK instale.
        minSdk = 23
        targetSdk = 37
        versionCode = 14
        versionName = "0.4.6"
    }

    buildTypes {
        release {
            // Testes de desempenho devem usar sempre o build otimizado:
            // builds de debug do Compose são muito mais lentos e distorcem as medições.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Fase 1: assinado com a chave de debug desta máquina, apenas para testes.
            // A Fase 6 criará uma chave de release própria (ver BUILD.md).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Necessário para o Robolectric carregar recursos (temas, strings, ícones).
        unitTests.isIncludeAndroidResources = true
    }
}

base {
    archivesName = "nyx-car-launcher"
}

tasks.withType<Test>().configureEach {
    // Roborazzi grava as capturas em app/build/outputs/roborazzi (sem comparação por enquanto).
    systemProperty("roborazzi.test.record", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
