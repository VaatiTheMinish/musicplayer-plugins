// Compilation stubs — mirror the real types in the main app.
// Excluded from the plugin JAR by the shadowJar task; provided at runtime by the host app.
package net.vaati.musicplayer.plugins

import net.vaati.musicplayer.audio.AudioPlayer
import net.vaati.musicplayer.audio.PlaylistManager

data class ConfigField(
    val key: String,
    val label: String,
    val currentValue: String,
    val hint: String = "",
    val type: String = "text"
)

interface MusicPlayerPlugin {
    val id: String
    val name: String
    fun onLoad(player: AudioPlayer, playlist: PlaylistManager)
    fun onUnload() {}
    fun getConfigFields(): List<ConfigField> = emptyList()
    fun applyConfig(values: Map<String, String>) {}
    fun hasWindow(): Boolean = false
    fun openWindow() {}
}
