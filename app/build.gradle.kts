plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.aiinterviewtrainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aiinterviewtrainer"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // 🌟 수정된 부분: ViewBinding은 buildFeatures 안에서 켭니다!
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Firebase BOM (이게 있으면 밑에 버전 명시 안 해도 됨)
    implementation(platform("com.google.firebase:firebase-bom:32.0.0"))

    // Firebase
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore") // 🌟 수정된 부분: BOM을 따르도록 직접 선언

    // AndroidX & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.foundation.android)

    // Gson & 기타
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.common)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}