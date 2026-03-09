// Compilation stubs — mirror the real classes in the main app.
// Excluded from the plugin JAR by the shadowJar task; provided at runtime by the host app.
package net.vaati.musicplayer.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class PlayState { STOPPED, PLAYING, PAUSED }
enum class RepeatMode { OFF, ONE, ALL }

data class PlayerState(
    val state: PlayState = PlayState.STOPPED,
    val currentIndex: Int = -1,
    val currentSong: String = "",
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val volume: Float = 0.8f,
    val playlist: List<String> = emptyList(),
    val currentDevice: String = "Default",
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF
)

data class Track(
    val file: File,
    val displayName: String = file.nameWithoutExtension
)

class AudioPlayer {
    val state: StateFlow<PlayerState> get() = error("stub")
}

class PlaylistManager {
    fun trackAt(index: Int): Track? = error("stub")
}
