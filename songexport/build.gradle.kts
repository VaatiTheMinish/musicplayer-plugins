plugins {
    id("musicplayer-plugin")
}

dependencies {
    // Metadata + cover art reading — bundled in the plugin JAR
    implementation("net.jthink:jaudiotagger:3.0.1")
}
