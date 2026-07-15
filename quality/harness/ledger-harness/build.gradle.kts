plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation("com.hengji:core-domain:0.1.0")
    implementation("com.hengji:core-data:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.hengji.quality.LedgerHarnessKt")
}
