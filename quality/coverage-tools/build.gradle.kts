import org.gradle.api.artifacts.dsl.LockMode

plugins {
    base
}

group = "com.hengji.quality"
version = "1.0"

val jacocoAgent by configurations.creating
val jacocoCli by configurations.creating

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

dependencies {
    jacocoAgent("org.jacoco:org.jacoco.agent:0.8.14")
    jacocoCli("org.jacoco:org.jacoco.cli:0.8.14:nodeps") {
        isTransitive = false
    }
}

val prepareCoverageTools by tasks.registering(Sync::class) {
    from({ zipTree(jacocoAgent.singleFile) }) {
        include("jacocoagent.jar")
        rename { "jacocoagent.jar" }
    }
    from(jacocoCli) {
        rename { "jacococli.jar" }
    }
    into(layout.buildDirectory.dir("tools"))
}

tasks.register("resolveAllDependencies") {
    notCompatibleWithConfigurationCache("Resolves every coverage-tool configuration")
    doLast {
        configurations
            .filter { it.isCanBeResolved }
            .forEach { it.incoming.resolutionResult.rootComponent.get() }
    }
}
