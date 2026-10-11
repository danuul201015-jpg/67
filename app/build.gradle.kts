plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
<<<<<<< HEAD
android {
    namespace = "com.example.autoclicker"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.autoclicker"
        minSdk = 30
=======

android {
    namespace = "com.example.mcmodbuilder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mcmodbuilder"
        minSdk = 26
>>>>>>> dfdf6074b41ac12125ae38f34e37f92dedc06a9b
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
<<<<<<< HEAD
=======

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

>>>>>>> dfdf6074b41ac12125ae38f34e37f92dedc06a9b
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
<<<<<<< HEAD
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
=======
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
>>>>>>> dfdf6074b41ac12125ae38f34e37f92dedc06a9b
}
