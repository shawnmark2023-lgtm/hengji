pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "hengji-quality-harness"

include(":import-harness")
include(":ledger-harness")

includeBuild("../..") {
    // A repository mounted at a Windows drive root (for example, T:\ via subst)
    // has no directory name for Gradle to derive as the composite-build path.
    name = "hengji-business"
}
