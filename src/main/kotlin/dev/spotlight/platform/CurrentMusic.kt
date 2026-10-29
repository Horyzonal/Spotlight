package dev.spotlight.platform

import dev.spotlight.core.MusicMetadata

data class CurrentSong(
    val id: String,
    val title: String,
    val artist: String?,
    val paused: Boolean,
    val elapsedSeconds: Double?,
    val durationSeconds: Double? = null,
    val owned: Boolean,
)


object CurrentMusic {
    fun snapshot(): CurrentSong? = SoundControls.currentSong()

    fun pause() { snapshot()?.let { SoundControls.setPaused(it.id, true) } }
    fun resume() { snapshot()?.let { SoundControls.setPaused(it.id, false) } }
    fun togglePause() { snapshot()?.let { SoundControls.setPaused(it.id, !it.paused) } }
    fun stop() { snapshot()?.let { SoundControls.stop(it.id) } }
}

internal fun currentSongMetadata(
    id: String, recording: String, event: String, paused: Boolean, owned: Boolean,
): CurrentSong {
    val track = MusicMetadata.track(recording, setOf(event))
    
    return CurrentSong(id, track.title, track.artist, paused, null, owned = owned)
}
