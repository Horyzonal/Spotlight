package dev.spotlight.ui

import dev.spotlight.core.Theme
import dev.spotlight.core.ThemeColor
import dev.spotlight.core.ThemeReport
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.FittingMultiLineTextWidget
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import java.nio.file.Path

class ThemeImportScreen(private val parent: ThemesScreen) : Screen(catalogText("Import theme")) {
    private var fileMode = false
    private var name = ""
    private var text = ""
    private var path = ""
    private var failure: String? = null
    private var report: ThemeReport? = null
    private var left = 0
    private var panelWidth = 1
    private var previewY = 0
    private lateinit var editor: MultiLineEditBox
    private lateinit var source: SpotlightEditBox
    private lateinit var importButton: Button
    private var summary: FittingMultiLineTextWidget? = null

    override fun isPauseScreen() = false
    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun init() {
        summary = null
        panelWidth = minOf(620, (width - 24).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        val half = ((panelWidth - 4) / 2).coerceAtLeast(1)
        addRenderableWidget(macroButton("Paste theme", left, 28, half) { mode(false) }).active = fileMode
        addRenderableWidget(macroButton("Local file", left + half + 4, 28, half) { mode(true) }).active = !fileMode
        val nameField = addRenderableWidget(SpotlightEditBox(font, left, 62, panelWidth, 20,
            catalogText(if (fileMode) "Theme name (optional; blank uses # name: header)" else "Theme name (required)")))
        nameField.setMaxLength(128)
        nameField.value = name
        nameField.setResponder { name = it; failure = null; validate() }
        val buttonsY = height - 28
        val contentHeight = ((height - 200) / 2).coerceAtLeast(24)
        editor = addRenderableWidget(MultiLineEditBox.builder().setX(left).setY(106)
            .setTextShadow(SpotlightTheme.textShadow())
            .setTextColor(catalogNativeColor(-2039584, 0xFFF2F5FC))
            .setCursorColor(catalogNativeColor(-3092272, 0xFFF2F5FC))
            .setShowBackground(!catalogCustomFrame())
            .build(font, panelWidth, contentHeight, catalogText("Theme properties")))

        editor.setCharacterLimit(128 * 1024)
        editor.value = text
        editor.setValueListener { text = it; failure = null; validate() }
        editor.visible = !fileMode
        editor.active = !fileMode
        source = addRenderableWidget(SpotlightEditBox(font, left, 106, panelWidth, 20, catalogText("Local .properties file path")))
        source.setMaxLength(4096)
        source.value = path
        source.setResponder { path = it; failure = null; validate() }
        source.visible = fileMode
        source.active = fileMode
        val pasteY = if (fileMode) 132 else editor.bottom + 4
        addRenderableWidget(macroButton(if (fileMode) "Paste path" else "Paste", left, pasteY, panelWidth) { paste() })
        previewY = pasteY + 24
        importButton = addRenderableWidget(macroButton("Import", left, buttonsY, half) { importTheme() })
        importButton.setTooltip(Tooltip.create(catalogText("Import a new theme without applying · $primaryModifierLabel+Enter")))
        addRenderableWidget(macroButton("Cancel · Esc", left + half + 4, buttonsY, half) { onClose() })
        val detailsY = previewY + if (fileMode) 0 else 18
        summary = addRenderableWidget(catalogDetails(left, detailsY, panelWidth,
            (buttonsY - detailsY - 4).coerceAtLeast(12), Component.empty(), font))
        validate()
        setInitialFocus(if (fileMode) source else nameField)
    }

    private fun mode(file: Boolean) {
        fileMode = file
        failure = null
        rebuildWidgets()
    }

    private fun validate() {
        if (!::importButton.isInitialized) return
        var validation: String? = null
        report = null
        try {
            validateImportName(name, fileMode)
            if (fileMode) {
                importSource(path)
            } else {
                report = parseImportText(text)
                require(report!!.warnings.isEmpty()) { report!!.warnings.joinToString("\n") }
            }
        } catch (error: Exception) { validation = error.message ?: error.javaClass.simpleName }
        importButton.active = validation == null
        val error = failure ?: validation
        val hint = if (fileMode) {
            "Paste or drop one local .properties path. File is read and validated only on Import. Blank name uses its # name: header. No URLs."
        } else {
            "Preview only. Unknown keys and invalid values block Import. Paste reads clipboard only when pressed."
        }
        summary?.message = catalogText(listOfNotNull(error ?: "Ready to import; active theme stays unchanged.", hint,
            "Saves new copy in config/spotlight/themes; Apply separately in Themes.").joinToString("\n"))
            .withStyle { it.withColor(catalogNativeColor(-1, if (error != null) 0xFFFFB4A9 else 0xFFF2F5FC)) }
    }

    private fun paste() {
        try {
            val clipboard = minecraft.keyboardHandler.clipboard
            if (fileMode) {
                require(clipboard.length <= 4096) { "File path exceeds 4096 characters" }
                source.value = clipboard
                setFocused(source)
            } else {
                require(clipboard.length <= 128 * 1024 && clipboard.toByteArray(Charsets.UTF_8).size < 128 * 1024) { "Theme exceeds 128 KiB" }
                editor.value = clipboard
                setFocused(editor)
            }
            failure = null
        } catch (error: Exception) { failure = error.message ?: error.javaClass.simpleName }
        validate()
    }

    private fun importTheme() {
        validate()
        if (!importButton.active) return
        try {
            val library = SpotlightTheme.library()
            val entry = if (fileMode) library.importFile(importSource(path), name.trim().ifEmpty { null })
                else library.importTheme(name.trim(), text)
            parent.showImported(entry)
        } catch (error: Exception) {
            failure = error.message ?: error.javaClass.simpleName
            validate()
        }
    }

    override fun onFilesDrop(paths: List<Path>) {
        try {
            require(paths.size == 1) { "Drop exactly one .properties file" }
            val dropped = paths.single().toString()
            require(dropped.length <= 4096) { "File path exceeds 4096 characters" }
            importSource(dropped)
            path = dropped
            fileMode = true
            failure = null
            rebuildWidgets()
        } catch (error: Exception) {
            failure = error.message ?: error.javaClass.simpleName
            validate()
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.isEscape) { onClose(); return true }
        if (primaryModifier(event) && !event.hasAltDown() && !event.hasShiftDown() && event.isConfirmation) {
            importTheme()
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.themeText(font, title, left, 10, SpotlightTheme.color(0xFFF2F5FC))
        graphics.themeText(font, catalogClip(font, if (fileMode) "Name (optional; uses file header)" else "Name (required)", panelWidth), left, 51, SpotlightTheme.color(0xFF9DADC3))
        graphics.themeText(font, catalogClip(font, if (fileMode) "Local .properties path · no URLs" else "Theme properties · scroll to edit", panelWidth), left, 94, SpotlightTheme.color(0xFF9DADC3))
        if (!fileMode) report?.config?.let { config ->
            val colors = listOf(ThemeColor.SCREEN_BACKGROUND, ThemeColor.ROW_SELECTED, ThemeColor.ACCENT, ThemeColor.TEXT, ThemeColor.ERROR)
            val cell = (panelWidth / colors.size).coerceAtLeast(1)
            colors.forEachIndexed { index, token ->
                val x = left + index * cell
                graphics.fill(x, previewY, x + cell - 2, previewY + 12, config.color(token.defaultArgb))
            }
        }
    }
}

internal fun validateImportName(name: String, optional: Boolean) {
    val value = name.trim()
    require(optional && value.isEmpty() || value.isNotEmpty() && value.length <= 64 && value.none { it.isISOControl() || it == '\u2028' || it == '\u2029' }) {
        "Theme name must contain 1..64 characters without line breaks or control characters"
    }
}

internal fun parseImportText(text: String): ThemeReport {
    require(text.isNotBlank()) { "Enter theme properties" }
    require(text.length <= 128 * 1024 && text.toByteArray(Charsets.UTF_8).size < 128 * 1024) { "Theme exceeds 128 KiB" }
    return Theme.parse(text)
}

internal fun importSource(text: String): Path {
    val value = text.trim()
    require(value.isNotEmpty()) { "Enter a local .properties file path" }
    require(!value.contains("://") && !value.startsWith("file:", ignoreCase = true)) { "Use a local file path, not a URL" }
    val source = Path.of(value)
    require(source.fileName?.toString()?.endsWith(".properties") == true) { "Theme file must end in .properties" }
    return source
}
