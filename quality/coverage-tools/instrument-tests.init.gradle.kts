import org.gradle.api.tasks.testing.Test

val coverageAgent = requireNotNull(gradle.startParameter.projectProperties["hengjiCoverageAgent"]) {
    "Missing -PhengjiCoverageAgent"
}
val coverageDirectory = requireNotNull(gradle.startParameter.projectProperties["hengjiCoverageDirectory"]) {
    "Missing -PhengjiCoverageDirectory"
}
val coveredTasks =
    requireNotNull(gradle.startParameter.projectProperties["hengjiCoverageTasks"]) {
        "Missing -PhengjiCoverageTasks"
    }.split(",").toSet()

allprojects {
    tasks.withType<Test>().configureEach {
        if ("${project.path}:$name" in coveredTasks) {
            val safeName = project.path.trim(':').replace(':', '-') + "-$name"
            val destination = file("$coverageDirectory/$safeName.exec")
            doFirst {
                destination.parentFile.mkdirs()
                destination.delete()
            }
            jvmArgs(
                "-javaagent:$coverageAgent=" +
                    "destfile=${destination.absolutePath},append=true,dumponexit=true,output=file",
            )
        }
    }
}
