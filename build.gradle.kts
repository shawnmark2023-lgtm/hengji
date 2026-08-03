import org.gradle.api.artifacts.dsl.LockMode

if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
    val minimumWindowsRuntime = Runtime.Version.parse("21.0.12")
    check(Runtime.version() >= minimumWindowsRuntime) {
        "Windows builds require JDK 21.0.12 or newer so the packaged VC runtime can load the built-in AI engine. " +
            "Current runtime: ${Runtime.version()}"
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.cryptography) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    group = "com.hengji"
    version = "0.1.0"

    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.STRICT
    }

    tasks.register("resolveAllDependencies") {
        notCompatibleWithConfigurationCache("Resolves every resolvable configuration at execution time")
        doLast {
            configurations
                .filter { it.isCanBeResolved }
                .forEach { it.incoming.resolutionResult.rootComponent.get() }
        }
    }
}
