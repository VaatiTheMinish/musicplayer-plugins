package net.vaati.musicplayer.plugins.ytdlpgui

import net.vaati.musicplayer.audio.AudioPlayer
import net.vaati.musicplayer.audio.PlaylistManager
import net.vaati.musicplayer.plugins.MusicPlayerPlugin
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

class YtDlpGuiPlugin : MusicPlayerPlugin {
    override val id = "net.vaati.musicplayer.plugins.ytdlpgui"
    override val name = "yt-dlp GUI"

    private val logger = LoggerFactory.getLogger(YtDlpGuiPlugin::class.java)
    private var frame: JFrame? = null
    private var downloadProcess: Process? = null

    companion object {
        // Applied once per JVM — use the OS-native look and feel
        private var themeApplied = false

        private fun applyTheme() {
            if (themeApplied) return
            themeApplied = true
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (e: Exception) {
                LoggerFactory.getLogger(YtDlpGuiPlugin::class.java).warn("Failed to apply system L&F", e)
            }
        }

        fun pluginDataDir(): File {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            return if (isWindows)
                File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "musicplayer/plugins/ytdlpgui")
            else
                File(System.getProperty("user.home"), ".local/share/musicplayer/plugins/ytdlpgui")
        }
    }

    override fun onLoad(player: AudioPlayer, playlist: PlaylistManager) {
        logger.info("yt-dlp GUI plugin loaded")
    }

    override fun onUnload() {
        downloadProcess?.destroyForcibly()
        frame?.dispose()
        frame = null
    }

    override fun hasWindow() = true

    override fun openWindow() {
        SwingUtilities.invokeLater {
            applyTheme()
            val existing = frame
            if (existing != null && existing.isShowing) {
                existing.toFront()
                return@invokeLater
            }
            frame = buildFrame()
            frame!!.isVisible = true
        }
    }

    private fun buildFrame(): JFrame {
        val dataDir = pluginDataDir()
        val archiveFile = File(dataDir, "archive.txt")
        val defaultOutputDir = File(dataDir, "downloads")
        val configFile = File(dataDir, "config.properties")

        // Load persisted output dir
        val savedOutputDir = loadOutputDir(configFile, defaultOutputDir)

        val f = JFrame("yt-dlp GUI")
        f.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
        f.setSize(640, 560)
        f.setLocationRelativeTo(null)

        val content = JPanel(BorderLayout(8, 8))
        content.border = EmptyBorder(12, 12, 12, 12)

        // ── Top form ──────────────────────────────────────────────
        val form = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        fun label(text: String, row: Int) {
            gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            form.add(JLabel(text), gbc)
        }
        fun field(comp: JComponent, row: Int) {
            gbc.gridx = 1; gbc.gridy = row; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            form.add(comp, gbc)
        }
        val urlField = JTextField(savedOutputDir.absolutePath)  // placeholder text handled below
        urlField.text = ""
        urlField.toolTipText = "YouTube channel URL, playlist URL, or video URL"

        val filterField = JTextField()
        filterField.toolTipText = "Optional title filter substring, e.g. [Copyright Free]"

        val outputDirField = JTextField(savedOutputDir.absolutePath)
        val browseBtn = JButton("Browse…")

        label("URL:", 0); field(urlField, 0)
        label("Title filter:", 1); field(filterField, 1)

        // Output dir row has 3 columns
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        form.add(JLabel("Output dir:"), gbc)
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        form.add(outputDirField, gbc)
        gbc.gridx = 2; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        form.add(browseBtn, gbc)

        content.add(form, BorderLayout.NORTH)

        // ── Log area ──────────────────────────────────────────────
        val logArea = JTextArea()
        logArea.isEditable = false
        logArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        logArea.lineWrap = true
        logArea.wrapStyleWord = true
        val scroll = JScrollPane(logArea)
        scroll.preferredSize = Dimension(0, 300)
        content.add(scroll, BorderLayout.CENTER)

        // ── Button bar ────────────────────────────────────────────
        val downloadBtn = JButton("Download")
        val stopBtn = JButton("Stop")
        val clearBtn = JButton("Clear Log")
        stopBtn.isEnabled = false

        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        btnPanel.add(downloadBtn)
        btnPanel.add(stopBtn)
        btnPanel.add(clearBtn)
        content.add(btnPanel, BorderLayout.SOUTH)

        f.contentPane = content

        // ── Browse action ─────────────────────────────────────────
        browseBtn.addActionListener {
            val chooser = JFileChooser(outputDirField.text.ifBlank { defaultOutputDir.absolutePath })
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (chooser.showOpenDialog(f) == JFileChooser.APPROVE_OPTION) {
                outputDirField.text = chooser.selectedFile.absolutePath
                saveOutputDir(configFile, chooser.selectedFile)
            }
        }

        // ── Clear action ──────────────────────────────────────────
        clearBtn.addActionListener { logArea.text = "" }

        // ── Download action ───────────────────────────────────────
        downloadBtn.addActionListener {
            val url = urlField.text.trim()
            if (url.isBlank()) {
                JOptionPane.showMessageDialog(f, "Please enter a URL.", "No URL", JOptionPane.WARNING_MESSAGE)
                return@addActionListener
            }
            val outDir = File(outputDirField.text.trim().ifBlank { defaultOutputDir.absolutePath })
            outDir.mkdirs()
            archiveFile.parentFile?.mkdirs()

            val cmd = buildCommand(url, filterField.text.trim(), outDir, archiveFile)
            logArea.append("$ ${cmd.joinToString(" ")}\n\n")

            downloadBtn.isEnabled = false
            stopBtn.isEnabled = true

            Thread {
                try {
                    val proc = ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start()
                    downloadProcess = proc

                    proc.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            SwingUtilities.invokeLater {
                                logArea.append("$line\n")
                                // Auto-scroll to bottom
                                logArea.caretPosition = logArea.document.length
                            }
                        }
                    }

                    val exitCode = proc.waitFor()
                    SwingUtilities.invokeLater {
                        logArea.append("\n[Process exited with code $exitCode]\n")
                        logArea.caretPosition = logArea.document.length
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        logArea.append("\n[Error: ${e.message}]\n")
                    }
                    logger.error("yt-dlp process error", e)
                } finally {
                    downloadProcess = null
                    SwingUtilities.invokeLater {
                        downloadBtn.isEnabled = true
                        stopBtn.isEnabled = false
                    }
                }
            }.also { it.isDaemon = true }.start()
        }

        // ── Stop action ───────────────────────────────────────────
        stopBtn.addActionListener {
            downloadProcess?.destroy()
            logArea.append("\n[Download stopped by user]\n")
        }

        // ── Save output dir on text change ────────────────────────
        outputDirField.addActionListener {
            val dir = File(outputDirField.text.trim())
            if (dir.absolutePath.isNotBlank()) saveOutputDir(configFile, dir)
        }

        f.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                val dir = File(outputDirField.text.trim())
                if (dir.absolutePath.isNotBlank()) saveOutputDir(configFile, dir)
            }
        })

        return f
    }

    private fun resolveYtDlp(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val exeName = if (isWindows) "yt-dlp.exe" else "yt-dlp"
        val local = File(pluginDataDir(), exeName)
        return if (local.exists()) local.absolutePath else "yt-dlp"
    }

    private fun buildCommand(url: String, filter: String, outDir: File, archiveFile: File): List<String> {
        val cmd = mutableListOf(
            resolveYtDlp(),
            "--extract-audio",
            "--audio-format", "mp3",
            "--audio-quality", "0",
            "--embed-thumbnail",
            "--convert-thumbnails", "jpg",
            "--add-metadata",
            "--parse-metadata", "%(uploader)s:%(artist)s",
            "--parse-metadata", "%(webpage_url)s:%(comment)s",
            "--download-archive", archiveFile.absolutePath,
            "-o", File(outDir, "%(title)s.%(ext)s").absolutePath
        )
        if (filter.isNotBlank()) {
            cmd.addAll(listOf("--match-filter", "title~='${filter.replace("'", "\\'")}'"))
        }
        cmd.add(url)
        return cmd
    }

    private fun loadOutputDir(configFile: File, default: File): File {
        if (!configFile.exists()) return default
        return try {
            val props = java.util.Properties()
            configFile.inputStream().use { props.load(it) }
            val path = props.getProperty("outputDir") ?: return default
            File(path).takeIf { it.absolutePath.isNotBlank() } ?: default
        } catch (_: Exception) { default }
    }

    private fun saveOutputDir(configFile: File, dir: File) {
        try {
            configFile.parentFile?.mkdirs()
            val props = java.util.Properties()
            if (configFile.exists()) configFile.inputStream().use { props.load(it) }
            props.setProperty("outputDir", dir.absolutePath)
            configFile.outputStream().use { props.store(it, null) }
        } catch (e: Exception) {
            logger.warn("Failed to save config", e)
        }
    }

}
