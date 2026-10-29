package dev.spotlight.ui

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.platform.PlayingSound

internal object SoundMenuCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(soundRowClick(InputConstants.MOUSE_BUTTON_LEFT))
        check(!soundRowClick(InputConstants.MOUSE_BUTTON_RIGHT))
        val first = PlayingSound("sound-1", "minecraft:music.test", "music", 0.75f, 1.25f, true)
        val second = first.copy(id = "sound-2")
        check(soundRow(first).contains("music · volume 0.75 · pitch 1.25 · loop"))
        check(soundRow(first) != soundRow(second))
        check(soundSelection(listOf(first.id, second.id), second.id) == second.id)
        check(soundSelection(listOf(first.id), second.id) == first.id)
        check(soundSelection(emptyList(), first.id) == null)
        check(soundMatches("mct", listOf(first.id to first.name, second.id to second.name)).map { it.first } == listOf(first.id, second.id))
        check(soundMatches("absent", listOf(first.id to first.name)).isEmpty())
        check(soundNumber("0", 0f..1f) == 0f && soundNumber("1", 0f..1f) == 1f)
        check(soundNumber("0.5", 0.5f..2f) == 0.5f && soundNumber("2", 0.5f..2f) == 2f)
        for (text in listOf("", "NaN", "Infinity", "-1", "1.1")) check(soundNumber(text, 0f..1f) == null)
        check(soundNumber("0.49", 0.5f..2f) == null && soundNumber("2.01", 0.5f..2f) == null)
        println("Sound menu format, identity selection, fuzzy search and numeric checks passed")
    }
}
