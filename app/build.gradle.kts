plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.basel.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.basel.ai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // tasks-genai ships arm64-v8a native libraries only, so the app targets
        // that ABI. Note this means x86_64 emulators are not supported — test
        // on a physical device (which is what you want for LLM timings anyway).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // Static libc++ keeps everything inside libllamabridge.so, so no
                // extra runtime library has to be packaged.
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
                cppFlags += listOf("-O3", "-fexceptions")
            }
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

    // Builds llama.cpp plus the JNI bridge, giving the app GGUF support
    // alongside MediaPipe's .task format.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
            // LiteRT and MediaPipe both carry a TFLite native library. Now
            // that LiteRT is actually packaged, this is what keeps the two
            // copies from failing the build.
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

    // LiteRT / TensorFlow Lite — runs a user-supplied text-to-speech model in
    // ModelTtsSynthesizer.
    //
    // This was compileOnly as a precaution against clashing with the TFLite
    // that MediaPipe links internally. The cost of that caution was the whole
    // feature: nothing was packaged, so isRuntimeAvailable() returned false in
    // every build that shipped and "add a voice model" could not work at all.
    // A precaution that silently removes a feature is worse than the conflict
    // it guards against, so it is now packaged and the duplicate-native-library
    // rule below handles the overlap. If duplicate *classes* appear, exclude
    // them here rather than going back to compileOnly.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    // PDFBox for Android — the text layer and the embedded image objects.
    //
    // Android itself has no API for either: PdfRenderer draws a page and
    // cannot report a single character on it, so reading a PDF means parsing
    // one. This is the maintained port of Apache PDFBox, and text extraction
    // is the part of it this app uses.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    // StringsTest walks every AppStrings property by reflection rather than
    // against a hand-kept list, so an untranslated string cannot slip through.
    // Test-only and version-locked to the Kotlin plugin above: nothing from it
    // reaches the APK, so it can't conflict with anything at runtime.
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
}
