import org.gradle.api.artifacts.dsl.LockMode

plugins {
    kotlin("jvm") version "2.4.0" apply false
}

allprojects {
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
