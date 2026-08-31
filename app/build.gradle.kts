plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("io.objectbox")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.odys.mototriptracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.odys.mototriptracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        val backendBaseUrl = project.findProperty("BACKEND_BASE_URL") as String? ?: ""
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// Keep Hilt compatible with Kotlin 2.3 metadata.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.android.maps.utils)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hilt/Dagger need this for Kotlin 2.3 metadata (unshaded since Dagger 2.57).
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    implementation(libs.maps.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.splashscreen)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
