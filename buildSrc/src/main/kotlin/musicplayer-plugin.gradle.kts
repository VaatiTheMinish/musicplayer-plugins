import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

group = "net.vaati.musicplayer.plugins"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Provided by the host app's classloader at runtime — compile-only stubs
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<ShadowJar>().configureEach {
    archiveBaseName.set(project.name)
    archiveClassifier.set("")
    archiveVersion.set("")
    // Exclude stubs — provided by the host app at runtime
    exclude("net/vaati/musicplayer/audio/**")
    exclude("net/vaati/musicplayer/plugins/MusicPlayerPlugin*.class")
    exclude("net/vaati/musicplayer/plugins/ConfigField*.class")
    // Exclude Kotlin stdlib and coroutines — provided by the host app
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
