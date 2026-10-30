package dev.spotlight.platform

import com.google.gson.Gson
import com.mojang.serialization.JsonOps
import dev.spotlight.core.ColoredRun
import dev.spotlight.core.StyledText
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component

import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack

import net.minecraft.world.level.GameType

object CreativeText {
    const val MAX_LORE_LINES = 256
    fun component(text: StyledText): Component {
        val style = text.style
        val root = Component.literal("").setStyle(Style.EMPTY
            .withBold(style.bold).withItalic(style.italic).withUnderlined(style.underlined)
            .withStrikethrough(style.strikethrough).withObfuscated(style.obfuscated))
        for (run in text.runs) {
            root.append(Component.literal(run.text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(run.color))))
        }
        return root
    }

    fun loreLines(text: StyledText): List<Component> {
        val lines = mutableListOf<Component>()
        val runs = mutableListOf<ColoredRun>()
        for (run in text.runs) {
            val parts = run.text.split('\n')
            for ((index, part) in parts.withIndex()) {
                if (index > 0) {
                    lines += component(StyledText(runs.toList(), text.style))
                    require(lines.size < MAX_LORE_LINES) { "Lore exceeds $MAX_LORE_LINES lines" }
                    runs.clear()
                }
                if (part.isNotEmpty()) runs += ColoredRun(part, run.color)
            }
        }
        lines += component(StyledText(runs.toList(), text.style))
        return lines
    }

    fun applyName(text: StyledText) {
        val name = component(text)
        edit(listOf(name)) { setName(it, name) }
    }

    fun applyLore(text: StyledText) {
        val lines = loreLines(text)
        edit(lines) { setLore(it, lines) }
    }

    fun makeUnbreakable() { edit(emptyList(), ::setUnbreakable) }

    fun clearName() { edit(emptyList()) { clearDisplayKey(it, "Name") } }

    fun clearLore() { edit(emptyList()) { clearDisplayKey(it, "Lore") } }

    internal fun setName(stack: ItemStack, name: Component) {
        validateTextSize(listOf(name))
        stack.getOrCreateTagElement("display").putString("Name", Component.Serializer.toJson(name))
    }

    internal fun setLore(stack: ItemStack, lines: List<Component>) {
        require(lines.size <= MAX_LORE_LINES) { "Lore exceeds $MAX_LORE_LINES lines" }
        validateTextSize(lines)
        stack.getOrCreateTagElement("display").put("Lore", ListTag().also { lore ->
            lines.forEach { lore.add(StringTag.valueOf(Component.Serializer.toJson(it))) }
        })
    }

    internal fun setUnbreakable(stack: ItemStack) { stack.orCreateTag.putBoolean("Unbreakable", true) }

    internal fun clearDisplayKey(stack: ItemStack, key: String) {
        require(key == "Name" || key == "Lore")
        val display = stack.getTagElement("display") ?: return
        display.remove(key)
        if (display.isEmpty) stack.removeTagKey("display")
    }

    internal fun itemDataJson(stack: ItemStack): String {
        require(!stack.isEmpty) { "Hold an item in your main hand" }
        return Gson().toJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, stack.save(CompoundTag())))
    }

    fun copyItemData(): String {
        val client = Minecraft.getInstance()
        requireNotNull(client.level) { "Join a world first" }
        val stack = requireNotNull(client.player) { "Player unavailable" }.mainHandItem.copy()
        require(!stack.isEmpty) { "Hold an item in your main hand" }
        return itemDataJson(stack)
    }

    internal fun validateTextSize(lines: List<Component>) {
        val json = lines.map(Component.Serializer::toJsonTree)
        require(Gson().toJson(json).toByteArray(Charsets.UTF_8).size <= 32 * 1024) {
            "Formatted text exceeds 32 KiB serialized UTF-8"
        }
    }

    private fun edit(lines: List<Component>, change: (ItemStack) -> Unit) {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Item editing requires the client thread" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val level = requireNotNull(client.level) { "Join a world first" }
        val gameMode = requireNotNull(client.gameMode) { "Game mode unavailable" }
        val connection = requireNotNull(client.connection) { "Server connection unavailable" }
        fun validateState() {
            check(client.player === player && client.level === level && client.gameMode === gameMode &&
                client.connection === connection && connection.connection.isConnected) { "World or connection changed; try again" }
            check(gameMode.playerMode == GameType.CREATIVE && player.abilities.instabuild) { "Creative mode required" }
            check(player.containerMenu === player.inventoryMenu) { "Close the open container first" }
        }
        validateState()
        val inventory = player.inventory
        val slot = inventory.selected
        check(slot in 0..8) { "Select a hotbar slot" }
        val original = inventory.getItem(slot).copy()
        require(!original.isEmpty) { "Hold an item in your main hand" }
        val changed = original.copy()
        change(changed)
        validateTextSize(lines)

        require(changed.count in 1..changed.maxStackSize && changed.isItemEnabled(level.enabledFeatures())) {
            "Held item is not valid for this server"
        }
        changed.save(CompoundTag())
        validateState()
        check(inventory.selected == slot && ItemStack.matches(inventory.getItem(slot), original)) {
            "Held item changed; try again"
        }
        gameMode.handleCreativeModeItemAdd(changed, slot + 36)
        inventory.setItem(slot, changed)
        inventory.setChanged()
    }
}
