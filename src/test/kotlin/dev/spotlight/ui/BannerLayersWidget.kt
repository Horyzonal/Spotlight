package dev.spotlight.ui

object BannerLayersCheck {
    @JvmStatic fun main(args: Array<String>) {
        val layers = mutableListOf("bottom", "middle", "top")
        check(moveBannerLayer(layers, 0, 2) == 2 && layers == listOf("middle", "top", "bottom"))
        check(moveBannerLayer(layers, 2, 0) == 0 && layers == listOf("bottom", "middle", "top"))
        check(moveBannerLayer(layers, 0, -1) == 0 && layers.first() == "bottom")
        check(moveBannerLayer(layers, 2, 3) == 2 && layers.last() == "top")
        check(moveBannerLayer(layers, 1, 1) == 1 && layers == listOf("bottom", "middle", "top"))
        check(moveBannerLayer(mutableListOf<String>(), -1, 0) == -1)
        println("Banner layer reorder and boundary checks passed")
    }
}
