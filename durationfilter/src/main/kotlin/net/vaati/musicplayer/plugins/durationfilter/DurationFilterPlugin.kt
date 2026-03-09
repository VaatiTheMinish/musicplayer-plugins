package net.vaati.musicplayer.plugins.durationfilter

import net.vaati.musicplayer.audio.AudioPlayer
import net.vaati.musicplayer.audio.PlaylistManager
import net.vaati.musicplayer.plugins.MusicPlayerPlugin
import javax.sound.sampled.AudioSystem
import javax.swing.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout

class DurationFilterPlugin : MusicPlayerPlugin {
    override val id = "net.vaati.musicplayer.plugins.durationfilter"
    override val name = "Duration Filter"

    private var playlist: PlaylistManager? = null
    private var frame: JFrame? = null

    override fun onLoad(player: AudioPlayer, playlist: PlaylistManager) {
        this.playlist = playlist
    }

    override fun hasWindow() = true

    override fun openWindow() {
        SwingUtilities.invokeLater {
            applyTheme()
            val existing = frame
            if (existing != null && existing.isShowing) { existing.toFront(); return@invokeLater }
            frame = buildFrame()
            frame!!.isVisible = true
        }
    }

    private fun buildFrame(): JFrame {
        val logArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        }

        val spinner = JSpinner(SpinnerNumberModel(30, 1, 3600, 1)).apply {
            preferredSize = Dimension(65, 26)
        }

        val filterBtn = JButton("Filter Playlist")
        filterBtn.addActionListener {
            val minSecs = (spinner.value as Int).toDouble()
            filterBtn.isEnabled = false
            logArea.text = "Scanning…"
            Thread {
                val result = runFilter(minSecs)
                SwingUtilities.invokeLater {
                    logArea.text = result
                    filterBtn.isEnabled = true
                }
            }.apply { isDaemon = true }.start()
        }

        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            add(JLabel("Remove tracks shorter than:"))
            add(spinner)
            add(JLabel("seconds"))
            add(filterBtn)
        }

        return JFrame("Duration Filter").apply {
            defaultCloseOperation = JFrame.HIDE_ON_CLOSE
            size = Dimension(500, 360)
            setLocationRelativeTo(null)
            layout = BorderLayout()
            add(topPanel, BorderLayout.NORTH)
            add(JScrollPane(logArea), BorderLayout.CENTER)
        }
    }

    private fun runFilter(minSecs: Double): String {
        val pl = playlist ?: return "Plugin not loaded."
        val tracks = pl.tracks.value
        if (tracks.isEmpty()) return "Playlist is empty."

        data class Entry(val index: Int, val name: String, val durLabel: String)

        val toRemove = mutableListOf<Entry>()
        val skipped = mutableListOf<String>()

        for ((i, track) in tracks.withIndex()) {
            // Use pre-loaded duration if available; fall back to reading from file
            val dur = track.durationSec ?: getDurationSeconds(track.file)
            when {
                dur == null -> skipped += track.displayName
                dur < minSecs -> toRemove += Entry(i, track.displayName, "%.1fs".format(dur))
            }
        }

        if (toRemove.isEmpty()) {
            return buildString {
                appendLine("No tracks shorter than ${minSecs.toInt()}s found.")
                if (skipped.isNotEmpty()) {
                    appendLine()
                    appendLine("${skipped.size} track(s) with unavailable duration (not removed):")
                    skipped.forEach { appendLine("  • $it") }
                }
            }.trimEnd()
        }

        // Remove highest indices first so lower indices stay valid
        toRemove.sortedByDescending { it.index }.forEach { pl.removeAt(it.index) }

        return buildString {
            appendLine("Removed ${toRemove.size} track(s) shorter than ${minSecs.toInt()}s:")
            toRemove.sortedBy { it.index }.forEach { appendLine("  • ${it.name}  (${it.durLabel})") }
            if (skipped.isNotEmpty()) {
                appendLine()
                appendLine("${skipped.size} track(s) skipped — duration unavailable:")
                skipped.forEach { appendLine("  • $it") }
            }
        }.trimEnd()
    }

    private fun getDurationSeconds(file: java.io.File): Double? = try {
        val micros = AudioSystem.getAudioFileFormat(file).properties()["duration"] as? Long
        micros?.let { it / 1_000_000.0 }
    } catch (_: Exception) { null }

    companion object {
        private var themeApplied = false
        private fun applyTheme() {
            if (themeApplied) return
            themeApplied = true
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}
        }
    }
}
