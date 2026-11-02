package dev.spotlight.ui

object MusicNavigationCheck {
    @JvmStatic fun main(args: Array<String>) {
        val tracks = listOf("first", "second", "third")
        check(adjacentMusicTrack(emptyList(), null, 1) == null)
        check(adjacentMusicTrack(tracks, "first", 1) == "second")
        check(adjacentMusicTrack(tracks, "first", -1) == "third")
        check(adjacentMusicTrack(tracks, "third", 1) == "first")
        check(adjacentMusicTrack(tracks, "filtered-out", 1) == "first")
        check(adjacentMusicTrack(tracks, null, -1) == "third")
        check(adjacentMusicTrack(listOf("only"), "only", 1) == "only")
        println("Music navigation checks passed")
    }
}
