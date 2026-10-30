package dev.spotlight.ui


import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack

object BannerPatternGridCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val dyes = DyeColor.entries.associateWith { ItemStack(net.minecraft.world.item.DyeItem.byColor(it)) }
        check(dyes.keys == DyeColor.entries.toSet())
        dyes.forEach { (color, stack) -> check((stack.item as net.minecraft.world.item.DyeItem).dyeColor == color && !stack.isEmpty) }
        check(bannerGridRows(-1) == 0)
        check(bannerGridRows(0) == 0)
        check(bannerGridRows(1) == 1)
        check(bannerGridRows(4) == 1)
        check(bannerGridRows(5) == 2)
        check(bannerGridRows(1000) == 250)
        MultilineInputCheck.main(emptyArray())
        BannerLayersCheck.main(emptyArray())
        println("Banner grid empty and partial row checks passed")
    }
}
