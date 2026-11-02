package dev.spotlight.ui

import dev.spotlight.platform.ClientItemStorage
import net.minecraft.client.gui.screens.Screen

class KitScreen(parent: Screen? = null) : StorageScreen(parent, "Saved kits · exact inventory snapshots") {
    private var offset = 0
    private var selected: String? = null
    private var query = ""
    override fun build() {
        row("Save inventory as kit", "Back") { if (it == 0) prompt("New kit name", hint = "Saves inventory and equipment copies; existing names never overwritten") { ClientItemStorage.saveKit(it) } else onClose() }
        row("Search kits", "Last restore backup") { index ->
            if (index == 0) prompt("Search kit names", query) { query = it; offset = 0; rebuild() }
            else ClientItemStorage.restoreLastKitBackup()
        }
        val kits = ClientItemStorage.kits().entries.filter { query.isBlank() || it.value.getString("Name").contains(query, true) }.sortedBy { it.value.getString("Name") }
        kits.drop(offset).take(4).forEach { (id, kit) -> row(kit.getString("Name")) { selected = id; rebuild() } }
        row("Previous", "Next") { if (it == 0) offset = (offset - 4).coerceAtLeast(0) else if (offset + 4 < kits.size) offset += 4; rebuild() }
        selected?.let { id -> kits.firstOrNull { it.key == id }?.let { (_, kit) ->
            text("${kit.getString("Name")} · read-only preview; hover for components")
            val full = ClientItemStorage.isFullKit(kit)
            text(if (full) "Full snapshot · empty slots clear on restore" else "Legacy partial · unsaved armor/offhand unchanged")
            val saved = ClientItemStorage.items(kit)
            val top = cursorY
            fun slot(index: Int, x: Int, y: Int, label: String, sprite: String? = null) {
                var error: String? = null
                val stack = saved[index]?.let { raw -> try { ClientItemStorage.decode(raw) } catch (e: Exception) { error = e.message; null } } ?: net.minecraft.world.item.ItemStack.EMPTY
                addRenderableWidget(KitInventoryWidget(x, y, stack, error ?: label, sprite))
            }
            listOf("Head", "Chest", "Legs", "Feet").forEachIndexed { index, label ->
                slot(39 - index, left, top + index * 20, if (full || 39 - index in saved) label else "$label: not saved in legacy kit", listOf("helmet", "chestplate", "leggings", "boots")[index])
            }
            slot(40, left + 200, top + 60, if (full || 40 in saved) "Offhand" else "Offhand: not saved in legacy kit", "shield")
            repeat(27) { index -> slot(index + 9, left + 20 + index % 9 * 20, top + index / 9 * 20, "Storage slot ${index + 1}") }
            repeat(9) { index -> slot(index, left + 20 + index * 20, top + 64, "Hotbar slot ${index + 1}") }
            cursorY += 88
            row("Replace saved kit with current inventory") {
                minecraft!!.setScreen(net.minecraft.client.gui.screens.ConfirmScreen({ accepted ->
                    minecraft!!.setScreen(this)
                    if (accepted) attempt {
                        ClientItemStorage.replaceKit(id)
                        notice = "Saved kit replaced with current inventory"
                        rebuild()
                    }
                }, net.minecraft.network.chat.Component.literal("Replace saved kit: ${kit.getString("Name")}?"),
                    net.minecraft.network.chat.Component.literal("Overwrites this saved kit with your current inventory, armor and offhand. Your inventory and kit hotkeys stay unchanged."),
                    net.minecraft.network.chat.Component.literal("Replace saved kit"), net.minecraft.network.chat.Component.literal("Cancel")))
            }
            row("Restore kit (Creative; overwrites slots)") { ClientItemStorage.restoreKit(id) }
            row("Delete kit") {
                minecraft!!.setScreen(net.minecraft.client.gui.screens.ConfirmScreen({ accepted ->
                    minecraft!!.setScreen(this)
                    if (accepted) attempt {
                        ClientItemStorage.deleteKit(id)
                        selected = null
                        offset = 0
                        notice = "Saved kit deleted"
                        rebuild()
                    }
                }, net.minecraft.network.chat.Component.literal("Delete kit: ${kit.getString("Name")}?"),
                    net.minecraft.network.chat.Component.literal("Deletes this saved kit and removes its restore command. Your current inventory is not changed."),
                    net.minecraft.network.chat.Component.literal("Delete kit"), net.minecraft.network.chat.Component.literal("Cancel")))
            }
        } }
        text("Each kit exposes kit:<SHA-256> command for existing hotkey settings.")
    }
}
