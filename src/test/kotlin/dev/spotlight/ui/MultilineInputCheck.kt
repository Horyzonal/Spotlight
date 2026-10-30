package dev.spotlight.ui

import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.font.SheetGlyphInfo
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.components.Whence
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import java.util.function.Function

internal object MultilineInputCheck {
    @JvmStatic fun main(args: Array<String>) {
        val glyph = object : GlyphInfo {
            override fun getAdvance() = 6f
            override fun bake(factory: Function<SheetGlyphInfo, BakedGlyph>): BakedGlyph = error("Cursor check must not render")
        }
        val source = object : FontSet(null, ResourceLocation("test")) {
            override fun getGlyphInfo(codepoint: Int, filterFishyGlyphs: Boolean) = glyph
        }
        val font = Font({ source }, false)
        val field = MultilineTextField(font, 120)
        field.setValue("abc\ndef")
        check(field.cursor() == 7)
        field.seekCursorToPoint(6.0, 0.0)
        check(field.cursor() == 1)
        field.seekCursorToPoint(12.0, font.lineHeight.toDouble())
        check(field.cursor() == 6)
        field.setSelecting(true)
        field.seekCursorToPoint(0.0, 0.0)
        check(field.hasSelection())
        field.setSelecting(false)
        field.seekCursor(Whence.ABSOLUTE, field.value().length)
        check(field.cursor() == field.value().length)
        check(KeyEvent(InputConstants.KEY_LEFT, 0, GLFW.GLFW_MOD_SHIFT).hasShiftDown())
        check(!KeyEvent(InputConstants.KEY_LEFT, 0, 0).hasShiftDown())
        check(KeyEvent(InputConstants.KEY_NUMPADENTER, 0, 0).isConfirmation)
        check(KeyEvent(InputConstants.KEY_ESCAPE, 0, 0).isEscape)
        println("Multiline pixel coordinates, line height, selection and GLFW event checks passed")
    }
}
