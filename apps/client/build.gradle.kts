import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.cryptography)
}

val desktopDependencyProfile =
    providers.gradleProperty("hengji.desktopDependencyProfile")
        .orElse(
            when {
                System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows-x64"
                System.getProperty("os.name").startsWith("Mac", ignoreCase = true) &&
                    System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "macos-arm64"
                System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "macos-x64"
                System.getProperty("os.arch") in setOf("aarch64", "arm64") -> "linux-arm64"
                else -> "linux-x64"
            },
        ).get()
val composeMultiplatformVersion = libs.versions.compose.asProvider().get()
val desktopPackageVersion =
    providers.gradleProperty("hengji.packageVersion")
        .orElse("0.1.0")
        .get()
val desktopPackageVersionParts = desktopPackageVersion.split(".").map(String::toIntOrNull)

check(
    desktopDependencyProfile in
        setOf("windows-x64", "macos-arm64", "macos-x64", "linux-arm64", "linux-x64"),
) {
    "Unsupported desktop dependency profile: $desktopDependencyProfile"
}
check(
    desktopPackageVersionParts.size == 3 &&
        desktopPackageVersionParts.all { it != null } &&
        desktopPackageVersionParts[0] in 0..255 &&
        desktopPackageVersionParts[1] in 0..255 &&
        desktopPackageVersionParts[2] in 0..65535,
) {
    "hengji.packageVersion must be a three-part MSI version: major 0..255, minor 0..255, build 0..65535"
}

cryptography {
    configureSwiftLinkerOpts = true
}

kotlin {
    android {
        namespace = "com.hengji.client"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }

        androidResources {
            enable = true
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "HengjiClient"
            isStatic = true
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(
                    "org.jetbrains.compose.desktop:desktop-jvm-$desktopDependencyProfile:" +
                        composeMultiplatformVersion,
                )
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna.platform)
            }
        }
        val desktopTest by getting

        commonMain.dependencies {
            implementation(project(":modules:core-domain"))
            implementation(project(":modules:core-data"))
            implementation(project(":modules:core-insights"))
            implementation(project(":modules:connectors"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation("org.jetbrains.compose.ui:ui-test:$composeMultiplatformVersion")
        }

        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencyLocking {
    lockFile = file("gradle-$desktopDependencyProfile.lockfile")
}

val hostOnlyDevelopmentConfigurations =
    setOf(
        "allDevSourceSetsCompileDependenciesMetadata",
        "composeHotReloadDevDesktopDevRuntimeClasspath",
        "composeHotReloadDevDesktopRuntimeClasspath",
        "composeHotReloadDevDesktopTestRuntimeClasspath",
        "desktopDevCompileClasspath",
        "desktopDevResolvableDependenciesMetadata",
        "desktopDevRuntimeClasspath",
        "desktopTestCompileClasspath",
        "desktopTestRuntimeClasspath",
    )

configurations.configureEach {
    if (name in hostOnlyDevelopmentConfigurations) {
        resolutionStrategy.deactivateDependencyLocking()
    }
}

compose.resources {
    packageOfResClass = "com.hengji.app.generated.resources"
}

compose.desktop {
    application {
        mainClass = "com.hengji.app.DesktopMainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Hengji"
            packageVersion = desktopPackageVersion
            description = "Local-first spending value manager"
            vendor = "HENGJI"

            windows {
                // Keep binaries separate from %LOCALAPPDATA%\Hengji ledger data while
                // avoiding JDK-8226534/ICE64 for nested per-user install directories.
                installationPath = "HengjiApp"
                menuGroup = "HENGJI"
                perUserInstall = true
                upgradeUuid = "b2248acb-5ced-48a7-b69f-3b4f34571acf"
            }

            macOS {
                bundleID = "com.hengji.app"
            }
        }
    }
}
