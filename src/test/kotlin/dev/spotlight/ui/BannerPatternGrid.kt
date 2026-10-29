package dev.spotlight.ui

object BannerPatternGridCheck {
    @JvmStatic fun main(args: Array<String>) {
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
