package dev.spotlight.platform


object SoundControlsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        for (volume in listOf(0f, 0.5f, 1f)) {
            for (pitch in listOf(0.5f, 1f, 2f)) check(validSoundParameters(volume, pitch))
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.1f, 1.01f)) {
            check(!validSoundParameters(value, 1f))
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.49f, 2.01f)) {
            check(!validSoundParameters(1f, value))
        }
        val loaded = listOf("minecraft:music.menu", "mod:music/track", "mod:ambient.music.night", "mod:music", "mod:block.music", "mod:entity.cat.ambient", "musicmod:block.break", "mod:musical.bell")
        check(loaded.filter(::isMusicSound) == loaded.take(5))
        data class EqualSound(val name: String)
        val first = EqualSound("same")
        val second = EqualSound("same")
        val tokens = SoundIdentityTokens<EqualSound>()
        check(tokens.sync(listOf(first, second)).size == 2)
        val firstId = tokens.id(first)
        val secondId = tokens.id(second)
        check(firstId != secondId)
        check(tokens.instance(firstId) === first && tokens.instance(secondId) === second)
        tokens.stopped(first)
        check(tokens.instance(firstId) == null)
        check(tokens.sync(listOf(first, second)).single() === second)
        check(tokens.id(second) == secondId)
        tokens.sync(emptyList())
        check(tokens.instance(firstId) == null && tokens.instance(secondId) == null)
        tokens.sync(listOf(first))
        check(tokens.id(first) != firstId)
        check(tokens.instance("unknown") == null)
        val liveId = tokens.id(first)
        tokens.setPaused(first, true)
        check(tokens.isPaused(first))
        check(tokens.sync(listOf(first)).single() === first && tokens.isPaused(first))
        check(tokens.id(first) == liveId) 
        tokens.setPaused(first, true)
        check(tokens.isPaused(first))
        tokens.setPaused(first, false)
        check(!tokens.isPaused(first))
        tokens.setPaused(first, true)
        tokens.sync(listOf(first, second))
        check(tokens.latest(listOf(first, second)) === second)
        check(tokens.isPaused(first) && !tokens.isPaused(second))
        tokens.stopped(first)
        check(!tokens.isPaused(first))
        tokens.setPaused(first, true)
        check(!tokens.isPaused(first))
        tokens.setPaused(second, true)
        tokens.sync(emptyList())
        check(!tokens.isPaused(second) && tokens.latest(emptyList()) == null)
        tokens.sync(listOf(second))
        check(!tokens.isPaused(second) && tokens.id(second) != secondId)
        val vanilla = currentSongMetadata("sound-1", "minecraft:music/game/infinite_amethyst", "minecraft:music.game", true, false)
        check(vanilla.title == "Infinite Amethyst" && vanilla.artist == "Lena Raine")
        check(vanilla.id == "sound-1" && vanilla.paused && !vanilla.owned)
        check(vanilla.elapsedSeconds == null && vanilla.durationSeconds == null)
        val owned = currentSongMetadata("sound-2", "mod:music/custom_recording", "minecraft:music.menu", false, true)
        check(owned.title == "Custom Recording" && owned.artist == null && owned.owned && !owned.paused)
        check(currentSongMetadata("sound-3", "mod:music/game/infinite_amethyst", "mod:music", false, false).artist == null)
        println("Sound controls checks passed: bounds, music filtering, identity, exact stop, pause/resume, live cleanup, latest selection, resolved recording metadata and unknown timing.")
    }
}
