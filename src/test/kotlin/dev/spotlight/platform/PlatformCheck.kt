package dev.spotlight.platform

import dev.spotlight.commands.creativePlan
import dev.spotlight.commands.chunkInfo
import com.mojang.serialization.JsonOps
import dev.spotlight.core.ColoredRun
import dev.spotlight.core.StyledText
import dev.spotlight.core.TextStyle
import net.minecraft.network.chat.Component
import dev.spotlight.core.Entry
import dev.spotlight.core.Fuzzy
import dev.spotlight.core.LocationStore
import dev.spotlight.core.CommandSettings
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

import java.nio.file.Files
import dev.spotlight.ui.fillModes
import dev.spotlight.ui.fillModeHelp
import dev.spotlight.ui.cloneMasks
import dev.spotlight.ui.cloneModes
import dev.spotlight.ui.uuidEntries
import dev.spotlight.ui.pingEntries
import java.util.UUID
import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items


internal object PlatformCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        dev.spotlight.ui.SoundMenuCheck.main(emptyArray())
        dev.spotlight.ui.CurrentSongCheck.run()
        val registries = VanillaRegistries.createLookup()
        val formatted = StyledText(listOf(ColoredRun("red\n", 0xFF0000), ColoredRun("blue\n\n", 0x0000FF)),
            TextStyle(bold = true, italic = false, underlined = true, strikethrough = true, obfuscated = true))
        val component = CreativeText.component(formatted)
        check(component.string == "red\nblue\n\n")
        check(component.style.isBold && !component.style.isItalic && component.style.isUnderlined &&
            component.style.isStrikethrough && component.style.isObfuscated)
        check(component.siblings.map { it.style.color!!.value } == listOf(0xFF0000, 0x0000FF))
        val encoded = Component.Serializer.toJson(component)
        val decoded = requireNotNull(Component.Serializer.fromJson(encoded))
        check(decoded.string == component.string)
        check(decoded.style == component.style)
        check(Component.Serializer.toJson(decoded) == encoded)
        CreativeTextNbtCheck.checkAll()
        Runtime1201Check.checkAll()
        val lines = CreativeText.loreLines(formatted)
        check(lines.map { it.string } == listOf("red", "blue", "", ""))
        check(lines.all { it.style == component.style })
        check(lines[0].siblings.single().style.color!!.value == 0xFF0000)
        check(lines[1].siblings.single().style.color!!.value == 0x0000FF)
        val mixed = CreativeText.loreLines(StyledText(listOf(ColoredRun("a", 1), ColoredRun("b\nc", 2))))
        check(mixed.map { it.string } == listOf("ab", "c"))
        check(mixed[0].siblings.map { it.style.color!!.value } == listOf(1, 2))
        check(CreativeText.loreLines(StyledText(emptyList())).single().string.isEmpty())
        check(CreativeText.loreLines(StyledText(listOf(ColoredRun("\n".repeat(255), 0)))).size == 256)
        check(runCatching { CreativeText.loreLines(StyledText(listOf(ColoredRun("\n".repeat(256), 0)))) }.isFailure)
        CreativeText.validateTextSize(lines)
        val oversized = StyledText(List(4096) { ColoredRun("x", it) })
        check(runCatching { CreativeText.validateTextSize(listOf(CreativeText.component(oversized))) }.isFailure)
        val command = SpotlightCommand("check:registry", "Registry check") {}
        Spotlight.register(command)
        check(Spotlight.commands == listOf(command))
        check(runCatching { Spotlight.register(command) }.isFailure)
        check(runCatching { (Spotlight.commands as MutableList<SpotlightCommand>).clear() }.isFailure)
        check(Spotlight.commands == listOf(command))
        check(CommandContext().text == "")
        check(!SpotlightClient.shouldReplaceCommandKey())
        val hotkeys = (('A'..'Z') + ('0'..'9')).map { it.toString() } + (1..25).map { "F$it" } +
            listOf("SPACE", "TAB", "ENTER", "ESCAPE", "BACKSPACE", "DELETE", "INSERT", "HOME", "END",
                "PAGEUP", "PAGEDOWN", "UP", "DOWN", "LEFT", "RIGHT", "MINUS", "EQUAL", "LEFT_BRACKET",
                "RIGHT_BRACKET", "BACKSLASH", "SEMICOLON", "APOSTROPHE", "GRAVE_ACCENT", "COMMA", "PERIOD",
                "SLASH", "CAPS_LOCK", "SCROLL_LOCK", "NUM_LOCK", "PRINT_SCREEN", "PAUSE", "KP_DECIMAL",
                "KP_DIVIDE", "KP_MULTIPLY", "KP_SUBTRACT", "KP_ADD", "KP_ENTER", "KP_EQUAL") + (0..9).map { "KP_$it" }
        hotkeys.forEach { check(hotkeyCode(it) != null) { "Unsupported hotkey: $it" } }
        check(hotkeyCode("PAGEUP") == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP)
        check(hotkeyCode("KP_ENTER") == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)
        check(hotkeyCode("KP_DECIMAL") == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_DECIMAL)
        check(hotkeyCode("UNSUPPORTED") == null)
        dev.spotlight.core.SpotlightSettingsCheck.main(emptyArray())
        check(Spotlight.requestedQuery == null)
        Spotlight.requestedQuery = "/seed "
        check(Spotlight.requestedQuery == "/seed ")
        Spotlight.requestedQuery = null
        check(CommandInput.entries.containsAll(listOf(CommandInput.NUMBER, CommandInput.ENTITY, CommandInput.BLOCK, CommandInput.LOCATION)))
        check(validatedSlash("/time set day") == "time set day")
        check(validatedSlash("/" + "x".repeat(32767)).length == 32767)
        for (text in listOf("", "/", "//tp", "tp", "/say hi\nstop", "/say hi\rstop", "/say\u0000hi", "/" + "x".repeat(32768))) {
            check(runCatching { validatedSlash(text) }.isFailure) { "Accepted invalid slash text" }
        }
        check(Spotlight.blocks().any { it.detail == "minecraft:stone" })
        check(dev.spotlight.ui.fillBlockIcon("minecraft:air").isEmpty)
        check(dev.spotlight.ui.fillBlockIcon("minecraft:water").isEmpty)
        check(dev.spotlight.ui.fillBlockIcon("minecraft:stone").item == Items.STONE)
        check(dev.spotlight.ui.fillBlockIcon("missing:block").isEmpty)
        val stone = ItemStack(Items.STONE)
        val existing = listOf(ItemStack.EMPTY, stone.copyWithCount(60), ItemStack(Items.DIRT))
        val plan = creativePlan(existing, stone, 68) { 64 }
        check(plan.map { it.first } == listOf(1, 0))
        check(plan.map { it.second.count } == listOf(64, 64))
        check(existing[0].isEmpty && existing[1].count == 60 && existing[2].item == Items.DIRT)
        check(runCatching { creativePlan(existing, stone, 69) { 64 } }.isFailure)
        check(existing[0].isEmpty && existing[1].count == 60)
        val tools = creativePlan(List(3) { ItemStack.EMPTY }, ItemStack(Items.DIAMOND_SWORD), 3) { 64 }
        check(tools.size == 3 && tools.all { it.second.count == 1 })
        check(runCatching { creativePlan(existing, stone, 0) { 64 } }.isFailure)
        check(runCatching { creativePlan(existing, stone, 2305) { 64 } }.isFailure)
        check(runCatching { creativePlan(existing, ItemStack.EMPTY, 1) { 64 } }.isFailure)
        check(creativePlan(listOf(ItemStack.EMPTY, ItemStack.EMPTY), stone, 32) { 16 }.all { it.second.count == 16 })
        registerBuiltIns()
        check(Spotlight.commands.drop(1).map { it.id.removePrefix("spotlight:") } == listOf(
            "fov", "gamma", "find_entities", "find_blocks", "tp", "sync_locations", "add_location", "spawn_location", "copy_item_id",
            "creative_give", "find_inventory", "copy_coordinates", "seed", "uuid", "ping",
                        "gamemode_survival", "gamemode_creative", "gamemode_adventure", "gamemode_spectator", "settings", "minecraft_settings", "extensions_catalog",
            "light_level", "biome", "chunk_info", "dimension_info", "world_time", "heightmap",
            "facing", "position", "fps", "memory", "player_info", "targeted_block", "targeted_entity", "effects", "connection", "server_plugins",
            "text_editor", "copy_item_data", "clear_item_name", "clear_item_lore", "fill", "clone",
                        "snake", "dino", "2048", "tetris", "banner_editor", "recipes", "sound_manager", "music_player", "current_song", "themes", "unbreakable",
        ))
        check(Spotlight.commands.single { it.id == "spotlight:settings" }.title == "Spotlight settings")
        check(Spotlight.commands.single { it.id == "spotlight:minecraft_settings" }.title == "Minecraft settings")
        for (id in listOf("light_level", "biome", "chunk_info", "dimension_info", "world_time", "heightmap")) {
            check(Spotlight.commands.single { it.id == "spotlight:$id" }.input == CommandInput.NONE)
        }
        for ((id, title) in listOf("sound_manager" to "Sounds", "music_player" to "Music player", "current_song" to "Current song")) {
            val audio = Spotlight.commands.single { it.id == "spotlight:$id" }
            check(audio.title == title && audio.input == CommandInput.NONE)
            check(audio.unavailable() == null)
        }
        check(Spotlight.commands.single { it.id == "spotlight:sound_manager" }.description == "Manage playing sounds stop instance or manually play")
        val themes = Spotlight.commands.single { it.id == "spotlight:themes" }
        check(themes.title == "Themes" && themes.input == CommandInput.NONE && themes.unavailable() == null)
        val themeProperties = java.util.Properties().apply {
            load(dev.spotlight.core.ThemeLibrary.serialize(dev.spotlight.core.Theme.defaults).reader())
        }
        val themeValues = themeProperties.stringPropertyNames().associateWith(themeProperties::getProperty)
        check(dev.spotlight.ui.validateThemeDraft("Draft", themeValues) == dev.spotlight.core.Theme.defaults)
        for ((key, value) in listOf("colors.overlay" to "#FFFFFF", "colors.text" to "#GGFFFFFF", "layout.rowHeight" to "21", "layout.widgetHeight" to "41", "layout.paletteWidth" to "160.0", "font" to "bad font")) {
            check(runCatching { dev.spotlight.ui.validateThemeDraft("Draft", themeValues + (key to value)) }.isFailure)
        }
        for (metric in dev.spotlight.core.ThemeMetric.entries) {
            for (number in listOf(metric.min, metric.max)) {
                check(dev.spotlight.ui.validateThemeDraft("Draft", themeValues + ("layout.${metric.key}" to number.toString())).metrics[metric.key] == number)
            }
            for (number in listOf(metric.min - 1, metric.max + 1)) {
                check(runCatching { dev.spotlight.ui.validateThemeDraft("Draft", themeValues + ("layout.${metric.key}" to number.toString())) }.isFailure)
            }
        }
        for (name in listOf("", "x".repeat(65), "bad\nname", "bad\u2028name")) {
            check(runCatching { dev.spotlight.ui.validateThemeDraft(name, themeValues) }.isFailure)
        }
        check(runCatching { dev.spotlight.ui.validateThemeDraft("Draft", themeValues - "colors.overlay") }.isFailure)
        check(runCatching { dev.spotlight.ui.validateThemeDraft("Draft", themeValues + ("unknown" to "1")) }.isFailure)
        check(dev.spotlight.ui.themePickerArgb(0x12345678, 0xABCDEF) == 0x12ABCDEF)
        check(dev.spotlight.ui.themePickerArgb(0x00345678, 0xABCDEF) == 0x00ABCDEF)
        val recipes = Spotlight.commands.single { it.id == "spotlight:recipes" }
        check(recipes.title == "Crafting recipes" && recipes.input == CommandInput.NONE)
        dev.spotlight.ui.RecipeScreenCheck.main(emptyArray())
        for (id in listOf("fill", "clone")) {
            val region = Spotlight.commands.single { it.id == "spotlight:$id" }
            check(region.title == id && region.input == CommandInput.NONE)

        }
        check(fillModes(emptyList()) == listOf("replace", "destroy", "keep", "hollow", "outline"))
        check(fillModes(listOf("outline", "keep", "replace", "strict", "unknown")) == listOf("replace", "keep", "outline"))
        check(fillModes(listOf("unknown")) == fillModes(emptyList()))
        check(fillModes(emptyList()).all { fillModeHelp(it).isNotBlank() })
        check(cloneMasks == listOf("replace", "masked"))
        check(cloneModes == listOf("normal", "force", "move"))
        check(chunkInfo(BlockPos(-1, -65, -513)) == "chunk X/Z=-1, -33; sectionY=-5; local block X/Y/Z=15, 15, 15; region file=r.-1.-2.mca; region-local chunk X/Z=31, 31")
        check(chunkInfo(BlockPos(512, 64, 511)) == "chunk X/Z=32, 31; sectionY=4; local block X/Y/Z=0, 0, 15; region file=r.1.0.mca; region-local chunk X/Z=0, 31")
        check(chunkInfo(BlockPos.ZERO) == "chunk X/Z=0, 0; sectionY=0; local block X/Y/Z=0, 0, 0; region file=r.0.0.mca; region-local chunk X/Z=0, 0")
        val aliasDirectory = Files.createTempDirectory("spotlight-alias-check")
        val aliasPath = aliasDirectory.resolve("commands.dat")
        try {
            val settings = CommandSettings(aliasPath)
            val modes = listOf("survival", "creative", "adventure", "spectator")
            val shortNames = listOf("gms", "gmc", "gma", "gmsp")
            check(builtInAliases.size == 8)
            check(runCatching { (builtInAliases as MutableMap<String, String>).clear() }.isFailure)
            for ((index, mode) in modes.withIndex()) {
                val id = "spotlight:gamemode_$mode"
                val gamemode = Spotlight.commands.single { it.id == id }
                check(gamemode.title == "Gamemode: ${mode.replaceFirstChar { it.uppercaseChar() }}")
                check(gamemode.input == CommandInput.NONE)
                for (alias in listOf(shortNames[index], "gm$index")) {
                    check(fallbackAlias(" ${alias.uppercase(java.util.Locale.ROOT)} ", settings.aliases) == id)
                    check(Spotlight.builtinAlias(alias) == id)
                    check(alias in aliasesForCommand(id, settings.aliases))
                    check(alias in gamemode.description)
                }
            }
            for (invalid in listOf("", "gm", "gm4", "/gmc", "gmc extra", "gmc\ngm1")) {
                check(fallbackAlias(invalid, settings.aliases) == null)
            }
            check(!Files.exists(aliasPath))
            settings.setAlias("check:registry", "gmc")
            settings.setAlias("spotlight:gamemode_creative", "mycreative")
            val saved = Files.readAllBytes(aliasPath)
            check(fallbackAlias(" GMC ", settings.aliases) == "check:registry")
            check(fallbackAlias("gm1", settings.aliases) == "spotlight:gamemode_creative")
            check(fallbackAlias("mycreative", settings.aliases) == "spotlight:gamemode_creative")
            check(aliasesForCommand("spotlight:gamemode_creative", settings.aliases) == listOf("mycreative", "gm1"))
            check(aliasesForCommand("check:registry", settings.aliases) == listOf("gmc"))
            check(aliasesForCommand("check:missing", settings.aliases).isEmpty())
            check(Files.readAllBytes(aliasPath).contentEquals(saved))
            check(CommandSettings(aliasPath).aliases == settings.aliases)
        } finally {
            Files.deleteIfExists(aliasPath)
            Files.deleteIfExists(aliasDirectory)
        }
        val seed = Spotlight.commands.single { it.id == "spotlight:seed" }
        check(seed.title == "seed" && seed.input == CommandInput.NONE)
        val uuid = Spotlight.commands.single { it.id == "spotlight:uuid" }
        check(uuid.title == "uuid" && uuid.input == CommandInput.NONE)
        val ping = Spotlight.commands.single { it.id == "spotlight:ping" }
        check(ping.title == "Ping" && ping.input == CommandInput.NONE)
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val entityId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val loadedPlayer = Entry(playerId.toString(), "Loaded player", "minecraft:player")
        val entity = Entry(entityId.toString(), "Pet", "minecraft:wolf")
        val uuids = uuidEntries(listOf(playerId to "Online player"), listOf(loadedPlayer, entity, entity))
        check(uuids == listOf(Entry(playerId.toString(), "Online player", "minecraft:player"), entity))
        check(uuidEntries(listOf(playerId to "Unloaded player"), emptyList()).single().id == playerId.toString())
        check(uuidEntries(emptyList(), listOf(loadedPlayer)).single() == loadedPlayer)
        check(uuidEntries(emptyList(), emptyList()).isEmpty())
        check(Fuzzy.search("Online", uuids).first().id == playerId.toString())
        check(Fuzzy.search("wolf", uuids).single() == entity)
        check(Fuzzy.search(entityId.toString(), uuids).first() == entity)
        val pings = pingEntries(listOf(
            Triple(entityId, "Zulu", -1), Triple(playerId, "alice", 0),
            Triple(UUID(0, 3), "Bob", 125), Triple(UUID(0, 4), "Carol", Int.MIN_VALUE),
        ))
        check(pings.map { it.title } == listOf("alice", "Bob", "Carol", "Zulu"))
        check(pings.map { it.detail } == listOf("0 ms", "125 ms", "Unknown", "Unknown"))
        check(pings.first().id == playerId.toString())
        check(pingEntries(emptyList()).isEmpty())
        check(pingEntries(listOf(Triple(playerId, "Self", 42))).single() == Entry(playerId.toString(), "Self", "42 ms"))
        check(Fuzzy.search("bob", pings).single().title == "Bob")
        check(Fuzzy.search("missing", pings).isEmpty())
        val manyPings = pingEntries((1..100).map { Triple(UUID(0, it.toLong()), "Player$it", it) })
        check(Fuzzy.search("", manyPings, limit = manyPings.size).size == 100)
        check(pingEntries(listOf(Triple(playerId, "Self", 99))).single().detail == "99 ms")
        check(Spotlight.commands.single { it.id == "spotlight:add_location" }.input == CommandInput.NONE)
        check(Spotlight.commands.single { it.id == "spotlight:spawn_location" }.title == "Spawn location")
        val spawnDraft = LocationActions.spawnDraft(BlockPos(0, 64, -12), Level.NETHER, "world-a")
        check(spawnDraft.id == "spawn" && spawnDraft.name == "Spawnpoint location")
        check(spawnDraft.dimension == "minecraft:the_nether" && spawnDraft.scope == "world-a")
        check(spawnDraft.x == 0.0 && spawnDraft.y == 64.0 && spawnDraft.z == -12.0)
        val directory = Files.createTempDirectory("spotlight-location-check")
        val path = directory.resolve("locations.dat")
        try {
            val store = LocationStore(path)
            val custom = spawnDraft.copy(id = "custom", name = "Home")
            store.put(custom)
            LocationActions.saveSpawn(store, spawnDraft)
            LocationActions.saveSpawn(store, spawnDraft.copy(x = 123.0))
            LocationActions.saveSpawn(store, spawnDraft.copy(scope = "world-b", x = 456.0))
            check(store.list("world-a").toSet() == setOf(custom, spawnDraft))
            check(LocationStore(path).list("world-a").toSet() == setOf(custom, spawnDraft))
            check(store.list("world-b").single().x == 456.0)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
        ClientDebugCheck.run()
        println("Platform checks passed")
    }
}
