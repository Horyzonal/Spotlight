package dev.spotlight.core

import java.nio.file.Files
import java.nio.file.Path


object ThemeLibraryCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = Files.createTempDirectory("spotlight-theme-library-check-").toRealPath()
        fun rejects(block: () -> Unit) { check(runCatching(block).isFailure) }
        try {
            val directory = root.resolve("config")
            val library = ThemeLibrary(directory)
            val active = directory.resolve("spotlight-theme.properties")
            val backup = directory.resolve("spotlight-theme.properties.bak")
            check(library.list().size == 6)
            check(!Files.exists(directory))
            check(library.load("default").config == Theme.defaults)
            check(library.load("current").config == Theme.defaults)
            check(Theme.parse(ThemeLibrary.serialize(Theme.defaults)).config == Theme.defaults)
            val expected = listOf(
                listOf("latte", "eff1f5", "e6e9ef", "dce0e8", "4c4f69", "6c6f85", "ccd0da", "bcc0cc", "acb0be", "1e66f5", "d20f39", "fe640b", "df8e1d"),
                listOf("frappe", "303446", "292c3c", "232634", "c6d0f5", "a5adce", "414559", "51576d", "626880", "8caaee", "e78284", "ef9f76", "e5c890"),
                listOf("macchiato", "24273a", "1e2030", "181926", "cad3f5", "a5adcb", "363a4f", "494d64", "5b6078", "8aadf4", "ed8796", "f5a97f", "eed49f"),
                listOf("mocha", "1e1e2e", "181825", "11111b", "cdd6f4", "a6adc8", "313244", "45475a", "585b70", "89b4fa", "f38ba8", "fab387", "f9e2af"),
            )
            val roles = listOf(ThemeColor.PANEL_BACKGROUND, ThemeColor.BUTTON_BACKGROUND, ThemeColor.OVERLAY,
                ThemeColor.TEXT, ThemeColor.TEXT_MUTED, ThemeColor.ROW_SELECTED, ThemeColor.ROW_HOVER,
                ThemeColor.BORDER, ThemeColor.ACCENT, ThemeColor.ERROR, ThemeColor.SELECTOR_HINT, ThemeColor.FAVORITE)
            for (palette in expected) {
                val config = library.load("catppuccin-${palette.first()}").config
                roles.zip(palette.drop(1)).forEach { (role, rgb) ->
                    check(config.color(role.defaultArgb) and 0xFFFFFF == rgb.toInt(16))
                }
                ThemeColor.entries.forEach { check(config.color(it.defaultArgb).ushr(24) == it.defaultArgb.ushr(24).toInt()) }
                check(config.metrics == Theme.defaults.metrics && config.font == null)
                check(config.textShadow() == (palette.first() != "latte"))
                val restored = Theme.parse(ThemeLibrary.serialize(config))
                check(restored.warnings.isEmpty() && restored.config == config)
                check(restored.config.textShadow() == config.textShadow())
            }
            Files.createDirectories(directory)
            val original = "# existing settings\ncolors.text=#FF010203\nlayout.paletteWidth=700\n"
            Files.writeString(active, original)
            check(library.load("current").config.color(ThemeColor.TEXT.defaultArgb) == 0xFF010203.toInt())
            check(Files.readString(active) == original)
            val customConfig = library.load("catppuccin-mocha").config.copy(font = "custom:ui/body")
            val first = library.save(null, "  Café = \\ name  ", customConfig)
            check(first.name == "Café = \\ name" && !first.builtin)
            check(ThemeLibrary(directory).load(first.id) == first)
            check(library.save(null, first.name, customConfig).id != first.id)
            check(library.save("default", "Default copy", customConfig).id != "default")
            check(library.save("current", "Current copy", customConfig).id != "current")
            check(library.load("default").config == Theme.defaults)
            check(Files.readString(active) == original && !Files.exists(backup))
            val updated = library.save(first.id, "Renamed", Theme.defaults)
            check(updated.id == first.id && library.load(first.id) == updated)
            check(library.apply("catppuccin-mocha").warnings.isEmpty())
            check(Files.readString(backup) == original)
            check(library.load("current").config == library.load("catppuccin-mocha").config)
            check(library.apply("default").warnings.isEmpty())
            check(library.load("current").config == Theme.defaults)
            check(Files.readString(backup) == original)
            rejects { library.save(null, " ", Theme.defaults) }
            rejects { library.save(null, "x".repeat(65), Theme.defaults) }
            rejects { library.save(null, "name\ncolors.text=bad", Theme.defaults) }
            rejects { library.save(null, "bad", Theme.defaults.copy(font = "bad\nfont=")) }
            rejects { library.save(null, "bad", Theme.defaults.copy(metrics = mapOf("rowHeight" to 0))) }
            rejects { library.save(null, "bad", Theme.defaults.copy(colors = mapOf(1L to 0))) }
            rejects { library.load("../spotlight-theme") }
            rejects { library.save("../../escape", "bad", Theme.defaults) }
            rejects { library.save("00000000-0000-0000-0000-000000000000", "missing", Theme.defaults) }
            val stored = directory.resolve("spotlight/themes/${first.id}.properties")
            check(Files.isRegularFile(stored))
            check(!Files.exists(directory.resolve("spotlight-themes")))
            Files.writeString(stored, "# name: Broken\ncolors.text=bad\n")
            val before = Files.readAllBytes(active)
            check(library.apply(first.id).warnings.isNotEmpty())
            check(Files.readAllBytes(active).contentEquals(before))
            rejects { library.load(first.id) }
            Files.writeString(stored, "# name: Broken\n" + "x".repeat(128 * 1024))
            rejects { library.load(first.id) }
            Files.delete(stored)
            Files.createSymbolicLink(stored, active)
            rejects { library.load(first.id) }
            rejects { library.list() }
            rejects { library.save(first.id, "bad", Theme.defaults) }
            Files.delete(stored)
            Files.delete(active)
            Files.createSymbolicLink(active, backup)
            check(library.apply("default").warnings.isNotEmpty())
            check(Files.isSymbolicLink(active) && Files.readString(backup) == original)
            Files.delete(active)
            Files.writeString(active, "colors.text=\\uNOPE\n")
            Files.delete(backup)
            check(library.apply("default").warnings.isEmpty())
            check(Files.readString(backup) == "colors.text=\\uNOPE\n")
            Files.delete(backup)
            val malformedBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            Files.write(active, malformedBytes)
            check(library.apply("default").warnings.isEmpty())
            check(Files.readAllBytes(backup).contentEquals(malformedBytes))
            Files.delete(backup)
            Files.createDirectory(backup)
            val rollback = Files.readAllBytes(active)
            check(library.apply("catppuccin-latte").warnings.isNotEmpty())
            check(Files.readAllBytes(active).contentEquals(rollback))
            val linked = root.resolve("linked")
            Files.createSymbolicLink(linked, directory)
            rejects { ThemeLibrary(linked).save(null, "bad", Theme.defaults) }
            check(ThemeLibrary(linked).apply("default").warnings.isNotEmpty())
            val importsDir = root.resolve("imports")
            val imports = ThemeLibrary(importsDir)
            val source = root.resolve("external.properties")
            val sourceText = "# name: External\ncolors.text=#FF010203\n"
            Files.writeString(source, sourceText)
            val imported = imports.importTheme(" Pasted ", sourceText)
            check(imported.name == "Pasted" && imported.config.color(ThemeColor.TEXT.defaultArgb) == 0xFF010203.toInt())
            check(imports.load(imported.id) == imported)
            val external = imports.importFile(source)
            check(external.name == "External" && external.config == imported.config && external.id != imported.id)
            check(imports.importFile(source, "Override").name == "Override")
            check(Files.readString(source) == sourceText)
            check(!Files.exists(importsDir.resolve("spotlight-theme.properties")))
            val importCount = imports.list().size
            rejects { imports.importTheme("Bad", "colors.text=bad\n") }
            rejects { imports.importTheme("Bad", "unknown=value\n") }
            rejects { imports.importTheme("Bad", "colors.text=\\uNOPE\n") }
            rejects { imports.importTheme("Bad", "#".repeat(128 * 1024)) }
            rejects { imports.importTheme(" ", sourceText) }
            rejects { imports.importTheme("x".repeat(65), sourceText) }
            rejects { imports.importTheme("Bad\nname", sourceText) }
            val invalidSource = root.resolve("invalid.properties")
            Files.writeString(invalidSource, "colors.text=bad\n")
            rejects { imports.importFile(invalidSource, "Bad") }
            Files.writeString(invalidSource, "colors.text=#FF010203\n")
            rejects { imports.importFile(invalidSource) }
            check(imports.importFile(invalidSource, "Explicit name").name == "Explicit name")
            Files.write(invalidSource, malformedBytes)
            rejects { imports.importFile(invalidSource, "Bad") }
            Files.writeString(invalidSource, "#".repeat(128 * 1024))
            rejects { imports.importFile(invalidSource, "Bad") }
            Files.delete(invalidSource)
            Files.createSymbolicLink(invalidSource, source)
            rejects { imports.importFile(invalidSource) }
            rejects { imports.importFile(linked.resolve("spotlight/themes/${updated.id}.properties")) }
            rejects { imports.importFile(root) }
            rejects { imports.importFile(root.resolve("missing.properties")) }
            val wrongExtension = root.resolve("external.txt")
            Files.writeString(wrongExtension, sourceText)
            rejects { imports.importFile(wrongExtension) }
            check(imports.list().size == importCount + 1)
            val noWrites = root.resolve("invalid-imports")
            rejects { ThemeLibrary(noWrites).importTheme("Bad", "unknown=value") }
            check(!Files.exists(noWrites))

            val migrationDir = root.resolve("migration")
            val migration = ThemeLibrary(migrationDir)
            val conflict = migration.save(null, "New", Theme.defaults)
            val identical = migration.save(null, "Identical", Theme.defaults)
            val legacyDir = Files.createDirectory(migrationDir.resolve("spotlight-themes"))
            val conflictSource = legacyDir.resolve("${conflict.id}.properties")
            val longName = "L".repeat(64)
            val conflictText = "# name: $longName\ncolors.text=#FF010203\n"
            Files.writeString(conflictSource, conflictText)
            val identicalTarget = migrationDir.resolve("spotlight/themes/${identical.id}.properties")
            val identicalSource = legacyDir.resolve("${identical.id}.properties")
            Files.copy(identicalTarget, identicalSource)
            val oldId = "00000000-0000-0000-0000-000000000001"
            val oldSource = legacyDir.resolve("$oldId.properties")
            Files.writeString(oldSource, sourceText)
            check(migration.load(oldId).name == "External")
            check(Files.readString(migrationDir.resolve("spotlight/themes/$oldId.properties")) == sourceText)
            val migratedList = migration.list()
            check(migratedList.size == 10)
            check(migration.load(conflict.id) == conflict)
            val collision = migratedList.single { it.name.endsWith(" (legacy)") }
            check(collision.id != conflict.id && collision.name.length == 64)
            check(collision.config.color(ThemeColor.TEXT.defaultArgb) == 0xFF010203.toInt())
            check(ThemeLibrary(migrationDir).list() == migratedList)
            check(Files.readString(conflictSource) == conflictText)
            check(Files.readString(oldSource) == sourceText)
            check(Files.readAllBytes(identicalSource).contentEquals(Files.readAllBytes(identicalTarget)))
            check(!Files.exists(migrationDir.resolve("spotlight-theme.properties")))
            val collisionTarget = migrationDir.resolve("spotlight/themes/${collision.id}.properties")
            Files.writeString(collisionTarget, "# name: Changed\nfont=\n")
            rejects { ThemeLibrary(migrationDir).list() }
            check(Files.readString(collisionTarget) == "# name: Changed\nfont=\n")
            check(Files.readString(conflictSource) == conflictText)

            val invalidLegacyDir = root.resolve("invalid-legacy")
            val invalidLegacy = Files.createDirectories(invalidLegacyDir.resolve("spotlight-themes"))
            val invalidLegacyFile = invalidLegacy.resolve("$oldId.properties")
            Files.writeString(invalidLegacyFile, "# name: Bad\nunknown=value\n")
            rejects { ThemeLibrary(invalidLegacyDir).list() }
            check(!Files.exists(invalidLegacyDir.resolve("spotlight")))
            check(Files.readString(invalidLegacyFile) == "# name: Bad\nunknown=value\n")
            Files.delete(invalidLegacyFile)
            Files.createSymbolicLink(invalidLegacyFile, source)
            rejects { ThemeLibrary(invalidLegacyDir).list() }
            val linkedStorageDir = Files.createDirectory(root.resolve("linked-storage"))
            Files.createSymbolicLink(linkedStorageDir.resolve("spotlight"), importsDir.resolve("spotlight"))
            rejects { ThemeLibrary(linkedStorageDir).importTheme("Bad", sourceText) }

            val full = ThemeLibrary(root.resolve("full"))
            repeat(128) { full.save(null, "Same name", Theme.defaults) }
            check(full.list().size == 134)
            rejects { full.save(null, "Overflow", Theme.defaults) }
            val existing = full.list().last()
            check(full.save(existing.id, "Still editable", Theme.defaults).id == existing.id)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
        println("Theme library checks passed")
    }
}
