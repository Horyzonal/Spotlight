package dev.spotlight.core

object ColorPickerCheck {
    @JvmStatic fun main(args: Array<String>) {
        listOf(0.0 to 0xFF0000, 60.0 to 0xFFFF00, 120.0 to 0x00FF00,
            180.0 to 0x00FFFF, 240.0 to 0x0000FF, 300.0 to 0xFF00FF, 360.0 to 0xFF0000).forEach { (hue, color) ->
            check(ColorPicker.rgb(hue, 1.0, 1.0) == color)
            check(ColorPicker.rgb(hue, 0.0, 1.0) == 0xFFFFFF)
            check(ColorPicker.rgb(hue, 1.0, 0.0) == 0)
        }
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val color = (r shl 16) or (g shl 8) or b
            val hsv = ColorPicker.hsv(color)
            check(ColorPicker.rgb(hsv.hue, hsv.saturation, hsv.value) == color)
            check(ColorPicker.parseHex(ColorPicker.hex(color)) == color)
        }
        check(ColorPicker.rgb(-60.0, 1.0, 1.0) == 0xFF00FF)
        check(ColorPicker.parseHex("#ab12EF") == 0xAB12EF)
        listOf("", "#", "123456", "#12345G", "#12345678", " #123456").forEach { check(ColorPicker.parseHex(it) == null) }
        check(ColorPicker.parseRgb(listOf("0", "128", "255")) == 0x0080FF)
        listOf("", "-1", "256", "+1", "1.0", "999999999999999999999").forEach {
            check(ColorPicker.parseRgb(listOf(it, "0", "0")) == null)
        }
        val original = listOf(0, 0xFFFFFF, 0xFF0000)
        check(ColorPicker.add(original, 0) == listOf(0, 0x808080, 0xFFFFFF, 0xFF0000))
        check(ColorPicker.add(original, 2) == original + 0xFF0000)
        check(ColorPicker.add(listOf(0x123456), 0) == listOf(0x123456, 0x123456))
        check(ColorPicker.remove(original, 1) == listOf(0, 0xFF0000))
        check(ColorPicker.move(original, 1, -1) == listOf(0xFFFFFF, 0, 0xFF0000))
        check(ColorPicker.move(original, 1, 1) == listOf(0, 0xFF0000, 0xFFFFFF))
        check(original == listOf(0, 0xFFFFFF, 0xFF0000))
        check(ColorPicker.add(List(15) { it }, 7).size == 16)
        check(runCatching { ColorPicker.add(List(16) { it }, 0) }.isFailure)
        check(runCatching { ColorPicker.remove(listOf(0), 0) }.isFailure)
        check(runCatching { ColorPicker.move(original, 0, -1) }.isFailure)
        check(runCatching { ColorPicker.move(original, 2, 1) }.isFailure)
        check(runCatching { ColorPicker.add(listOf(-1), 0) }.isFailure)
        check(runCatching { ColorPicker.hsv(0x1000000) }.isFailure)
        check(runCatching { ColorPicker.rgb(Double.NaN, 1.0, 1.0) }.isFailure)
        check(ColorPicker.positionedStops(original) == listOf(
            GradientStop(0, 0.0), GradientStop(0xFFFFFF, 0.5), GradientStop(0xFF0000, 1.0)))
        check(ColorPicker.positionedStops(listOf(42)) == listOf(GradientStop(42, 0.0)))
        check(ColorPicker.positionedStops(TextFormatter.parseColors("#000000, FFFFFF")) ==
            listOf(GradientStop(0, 0.0), GradientStop(0xFFFFFF, 1.0)))
        check(ColorPicker.positionedStops(List(16) { it }).last() == GradientStop(15, 1.0))
        for (colors in listOf(emptyList(), List(17) { 0 }, listOf(-1), listOf(0x1000000))) {
            check(runCatching { ColorPicker.positionedStops(colors) }.exceptionOrNull() is IllegalArgumentException)
        }
        println("ColorPickerCheck passed")
    }
}
