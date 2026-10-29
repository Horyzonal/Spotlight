package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.core.ColorPicker
import dev.spotlight.core.GradientStop

internal object GradientTimelineCheck {
    @JvmStatic fun main(args: Array<String>) {
        val stops = ColorPicker.positionedStops(listOf(0, 0xFF0000, 0xFFFFFF))
        fun evenlySpaced(list: List<GradientStop>) = check(list == ColorPicker.positionedStops(list.map { it.color }))
        val crossed = timelineMove(stops, 0, 2)
        check(crossed.second == 2 && crossed.first.map { it.color } == listOf(0xFF0000, 0xFFFFFF, 0))
        evenlySpaced(crossed.first)
        val back = timelineMove(crossed.first, crossed.second, -1)
        check(back.second == 0 && back.first == stops)
        check(timelineMove(stops, 0, 99) == crossed)
        check(timelineMove(stops, 1, 1).first == stops)
        val duplicates = ColorPicker.positionedStops(listOf(1, 1, 2))
        check(timelineMove(duplicates, 1, 2).first.map { it.color } == listOf(1, 2, 1))
        val inserted = timelineInsertion(stops, 0)
        check(inserted.map { it.color } == listOf(0, 0x800000, 0xFF0000, 0xFFFFFF))
        evenlySpaced(inserted)
        check(timelineInsertion(stops, 2).map { it.color } == listOf(0, 0xFF0000, 0xFFFFFF, 0xFFFFFF))
        check(timelineNormalize(listOf(GradientStop(7, 0.4))) == listOf(GradientStop(7, 0.0)))
        check(timelineInsertion(listOf(GradientStop(7, 0.0)), 0) == ColorPicker.positionedStops(listOf(7, 7)))
        val custom = listOf(GradientStop(1, 0.2), GradientStop(2, 0.2), GradientStop(3, 0.9))
        check(timelineNormalize(custom) == ColorPicker.positionedStops(listOf(1, 2, 3)))
        for (count in 1..16) {
            val list = ColorPicker.positionedStops((0 until count).toList())
            evenlySpaced(timelineMove(list, 0, count - 1).first)
            val added = timelineInsertion(list, 0)
            check(added.size == (count + 1).coerceAtMost(16))
            evenlySpaced(added)
            if (count > 1) evenlySpaced(timelineNormalize(list.drop(1)))
        }
        check(InputConstants.MOUSE_BUTTON_LEFT == 1 && InputConstants.MOUSE_BUTTON_RIGHT == 3)
        println("Color list insertion reorder, snapping, normalization, limits and SDL button checks passed")
    }
}
