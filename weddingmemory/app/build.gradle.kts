import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.weddingmemory.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.weddingmemory.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.weddingmemory.app.HiltTestRunner"

        // Room schema export directory (for migration tracking)
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
                arguments["room.expandProjection"] = "true"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // -------------------------------------------------------------------------
    // AndroidX Core
    // -------------------------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.splashscreen)

    // -------------------------------------------------------------------------
    // Lifecycle (ViewModel + LiveData + Runtime)
    // -------------------------------------------------------------------------
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.common)

    // -------------------------------------------------------------------------
    // Navigation Component
    // -------------------------------------------------------------------------
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // -------------------------------------------------------------------------
    // Hilt — Dependency Injection
    // -------------------------------------------------------------------------
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.fragment)

    // -------------------------------------------------------------------------
    // CameraX
    // -------------------------------------------------------------------------
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.concurrent.futures.ktx)

    // -------------------------------------------------------------------------
    // Media3 / ExoPlayer (HLS-capable)
    // -------------------------------------------------------------------------
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer.hls)

    // -------------------------------------------------------------------------
    // Room — Local Database
    // -------------------------------------------------------------------------
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // -------------------------------------------------------------------------
    // Networking — Retrofit + OkHttp + Moshi
    // -------------------------------------------------------------------------
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    kapt(libs.moshi.kotlin.codegen)

    // -------------------------------------------------------------------------
    // Coroutines
    // -------------------------------------------------------------------------
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)

    // -------------------------------------------------------------------------
    // Image Loading — Coil
    // -------------------------------------------------------------------------
    implementation(libs.coil)

    // -------------------------------------------------------------------------
    // DataStore — Preferences
    // -------------------------------------------------------------------------
    implementation(libs.datastore.preferences)

    // -------------------------------------------------------------------------
    // TensorFlow Lite — Embedding extraction
    // -------------------------------------------------------------------------
    implementation(libs.tflite.core)
    implementation(libs.tflite.support)
    implementation(libs.tflite.metadata)

    // -------------------------------------------------------------------------
    // Logging — Timber
    // -------------------------------------------------------------------------
    implementation(libs.timber)

    // -------------------------------------------------------------------------
    // Unit Tests
    // -------------------------------------------------------------------------
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // -------------------------------------------------------------------------
    // Android Instrumented Tests
    // -------------------------------------------------------------------------
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
}

// Ensure kapt is incremental
kapt {
    correctErrorTypes = true
    useBuildCache = true
}
