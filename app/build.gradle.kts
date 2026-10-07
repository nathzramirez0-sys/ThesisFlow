import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

val inviteHost: String = providers.gradleProperty("thesisflow.inviteHost").get()
val useEmulators: String = providers.gradleProperty("thesisflow.useEmulators").getOrElse("false")
val emulatorHost: String = providers.gradleProperty("thesisflow.emulatorHost").getOrElse("10.0.2.2")

android {
    namespace = "com.nathzramirez.thesisflow"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nathzramirez.thesisflow"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["inviteHost"] = inviteHost
        buildConfigField("String", "INVITE_HOST", "\"$inviteHost\"")
        // Closest Cloud Functions region to the Philippines.
        buildConfigField("String", "FUNCTIONS_REGION", "\"asia-southeast1\"")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "USE_EMULATORS", useEmulators)
            buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release builds always talk to the real Firebase project.
            buildConfigField("boolean", "USE_EMULATORS", "false")
            buildConfigField("String", "EMULATOR_HOST", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    // Generates the factories that let HiltWorkerFactory build @HiltWorker classes in this module.
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.vico.compose)

    // The push service and notification handling live in the app; the token bookkeeping is in :data.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
