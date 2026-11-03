package dev.spotlight.platform

import dev.spotlight.ui.InventoryStorageButtons
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.neoforged.neoforge.client.event.ScreenEvent
import sun.misc.Unsafe

object NeoForgeHooksCheck {
    @JvmStatic fun main(args: Array<String>) {
        val access = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = access.get(null) as Unsafe
        val inventory = unsafe.allocateInstance(InventoryScreen::class.java) as InventoryScreen
        val listeners = mutableListOf<GuiEventListener>()
        val event = ScreenEvent.Init.Post(inventory, listeners, { listeners.add(it) }, { listeners.remove(it) })
        InventoryStorageButtons.attach(event)
        check(listeners.filterIsInstance<Button>().map { it.message.string } == listOf("Client chest", "Saved kits", "Item archive"))
        check(listeners.filterIsInstance<Button>().all { it.active && it.visible && it.width == 104 })
        InventoryStorageButtons.attach(event)
        check(listeners.size == 3)
        val other = unsafe.allocateInstance(net.minecraft.client.gui.screens.TitleScreen::class.java) as Screen
        InventoryStorageButtons.attach(ScreenEvent.Init.Post(other, listeners, { error("Non-inventory button") }, {}))
        val spotlight = InstalledMods.search("spotlight").single { it.id == "spotlight" }
        check(spotlight.version == "1.0.1")
        check(InstalledMods.search("SPOTLIGHT").any { it.id == "spotlight" })
        check(!InstalledMods.hasConfigScreen("missing:mod"))
        check(InstalledMods.configScreen("missing:mod", other) == null)
        check(InstalledMods.webLink("https://example.com/path") != null)
        for (link in listOf("file:///tmp/test", "javascript:alert(1)", "https://user@example.com", "https:///path")) check(InstalledMods.webLink(link) == null)
        println("NeoForge hooks passed: inventory injection, duplicate guard, screen isolation, native mod metadata, absent configs and safe links")
    }
}
