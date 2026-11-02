package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object StorageUICheck {
    @JvmStatic fun main(args: Array<String>) {
        check(InputConstants.MOUSE_BUTTON_LEFT == 0)
        check(InputConstants.MOUSE_BUTTON_RIGHT == 1)
        fun mouse(button: Int, x: Double = 12.0, y: Double = 32.0) = MouseButtonEvent(x, y, button)
        fun CopySlot.keyPressed(event: KeyEvent) = keyPressed(event.key(), event.scancode(), event.modifiers())
        fun CopySlot.mouseClicked(event: MouseButtonEvent, doubleClick: Boolean) = mouseClicked(event.x(), event.y(), event.button())
        fun CopySlot.mouseReleased(event: MouseButtonEvent) = mouseReleased(event.x(), event.y(), event.button())
        fun key(code: Int) = KeyEvent(code, code, 0)
        val saved = listOf("1:4 Tools" to net.minecraft.nbt.CompoundTag().apply { putString("id", "minecraft:diamond_sword") },
            "2:8 Blocks" to net.minecraft.nbt.CompoundTag().apply { putString("id", "minecraft:stone") })
        fun name(raw: net.minecraft.nbt.CompoundTag) = if (raw.getString("id").endsWith("sword")) "Diamond Sword" else "Stone"
        check(searchVaultItems("", saved, ::name) == saved)
        check(searchVaultItems("dmnd swrd", saved, ::name).single().first == "1:4 Tools")
        check(searchVaultItems("Blocks", saved, ::name).single().first == "2:8 Blocks")
        check(searchVaultItems("minecraft:stone", saved, ::name).single().second === saved[1].second)
        check(searchVaultItems("missing", saved, ::name).isEmpty())
        var activations = 0
        val slot = CopySlot(10, 30, ItemStack(Items.STONE, 12), "Inventory copy") { activations++ }
        check(slot.mouseClicked(mouse(InputConstants.MOUSE_BUTTON_LEFT), false))
        check(activations == 1)
        slot.mouseReleased(mouse(InputConstants.MOUSE_BUTTON_LEFT))
        check(activations == 1)
        check(!slot.mouseClicked(mouse(InputConstants.MOUSE_BUTTON_RIGHT), false))
        check(!slot.mouseClicked(mouse(2), false))
        check(!slot.mouseClicked(mouse(InputConstants.MOUSE_BUTTON_LEFT, 9.0), false))
        check(activations == 1)
        slot.isFocused = true
        check(slot.keyPressed(key(InputConstants.KEY_RETURN)))
        check(slot.keyPressed(key(InputConstants.KEY_NUMPADENTER)))
        check(slot.keyPressed(key(InputConstants.KEY_SPACE)))
        check(activations == 4)
        slot.active = false
        check(!slot.mouseClicked(mouse(InputConstants.MOUSE_BUTTON_LEFT), false))
        check(!slot.keyPressed(key(InputConstants.KEY_RETURN)))
        slot.active = true
        slot.visible = false
        check(!slot.mouseClicked(mouse(InputConstants.MOUSE_BUTTON_LEFT), false))
        check(!slot.keyPressed(key(InputConstants.KEY_RETURN)))
        check(activations == 4)
        val preview = CopySlot(10, 30, ItemStack(Items.DIAMOND_SWORD), "Equipment preview", false) { error("Read-only kit activated") }
        preview.isFocused = true
        check(preview.isMouseOver(12.0, 32.0))
        check(preview.message.string.contains("Equipment preview") && preview.message.string.contains("×1"))
        for (button in listOf(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT)) {
            check(!preview.mouseClicked(mouse(button), false))
            preview.mouseReleased(mouse(button))
        }
        for (code in listOf(InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE)) check(!preview.keyPressed(key(code)))

        for (button in listOf(InputConstants.MOUSE_BUTTON_LEFT, 2, 3)) check(storageRemovalSlot(400, 240, 10, 30, mouse(button)) == null)
        check(storageRemovalSlot(400, 240, 10, 30, mouse(InputConstants.MOUSE_BUTTON_RIGHT)) == 0)
        for (y in listOf(-1.0, 25.0, 220.0, 239.0)) {
            check(!storageContentContains(400, 240, 12.0, y))
            check(storageGridSlot(400, 240, 10, y.toInt(), 12.0, y) == null)
            check(storageRemovalSlot(400, 240, 10, y.toInt(), mouse(InputConstants.MOUSE_BUTTON_RIGHT, y = y)) == null)
        }
        check(storageContentContains(400, 240, 0.0, 26.0))
        check(!storageContentContains(400, 240, 400.0, 30.0))
        check(storageGridSlot(400, 240, 10, -50, 12.0, 30.0) == 36)
        check(storageGridSlot(400, 240, 10, null, 12.0, 30.0) == null)
        println("StorageUICheck passed: native buttons/keys, viewport routing, single activation, read-only kit preview")
    }
}
