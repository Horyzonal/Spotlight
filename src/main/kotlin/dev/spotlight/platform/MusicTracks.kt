package dev.spotlight.platform

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import dev.spotlight.core.MusicMetadata
import dev.spotlight.core.MusicTrack
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundEventRegistration
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat
import org.slf4j.LoggerFactory
import java.io.Reader

private val musicLogger = LoggerFactory.getLogger("spotlight.music")
private val soundDefinitions = GsonBuilder().registerTypeAdapter(
    SoundEventRegistration::class.java, SoundEventRegistrationSerializer(),
).create()
private val definitionType = object : TypeToken<Map<String, SoundEventRegistration>>() {}.type

internal fun readMusicDefinitions(reader: Reader): Map<String, SoundEventRegistration> {

    val text = reader.readTextBounded(8 * 1024 * 1024)
    return soundDefinitions.fromJson<Map<String, SoundEventRegistration>>(text, definitionType) ?: emptyMap()
}

internal fun Reader.readTextBounded(limit: Int): String {
    val text = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return text.toString()
        require(text.length + count <= limit) { "sounds.json exceeds $limit characters" }
        text.append(buffer, 0, count)
    }
}

internal fun mergeMusicDefinitions(
    target: MutableMap<Identifier, List<Sound>>,
    namespace: String,
    definitions: Map<String, SoundEventRegistration>,
) {
    definitions.forEach { (name, registration) ->
        val id = Identifier.fromNamespaceAndPath(namespace, name)
        target[id] = (if (registration.isReplace) emptyList() else target[id].orEmpty()) + registration.sounds
    }
}

internal fun loadedMusicTracks(resources: ResourceManager, manager: SoundManager): List<MusicTrack> {
    val definitions = linkedMapOf<Identifier, List<Sound>>()
    for (namespace in resources.namespaces) {

        for (resource in resources.getResourceStack(Identifier.fromNamespaceAndPath(namespace, "sounds.json"))) {
            try {
                val parsed = resource.openAsReader().use(::readMusicDefinitions)
                mergeMusicDefinitions(definitions, namespace, parsed)
            } catch (failure: Exception) {
                musicLogger.warn("Cannot read music definitions from {}:{}", resource.sourcePackId(), namespace, failure)
            }
        }
    }
    return resolveMusicTracks(definitions, manager.availableSounds.toSet()) { sound ->
        resources.getResource(sound.path).isPresent
    }
}

internal fun resolveMusicTracks(
    definitions: Map<Identifier, List<Sound>>,
    loadedEvents: Set<Identifier>,
    exists: (Sound) -> Boolean,
): List<MusicTrack> {
    val recordings = linkedMapOf<String, MutableSet<String>>()
    for ((root, sounds) in definitions) {
        if (root !in loadedEvents) continue
        var remaining = 100_000
        val visiting = mutableSetOf<Identifier>()
        fun visit(event: Identifier, music: Boolean, depth: Int) {
            if (depth > 64 || --remaining < 0 || event !in loadedEvents || !visiting.add(event)) return
            for (sound in definitions[event].orEmpty()) {
                if (--remaining < 0) break
                if (sound.type == Sound.Type.SOUND_EVENT) {
                    visit(sound.location, music || isMusicSound(sound.location.toString()), depth + 1)
                } else if ((music || isMusicSound(sound.location.toString())) && exists(sound)) {
                    recordings.getOrPut(sound.location.toString(), ::linkedSetOf).add(root.toString())
                }
            }
            visiting.remove(event)
        }
        if (sounds.isNotEmpty()) visit(root, isMusicSound(root.toString()), 0)
    }
    return recordings.map { (file, events) -> MusicMetadata.track(file, events) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}


internal class TrackSoundInstance(file: Identifier) : AbstractSoundInstance(
    file, SoundSource.MUSIC, SoundInstance.createUnseededRandom(),
) {
    private val trackEvent: WeighedSoundEvents

    init {
        looping = true
        relative = true
        attenuation = SoundInstance.Attenuation.NONE
        sound = Sound(file, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, true, false, 16)
        trackEvent = WeighedSoundEvents(file, null).also { it.addSound(requireNotNull(sound)) }
    }

    override fun resolve(manager: SoundManager): WeighedSoundEvents = trackEvent
}
