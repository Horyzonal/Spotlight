package dev.spotlight.ui

import com.mojang.blaze3d.font.GlyphInfo
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.util.RandomSource
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

internal object MultilineInputCheck {
    @JvmStatic fun main(args: Array<String>) {
        val glyph = object : BakedGlyph {
            override fun info() = GlyphInfo.simple(6f)
            override fun createGlyph(x: Float, y: Float, color: Int, shadowColor: Int, style: Style, boldOffset: Float, shadowOffset: Float) =
                error("Cursor check must not render")
        }
        val source = object : GlyphSource {
            override fun getGlyph(codepoint: Int) = glyph
            override fun getRandomGlyph(random: RandomSource, codepoint: Int) = glyph
        }
        val font = Font(object : Font.Provider {
            override fun glyphs(description: FontDescription) = source
            override fun effect(): EffectGlyph = error("Cursor check must not render")
        })
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
        check(field.keyPressed(KeyEvent(InputConstants.KEY_RIGHT, 0, 0)))
        check(!field.hasSelection())
        check(field.keyPressed(KeyEvent(InputConstants.KEY_LEFT, 0, GLFW.GLFW_MOD_SHIFT)))
        check(field.hasSelection())
        field.setSelecting(false)
        field.seekCursorToPoint(999.0, 999.0)
        check(field.cursor() == field.value().length)
        println("Multiline pixel coordinates, line height, selection and GLFW key checks passed")
    }
}
