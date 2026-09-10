plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "compass.assistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "compass.assistant"
        minSdk = 25
        targetSdk = 37
        versionCode = 6
        versionName = "1.0"

        testInstrumentationRunner = "compass.assistant.HiltTestRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("qa") {
            dimension = "environment"
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
        }
        create("prod") {
            dimension = "environment"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("KEYSTORE_PATH") ?: "")
            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String?
            keyAlias = project.findProperty("KEY_ALIAS") as String?
            keyPassword = project.findProperty("KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    configurations.all {
        resolutionStrategy {
            force("com.google.ai.edge.litert:litert-support:1.4.2")
            force("com.google.ai.edge.litert:litert-support-api:1.4.2")
            force("com.google.ai.edge.litert:litert-api:1.4.2")
            force("com.google.ai.edge.litert:litert:1.4.2")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // LiteRT
    implementation(libs.litertlm.android)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.tensorflow.lite.support) {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }

    // Coroutines & Lifecycle
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // Hilt Testing
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // MediaPipe for Embeddings
    implementation(libs.mediapipe.tasks.text) {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }
    implementation(libs.mediapipe.tasks.core) {
        exclude(group = "com.google.ai.edge.litert", module = "litert-support-api")
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
    }

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
