plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("com.hengji:connectors:0.1.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.hengji.quality.ImportHarnessKt")
}
