package dev.spotlight.platform

import dev.spotlight.core.MusicMetadata
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat

object MusicTracksCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val definitions = linkedMapOf<Identifier, List<Sound>>()
        fun merge(json: String) = mergeMusicDefinitions(definitions, "test", json.reader().use(::readMusicDefinitions))
        merge("""{"music.root":{"sounds":[{"name":"test:nested","type":"event"},"test:music/old"]},"nested":{"sounds":["minecraft:music/game/infinite_amethyst",{"name":"test:music.root","type":"event"}]},"music.other":{"sounds":["minecraft:music/game/infinite_amethyst","mod:music/new_song"]}}""")
        merge("""{"music.root":{"replace":true,"sounds":[{"name":"test:nested","type":"event"} ]},"music.other":{"sounds":["mod:music/new_song","test:missing"]}}""")
        val tracks = resolveMusicTracks(definitions, definitions.keys) { it.location.toString() != "test:missing" }
        check(tracks.size == 2)
        val amethyst = tracks.single { it.id == "minecraft:music/game/infinite_amethyst" }
        check(amethyst.title == "Infinite Amethyst" && amethyst.artist == "Lena Raine")
        check(amethyst.eventIds.containsAll(setOf("test:music.root", "test:music.other")))
        check(tracks.single { it.id == "mod:music/new_song" }.let { it.title == "New Song" && it.artist == null })
        check(resolveMusicTracks(definitions, emptySet()) { true }.isEmpty())
        check(MusicMetadata.track("mod:music/game/infinite_amethyst", emptySet()).artist == null)
        val instance = TrackSoundInstance(Identifier.parse(amethyst.id))
        val selected = requireNotNull(instance.sound)
                check(selected.location.toString() == amethyst.id && selected.shouldStream())
        check(instance.isLooping && instance.isRelative && instance.source == SoundSource.MUSIC)
        check(instance.attenuation == SoundInstance.Attenuation.NONE)
        val deep = (0..100).associate { index ->
            Identifier.parse("test:music.$index") to listOf(Sound(
                Identifier.parse("test:music.${index + 1}"), ConstantFloat.of(1f), ConstantFloat.of(1f),
                1, Sound.Type.SOUND_EVENT, false, false, 16,
            ))
        }
        check(resolveMusicTracks(deep, deep.keys) { true }.isEmpty())
        check(runCatching { "12345".reader().readTextBounded(4) }.isFailure)
        check("1234".reader().readTextBounded(4) == "1234")
        if (args.isNotEmpty()) {
            val vanilla = linkedMapOf<Identifier, List<Sound>>()
            java.io.File(args.single()).bufferedReader().use {
                mergeMusicDefinitions(vanilla, "minecraft", readMusicDefinitions(it))
            }
            val actual = resolveMusicTracks(vanilla, vanilla.keys) { true }
            check(actual.single { it.id == "minecraft:music/game/infinite_amethyst" }.label == "Infinite Amethyst — Lena Raine")
            println("Vanilla definitions: ${actual.size} recordings, ${actual.count { it.artist != null }} curated credits; remaining artists unknown.")
        }
        println("Music tracks checks passed: exact streaming file, metadata, fallback, deduplication, stack append/replace, missing resources, cycles and depth bounds.")
    }
}
