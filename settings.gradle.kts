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

rootProject.name = "hengji"

include(":apps:client")
include(":apps:client:androidApp")
include(":modules:core-domain")
include(":modules:core-data")
include(":modules:core-insights")
include(":modules:connectors")
