gradle.beforeProject {
    if (rootProject.name == "hengji") {
        val safeProjectPath = path.trim(':').replace(':', '_').ifEmpty { "root" }
        layout.buildDirectory.set(
            rootProject.layout.projectDirectory.dir("quality/harness/.isolated-business-build/$safeProjectPath")
        )
    }
}
