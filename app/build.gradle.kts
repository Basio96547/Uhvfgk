plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.ondevicellm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ondevicellm"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // tasks-genai ships arm64-v8a native libraries only, so the app targets
        // that ABI. Note this means x86_64 emulators are not supported — test
        // on a physical device (which is what you want for LLM timings anyway).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Lets unit tests touch classes that reference android.jar stubs
            // (e.g. org.json) without pulling in Robolectric.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Harmless by default (nothing duplicate is packaged). Kept so that
            // switching the LiteRT dependency to `implementation` cannot fail
            // the build on a duplicate native library.
            pickFirsts += listOf(
                "**/libtensorflowlite_jni.so",
                "**/libtensorflowlite_gpu_jni.so",
            )
        }
    }
}

dependencies {
    // Core AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    // Material Components — provides the Theme.Material3.* XML parent theme.
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Resolving display names/sizes for models imported through the file picker.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // MediaPipe LLM Inference (on-device GenAI)
    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    // LiteRT / TensorFlow Lite — used only by ModelTtsSynthesizer to run a
    // user-supplied text-to-speech model.
    //
    // compileOnly on purpose: nothing from this artifact is packaged, so it can
    // never clash with the TFLite runtime that MediaPipe links internally. The
    // app builds and runs with zero conflicts out of the box, and speech output
    // works through the system TTS engine.
    //
    // To run your own TTS model files, change this one word to `implementation`
    // and rebuild. The app detects the runtime at startup and tells you if it
    // is missing instead of crashing.
    compileOnly("org.tensorflow:tensorflow-lite:2.16.1")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
