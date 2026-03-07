plugins {
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow") version "9.3.2"
}

group = "net.vaati.musicplayer.plugins"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Coroutines — provided by the main app's classloader at runtime (stubs need it to compile)
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // SLF4J — provided by main app at runtime
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Metadata + cover art reading — bundled in the plugin JAR
    implementation("net.jthink:jaudiotagger:3.0.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    archiveBaseName.set("obssongoverlay")
    archiveClassifier.set("")
    archiveVersion.set("")
    // Exclude stubs — these packages are provided by the host app at runtime
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
