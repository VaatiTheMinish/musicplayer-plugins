package net.vaati.musicplayer.plugins.songexport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChangedBy
import net.vaati.musicplayer.audio.AudioPlayer
import net.vaati.musicplayer.audio.PlaylistManager
import net.vaati.musicplayer.plugins.ConfigField
import net.vaati.musicplayer.plugins.MusicPlayerPlugin
import org.jaudiotagger.audio.AudioFileIO
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Properties
import java.util.logging.Level
import javax.imageio.ImageIO

/**
 * Writes the currently playing song title to song.txt and cover art to cover.png.
 * Intended for use with Streamer Bot !song commands, OBS text sources, etc.
 * Each output can be toggled independently in the plugin settings.
 */
class SongExportPlugin : MusicPlayerPlugin {

    override val id   = "net.vaati.musicplayer.plugins.songexport"
    override val name = "Song Export"

    private val logger = LoggerFactory.getLogger(name)

    private var scope: CoroutineScope? = null
    private var outputDir: File = defaultOutputDir()
    private var writeTxt: Boolean = true
    private var writeCover: Boolean = true

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onLoad(player: AudioPlayer, playlist: PlaylistManager) {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = Level.OFF

        loadOrCreateConfig()
        outputDir.mkdirs()
        logger.info("Song Export output dir: ${outputDir.absolutePath}")

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope!!.launch {
            player.state
                .distinctUntilChangedBy { it.currentIndex }
                .collect { state ->
                    val index = state.currentIndex
                    if (index < 0) {
                        if (writeTxt) writeTitleFile("")
                        if (writeCover) writeBlankCover()
                        return@collect
                    }
                    val track = playlist.trackAt(index) ?: return@collect
                    val title = stripBracketSuffix(track.displayName)
                    logger.info("Exporting: $title")
                    if (writeTxt) writeTitleFile(title)
                    if (writeCover) writeCoverArt(track.file)
                }
        }
    }

    override fun onUnload() {
        scope?.cancel()
        scope = null
    }

    // ── File writing ──────────────────────────────────────────────────────

    private fun writeTitleFile(title: String) {
        try {
            File(outputDir, "song.txt").writeText(title)
        } catch (e: Exception) {
            logger.error("Failed to write song.txt: ${e.message}")
        }
    }

    private fun writeCoverArt(audioFile: File) {
        try {
            val af = AudioFileIO.read(audioFile)
            val artwork = af.tag?.firstArtwork
            if (artwork != null) {
                val img = ImageIO.read(ByteArrayInputStream(artwork.binaryData))
                if (img != null) {
                    ImageIO.write(img, "png", File(outputDir, "cover.png"))
                    return
                }
            }
        } catch (_: Exception) {}
        // Folder art fallback
        val dir = audioFile.parentFile
        if (dir != null) {
            val folderArt = listOf("cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg")
                .map { File(dir, it) }.firstOrNull { it.exists() }
            if (folderArt != null) {
                try {
                    val img = ImageIO.read(folderArt)
                    if (img != null) { ImageIO.write(img, "png", File(outputDir, "cover.png")); return }
                } catch (_: Exception) {}
            }
        }
        writeBlankCover()
    }

    private fun writeBlankCover() {
        try {
            val img = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color(30, 30, 34)
            g.fillRect(0, 0, 256, 256)
            g.dispose()
            ImageIO.write(img, "png", File(outputDir, "cover.png"))
        } catch (e: Exception) {
            logger.error("Failed to write blank cover.png: ${e.message}")
        }
    }

    // ── Config ────────────────────────────────────────────────────────────

    override fun getConfigFields(): List<ConfigField> = listOf(
        ConfigField(
            key  = "outputDir",
            label = "Output directory",
            currentValue = outputDir.absolutePath,
            hint = "song.txt and/or cover.png are written here."
        ),
        ConfigField(
            key  = "writeTxt",
            label = "Write song title to song.txt",
            currentValue = writeTxt.toString(),
            type = "boolean"
        ),
        ConfigField(
            key  = "writeCover",
            label = "Write cover art to cover.png",
            currentValue = writeCover.toString(),
            type = "boolean"
        )
    )

    override fun applyConfig(values: Map<String, String>) {
        values["outputDir"]?.takeIf { it.isNotBlank() }?.let { outputDir = File(it); outputDir.mkdirs() }
        values["writeTxt"]?.toBooleanStrictOrNull()?.let { writeTxt = it }
        values["writeCover"]?.toBooleanStrictOrNull()?.let { writeCover = it }
        saveConfig()
    }

    private fun loadOrCreateConfig() {
        val cfg = configFile()
        if (!cfg.exists()) { cfg.parentFile.mkdirs(); saveConfig(); return }
        val props = Properties()
        try { cfg.inputStream().use { props.load(it) } } catch (_: Exception) { return }
        props.getProperty("outputDir")?.let { outputDir = File(it) }
        props.getProperty("writeTxt")?.toBooleanStrictOrNull()?.let { writeTxt = it }
        props.getProperty("writeCover")?.toBooleanStrictOrNull()?.let { writeCover = it }
    }

    private fun saveConfig() {
        val cfg = configFile()
        cfg.parentFile?.mkdirs()
        val props = Properties()
        props["outputDir"]  = outputDir.absolutePath
        props["writeTxt"]   = writeTxt.toString()
        props["writeCover"] = writeCover.toString()
        try { cfg.outputStream().use { props.store(it, "Song Export config") } } catch (_: Exception) {}
    }

    /** Strips trailing bracket content like "[dQw4w9WgXcQ]" or "[Official Video]" */
    private fun stripBracketSuffix(name: String): String =
        name.replace(Regex("""\s*\[[^\]]+]\s*$"""), "").trim()

    companion object {
        private fun pluginDataDir(): File {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            return if (isWindows)
                File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "musicplayer/plugins/songexport")
            else
                File(System.getProperty("user.home"), ".local/share/musicplayer/plugins/songexport")
        }
        fun defaultOutputDir() = File(pluginDataDir(), "output")
        fun configFile()       = File(pluginDataDir(), "config.properties")
    }
}
