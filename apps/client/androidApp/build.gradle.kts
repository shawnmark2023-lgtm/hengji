import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":apps:client"))
    implementation(project(":modules:connectors"))
    implementation(project(":modules:core-data"))
    implementation(project(":modules:core-insights"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.androidx.work.runtime)
    implementation(
        files(
            rootProject.file(
                "third_party/ai/runtime/0.15.0/onnxruntime-genai-android-0.15.0-hengji.aar",
            ),
        ),
    )
    implementation(files(rootProject.file("third_party/ai/runtime/0.15.0/onnxruntime-android-1.26.0.aar")))
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.compose.ui.tooling)
}

android {
    namespace = "com.hengji.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hengji.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += setOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                abiFilters += "arm64-v8a"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    sourceSets.getByName("main").assets.directories.add(
        rootProject.file("third_party/ai/model/common").absolutePath,
    )
    sourceSets.getByName("main").jniLibs.directories.add(
        rootProject.file("third_party/ai/runtime/0.15.0/android-jni").absolutePath,
    )

    androidResources {
        noCompress += setOf("onnx", "data", "json", "txt", "jinja")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
