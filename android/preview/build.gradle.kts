// Plain JVM module that compiles the platform-independent code of :app (everything except
// app.isaaccompanion.platform) so the real screen code can be rendered to PNG with java.awt.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}


sourceSets {
    main {
        kotlin.srcDir("../app/src/main/kotlin")
        kotlin.exclude("**/platform/**")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("screens.dir", layout.buildDirectory.dir("screens").get().asFile.absolutePath)
}
