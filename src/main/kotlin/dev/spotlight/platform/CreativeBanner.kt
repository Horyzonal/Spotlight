package dev.spotlight.platform

import net.minecraft.core.Holder
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import dev.spotlight.commands.creativePlan
import net.minecraft.client.Minecraft

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.BannerItem
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.entity.BannerPattern

data class BannerLayer(val pattern: Holder<BannerPattern>, val color: DyeColor) {
    fun pattern(): Holder<BannerPattern> = pattern
    fun color(): DyeColor = color
}

object CreativeBanner {

    const val MAX_LAYERS = 20

    private fun bannerItem(base: DyeColor): BannerItem =
        BuiltInRegistries.ITEM.firstOrNull { it is BannerItem && it.color == base } as? BannerItem
            ?: error("Banner item unavailable for ${base.name}")

    fun createBanner(base: DyeColor, layers: List<BannerLayer>): ItemStack =
        withLayers(ItemStack(bannerItem(base)), layers)

    fun createShield(base: DyeColor, layers: List<BannerLayer>): ItemStack =
        withLayers(ItemStack(Items.SHIELD).also { it.getOrCreateTagElement("BlockEntityTag").putInt("Base", base.id) }, layers)

    private fun withLayers(stack: ItemStack, layers: List<BannerLayer>): ItemStack {
        require(layers.size <= MAX_LAYERS) { "Editor limit: $MAX_LAYERS layers; remove layers first" }
        stack.getOrCreateTagElement("BlockEntityTag").put("Patterns", ListTag().also { patterns ->
            layers.forEach { layer ->
                patterns.add(CompoundTag().also {
                    it.putString("Pattern", layer.pattern.value().hashname)
                    it.putInt("Color", layer.color.id)
                })
            }
        })
        return stack
    }

    fun layers(stack: ItemStack): List<BannerLayer> {
        val patterns = stack.getTagElement("BlockEntityTag")?.getList("Patterns", 10) ?: return emptyList()
        return (0 until patterns.size).mapNotNull { index ->
            val tag = patterns.getCompound(index)
            BannerPattern.byHash(tag.getString("Pattern"))?.let { BannerLayer(it, DyeColor.byId(tag.getInt("Color"))) }
        }
    }

    fun giveBanner(base: DyeColor, layers: List<BannerLayer>) {
        give(createBanner(base, layers))
    }

    fun giveShield(base: DyeColor, layers: List<BannerLayer>) {
        give(createShield(base, layers))
    }

    private fun give(draft: ItemStack) {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Banner creation requires the client thread" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val level = requireNotNull(client.level) { "Join a world first" }
        val connection = requireNotNull(client.connection) { "Server connection unavailable" }
        val gameMode = requireNotNull(client.gameMode) { "Game mode unavailable" }
        val inventory = player.inventory
        val selectedSlot = inventory.selected
        val existing = (0 until 36).map { inventory.getItem(it).copy() }
        val carried = player.inventoryMenu.carried.copy()
        fun checkState() {
            check(client.player === player && client.level === level && client.connection === connection &&
                client.gameMode === gameMode && player.inventory === inventory && connection.connection.isConnected) {
                "World or connection changed; reopen editor"
            }
            check(gameMode.playerMode == GameType.CREATIVE && player.abilities.instabuild) { "Creative mode required to give" }
            check(player.containerMenu === player.inventoryMenu) { "Close the open container first" }
            check(inventory.selected == selectedSlot &&
                existing.indices.all { ItemStack.matches(inventory.getItem(it), existing[it]) } &&
                ItemStack.matches(player.inventoryMenu.carried, carried)) { "Inventory changed; try again" }
        }
        fun validate(stack: ItemStack) {

            require(stack.count in 1..stack.maxStackSize && stack.isItemEnabled(level.enabledFeatures())) {
                "Created item is not valid for this server"
            }
            stack.save(CompoundTag())
        }
        checkState()
        val incoming = draft.copyWithCount(1)
        validate(incoming)
        val changes = creativePlan(existing, incoming, 1) { inventory.maxStackSize }
        changes.forEach { (_, stack) -> validate(stack) }
        checkState()
        for ((slot, stack) in changes) {
            inventory.setItem(slot, stack)
            gameMode.handleCreativeModeItemAdd(stack.copy(), if (slot < 9) slot + 36 else slot)
        }
        inventory.setChanged()
    }

    fun baseColor(stack: ItemStack): DyeColor {
        require(!stack.isEmpty && (stack.item is BannerItem || stack.item === Items.SHIELD)) {
            "Hold a banner or shield in your main hand"
        }
        return (stack.item as? BannerItem)?.color ?: stack.getTagElement("BlockEntityTag")?.let {
                    if (it.contains("Base", 99)) DyeColor.byId(it.getInt("Base")) else DyeColor.WHITE
                } ?: DyeColor.WHITE
    }

    fun buildBanner(stack: ItemStack, base: DyeColor, patterns: List<BannerLayer>): ItemStack {
        baseColor(stack)
        val result = if (stack.item is BannerItem) {
            ItemStack(bannerItem(base), stack.count).also { it.tag = stack.tag?.copy() }
        } else stack.copy().also { it.getOrCreateTagElement("BlockEntityTag").putInt("Base", base.id) }
        return withLayers(result, patterns)
    }

    class Session {
        private val client = Minecraft.getInstance()
        private val player = requireNotNull(client.player) { "Join a world first" }
        private val level = requireNotNull(client.level) { "Join a world first" }
        private val connection = requireNotNull(client.connection) { "Server connection unavailable" }
        private val gameMode = requireNotNull(client.gameMode) { "Game mode unavailable" }
        private val slot = player.inventory.selected
        private var original = player.mainHandItem.copy()
        val source: ItemStack get() = original.copy()

        init {
            check(client.isSameThread) { "Banner editing requires the client thread" }
            baseColor(original)
        }

        fun unavailableReason(): String? = when {
            client.player !== player || client.level !== level || client.connection !== connection ||
                client.gameMode !== gameMode || !connection.connection.isConnected -> "World or connection changed; reopen editor"
            gameMode.playerMode != GameType.CREATIVE || !player.abilities.instabuild -> "Creative mode required to apply"
            player.containerMenu !== player.inventoryMenu -> "Close the open container first"
            slot !in 0..8 || player.inventory.selected != slot ||
                !ItemStack.matches(player.mainHandItem, original) -> "Held item changed; reopen editor"
            else -> null
        }

        fun apply(base: DyeColor, patterns: List<BannerLayer>) {
            check(client.isSameThread) { "Banner editing requires the client thread" }
            check(unavailableReason() == null) { unavailableReason().orEmpty() }
            val changed = buildBanner(original, base, patterns)

            require(changed.count in 1..changed.maxStackSize && changed.isItemEnabled(level.enabledFeatures())) {
                "Held item is not valid for this server"
            }
            changed.save(CompoundTag())
            check(unavailableReason() == null) { unavailableReason().orEmpty() }
            gameMode.handleCreativeModeItemAdd(changed, slot + 36)
            player.inventory.setItem(slot, changed)
            player.inventory.setChanged()
            original = changed.copy()
        }
    }
}
