package net.vaati.musicplayer.plugins.obssongoverlay

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

class ObsSongOverlayPlugin : MusicPlayerPlugin {

    override val id   = "net.vaati.obssongoverlay"
    override val name = "OBS Song Overlay"

    private val logger = LoggerFactory.getLogger(name)

    private var scope: CoroutineScope? = null
    private var outputDir: File = defaultOutputDir()

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onLoad(player: AudioPlayer, playlist: PlaylistManager) {
        // Silence JAudioTagger's verbose java.util.logging output
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = Level.OFF

        loadOrCreateConfig()
        outputDir.mkdirs()
        logger.info("OBS Song Overlay output dir: ${outputDir.absolutePath}")

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope!!.launch {
            player.state
                .distinctUntilChangedBy { it.currentIndex }
                .collect { state ->
                    val index = state.currentIndex
                    if (index < 0) {
                        writeTitleFile("")
                        writeBlankCover()
                        return@collect
                    }
                    val track = playlist.trackAt(index) ?: return@collect
                    val title = stripYouTubeId(track.displayName)
                    logger.info("Now playing: $title")
                    writeTitleFile(title)
                    writeCoverArt(track.file)
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
                val bytes = artwork.binaryData
                val img = ImageIO.read(ByteArrayInputStream(bytes))
                if (img != null) {
                    ImageIO.write(img, "png", File(outputDir, "cover.png"))
                    return
                }
            }
        } catch (_: Exception) {
            // format not supported by JAudioTagger, or no tag — fall through
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
            logger.error("Failed to write cover.png: ${e.message}")
        }
    }

    // ── Config ────────────────────────────────────────────────────────────

    private fun loadOrCreateConfig() {
        val cfg = configFile()
        if (!cfg.exists()) {
            cfg.parentFile.mkdirs()
            val props = Properties()
            props["outputDir"] = defaultOutputDir().absolutePath
            cfg.outputStream().use {
                props.store(it, "OBS Song Overlay — set outputDir to your desired folder")
            }
            logger.info("Created config at ${cfg.absolutePath} — edit outputDir to change output location")
        }
        val props = Properties()
        cfg.inputStream().use { props.load(it) }
        props.getProperty("outputDir")?.let { outputDir = File(it) }
    }

    // ── Config API ────────────────────────────────────────────────────────

    override fun getConfigFields(): List<ConfigField> = listOf(
        ConfigField(
            key = "outputDir",
            label = "Output directory",
            currentValue = outputDir.absolutePath,
            hint = "Folder where song.txt and cover.png are written"
        )
    )

    override fun applyConfig(values: Map<String, String>) {
        values["outputDir"]?.takeIf { it.isNotBlank() }?.let { dir ->
            outputDir = File(dir)
            outputDir.mkdirs()
            val cfg = configFile()
            val props = Properties()
            if (cfg.exists()) cfg.inputStream().use { props.load(it) }
            props["outputDir"] = outputDir.absolutePath
            cfg.outputStream().use { props.store(it, "OBS Song Overlay config") }
            logger.info("Output dir updated to: ${outputDir.absolutePath}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Strips an 11-char YouTube video ID in brackets, e.g. " [dQw4w9WgXcQ]" */
    private fun stripYouTubeId(name: String): String =
        name.replace(Regex("""\s*\[[a-zA-Z0-9_\-]{11}]$"""), "").trim()

    companion object {
        private fun pluginDataDir(): File {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            return if (isWindows)
                File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "musicplayer/plugins/obssongoverlay")
            else
                File(System.getProperty("user.home"), ".local/share/musicplayer/plugins/obssongoverlay")
        }
        fun defaultOutputDir() = File(pluginDataDir(), "overlay")
        fun configFile()       = File(pluginDataDir(), "config.properties")
    }
}
