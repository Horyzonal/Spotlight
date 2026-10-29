package dev.spotlight.ui

object NowPlayingWidgetCheck {
    @JvmStatic fun main(args: Array<String>) {
        for (panel in 1..2048) {
            val layout = nowPlayingLayout(panel)
            check(layout.textWidth >= 0 && layout.gap >= 0 && layout.widgetWidth in 0..180)
            check(layout.textWidth + layout.gap + layout.widgetWidth == (panel - 24).coerceAtLeast(0))
            if (panel >= 264) check(layout.widgetWidth <= panel / 3)
            else check(layout.widgetWidth <= 32)
            if (panel >= 58) check(layout.widgetWidth >= 32 && layout.textWidth > 0)
        }
        check(nowPlayingLayout(560) == NowPlayingLayout(348, 8, 180))
        check(nowPlayingLayout(160) == NowPlayingLayout(96, 8, 32))
        println("NowPlayingWidgetCheck passed")
    }
}
