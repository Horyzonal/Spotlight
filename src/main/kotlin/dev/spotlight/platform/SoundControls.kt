package dev.spotlight.platform

import dev.spotlight.core.MusicTrack
import dev.spotlight.mixin.SoundEngineAccessor
import dev.spotlight.mixin.SoundManagerAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.ChannelAccess
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import java.util.Collections
import java.util.IdentityHashMap

data class SoundPosition(val x: Double, val y: Double, val z: Double)

data class PlayingSound(
    val id: String,
    val name: String,
    val category: String,
    val volume: Float,
    val pitch: Float,
    val looping: Boolean,
    val position: SoundPosition? = null,
)


object SoundControls {
    private val identities = SoundIdentityTokens<SoundInstance>()
    private var musicInstance: SoundInstance? = null

    private fun client(): Minecraft = Minecraft.getInstance().also {
        check(it.isSameThread) { "SoundControls requires the Minecraft client thread" }
    }

    private fun channels(): Map<SoundInstance, ChannelAccess.ChannelHandle> {
        val engine = (client().soundManager as SoundManagerAccessor).`spotlight$soundEngine`()
        return (engine as SoundEngineAccessor).`spotlight$instanceToChannel`()
    }

    private fun snapshot(): List<SoundInstance> {
        
        val entries = channels().entries.map { it.key to it.value }
        val active = identities.sync(entries.filterNot { it.second.isStopped }.map { it.first }
            .sortedWith(compareBy({ it.location.toString() }, { it.sound?.location.toString() })))
        
        entries.filter { identities.isPaused(it.first) }.forEach { (_, handle) ->
            handle.execute { it.pause() }
        }
        return active
    }

    fun playing(): List<PlayingSound> = snapshot().map(::describe)

    internal fun currentSong(): CurrentSong? {
        val active = snapshot()
        val instance = musicInstance?.takeIf { owned -> active.any { it === owned } }
            ?: identities.latest(active.filter { it.source == SoundSource.MUSIC })
            ?: return null
        val recording = instance.sound?.location?.toString() ?: return null
        return currentSongMetadata(identities.id(instance), recording, instance.location.toString(),
            identities.isPaused(instance), instance === musicInstance)
    }

    internal fun setPaused(id: String, paused: Boolean): Boolean {
        snapshot()
        val instance = identities.instance(id) ?: return false
        val handle = channels()[instance]?.takeUnless { it.isStopped } ?: return false
        identities.setPaused(instance, paused)
        handle.execute { channel -> if (paused) channel.pause() else channel.unpause() }
        return true
    }

    fun stop(id: String): Boolean {
        snapshot()
        val instance = identities.instance(id) ?: return false
        client().soundManager.stop(instance)
        identities.stopped(instance)
        if (musicInstance === instance) musicInstance = null
        return true
    }

    
    fun stopAll() {
        client().soundManager.stop()
        identities.sync(emptyList())
        musicInstance = null
    }

    
    fun catalog(): List<String> = client().soundManager.availableSounds.map { it.toString() }.sorted()

    fun play(id: String, volume: Float = 1f, pitch: Float = 1f): String? =
        start(id, volume, pitch, SoundSource.MASTER, false)

    
    fun music(): List<String> = catalog().filter(::isMusicSound)

    
    fun tracks(): List<MusicTrack> {
        val minecraft = client()
        return loadedMusicTracks(minecraft.resourceManager, minecraft.soundManager)
    }

    fun playTrack(track: MusicTrack): String? {
        val minecraft = client()
        val file = ResourceLocation.tryParse(track.id) ?: return null
        if (!minecraft.resourceManager.getResource(file.withPath("sounds/${file.path}.ogg")).isPresent) return null
        
        if (tracks().none { it.id == track.id }) return null
        return startInstance(TrackSoundInstance(file))
    }

    
    fun playMusic(id: String): String? = start(id, 1f, 1f, SoundSource.MUSIC, true)

    fun stopMusic(): Boolean {
        val current = currentMusic ?: return false
        return stop(current.id)
    }

    val currentMusic: PlayingSound?
        get() {
            val active = snapshot()
            val instance = musicInstance ?: return null
            if (active.none { it === instance }) {
                musicInstance = null
                return null
            }
            return describe(instance)
        }

    private fun start(id: String, volume: Float, pitch: Float, source: SoundSource, loop: Boolean): String? {
        val minecraft = client()
        if (!validSoundParameters(volume, pitch)) return null
        val identifier = ResourceLocation.tryParse(id) ?: return null
        if (identifier !in minecraft.soundManager.availableSounds) return null
        if (source == SoundSource.MUSIC && !isMusicSound(identifier.toString())) return null
        val instance = SimpleSoundInstance(
            identifier, source, volume, pitch, SoundInstance.createUnseededRandom(),
            loop, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true,
        )
        return startInstance(instance)
    }

    private fun startInstance(instance: SoundInstance): String? {
        val minecraft = client()
        if (instance.source == SoundSource.MUSIC) {
            stopMusic()
            minecraft.musicManager.stopPlaying()
        }
        minecraft.soundManager.play(instance)
        if (snapshot().none { it === instance }) return null
        if (instance.source == SoundSource.MUSIC) musicInstance = instance
        return identities.id(instance)
    }

    private fun describe(instance: SoundInstance) = PlayingSound(
        identities.id(instance), instance.location.toString(), instance.source.getName(),
        instance.volume, instance.pitch, instance.isLooping,
        if (instance.isRelative) null else SoundPosition(instance.x, instance.y, instance.z),
    )
}

internal fun validSoundParameters(volume: Float, pitch: Float): Boolean =
    volume.isFinite() && volume in 0f..1f && pitch.isFinite() && pitch in 0.5f..2f

internal fun isMusicSound(id: String): Boolean {
    val path = id.substringAfter(':')
    return path == "music" || path.startsWith("music.") || path.startsWith("music/") ||
        path.contains(".music.") || path.endsWith(".music") || path.contains("/music/")
}


internal class SoundIdentityTokens<T : Any> {
    private val tokens = IdentityHashMap<T, String>()
    private val stopped = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
    private val paused = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
    private var next = 0L

    fun sync(instances: List<T>): List<T> {
        val live = Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(instances) }
        tokens.keys.retainAll(live)
        stopped.retainAll(live)
        paused.retainAll(live)
        return instances.filterNot { it in stopped }.also { active -> active.forEach(::id) }
    }

    fun id(instance: T): String = tokens.getOrPut(instance) { "sound-${++next}" }
    fun instance(id: String): T? = tokens.entries.firstOrNull { it.value == id && it.key !in stopped }?.key
    fun latest(instances: List<T>): T? = instances.maxByOrNull { id(it).removePrefix("sound-").toLong() }
    fun isPaused(instance: T): Boolean = instance in paused
    fun setPaused(instance: T, value: Boolean) {
        if (instance !in tokens || instance in stopped) return
        if (value) paused.add(instance) else paused.remove(instance)
    }
    fun stopped(instance: T) {
        stopped.add(instance)
        paused.remove(instance)
    }
}
