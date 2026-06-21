import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.aiinterviewtrainer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.aiinterviewtrainer"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue(
            "string",
            "google_stt_api_key",
            localProperties.getProperty("google.stt.api.key", "")
        )
        resValue(
            "string",
            "gemini_api_key",
            localProperties.getProperty("gemini.api.key", "")
        )
        resValue(
            "string",
            "gemini_model_name",
            localProperties.getProperty("gemini.model.name", "gemini-2.5-flash")
        )
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
    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.0.0"))

    // Firebase
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")

    implementation("org.tensorflow:tensorflow-lite:2.7.0")
    // AndroidX & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
