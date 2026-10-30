package dev.spotlight.platform

import com.google.gson.Gson
import com.mojang.serialization.JsonOps
import dev.spotlight.core.ColoredRun
import dev.spotlight.core.StyledText
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.GameType

object CreativeText {
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
                    require(lines.size < ItemLore.MAX_LINES) { "Lore exceeds ${ItemLore.MAX_LINES} lines" }
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
        edit(listOf(name)) { it.set(DataComponents.CUSTOM_NAME, name) }
    }

    fun applyLore(text: StyledText) {
        val lines = loreLines(text)
        edit(lines) { it.set(DataComponents.LORE, ItemLore(lines)) }
    }

    fun makeUnbreakable() { edit(emptyList()) { it.set(DataComponents.UNBREAKABLE, net.minecraft.world.item.component.Unbreakable(true)) } }

    fun clearName() { edit(emptyList()) { it.remove(DataComponents.CUSTOM_NAME) } }

    fun clearLore() { edit(emptyList()) { it.remove(DataComponents.LORE) } }

    fun copyItemData(): String {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val stack = requireNotNull(client.player) { "Player unavailable" }.mainHandItem.copy()
        require(!stack.isEmpty) { "Hold an item in your main hand" }
        val ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        return Gson().toJson(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow())
    }

    internal fun validateTextSize(lines: List<Component>, registries: HolderLookup.Provider) {
        val ops = registries.createSerializationContext(JsonOps.INSTANCE)
        val json = ComponentSerialization.CODEC.listOf().encodeStart(ops, lines).getOrThrow()
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
        validateTextSize(lines, level.registryAccess())

        require(changed.count in 1..changed.maxStackSize && changed.isItemEnabled(level.enabledFeatures())) {
            "Held item is not valid for this server"
        }
        ItemStack.STRICT_CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), changed).getOrThrow()
        validateState()
        check(inventory.selected == slot && ItemStack.matches(inventory.getItem(slot), original)) {
            "Held item changed; try again"
        }
        gameMode.handleCreativeModeItemAdd(changed, slot + 36)
        inventory.setItem(slot, changed)
        inventory.setChanged()
    }
}
