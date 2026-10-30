package dev.spotlight.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack

object BannerPatternGridCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(Minecraft::class.java.getMethod("setScreen", Screen::class.java).returnType == Void.TYPE)
        check(Minecraft::class.java.getField("screen").type == Screen::class.java)
        check(Minecraft::class.java.getMethod("getToastManager").returnType == ToastManager::class.java)
        AbstractContainerWidget::class.java.getConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Component::class.java,
        )
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val registries = net.minecraft.data.registries.VanillaRegistries.createLookup()
        val dyes = DyeColor.entries.associateWith { ItemStack(net.minecraft.world.item.DyeItem.byColor(it)) }
        check(dyes.keys == DyeColor.entries.toSet())
        dyes.forEach { (color, stack) -> check((stack.item as net.minecraft.world.item.DyeItem).dyeColor == color && !stack.isEmpty) }
        check(bannerGridRows(-1) == 0)
        check(bannerGridRows(0) == 0)
        check(bannerGridRows(1) == 1)
        check(bannerGridRows(4) == 1)
        check(bannerGridRows(5) == 2)
        check(bannerGridRows(1000) == 250)
        BannerLayersCheck.main(emptyArray())
        println("Banner grid empty and partial row checks passed")
    }
}
