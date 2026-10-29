package dev.spotlight.ui

internal object CurrentSongCheck {
    fun run() {
        val rotation = SongRotation()
        check(rotation.update("a", true, 0) == 0f)
        val moving = rotation.update("a", true, 100_000_000)
        check(moving > 0f)
        check(rotation.update("a", false, 200_000_000) == moving)
        check(rotation.update("a", false, 9_000_000_000) == moving)
        check(rotation.update("a", true, 9_100_000_000) == moving)
        check(rotation.update("a", true, 9_200_000_000) > moving)
        check(rotation.update("b", true, 9_300_000_000) == 0f)
        check(rotation.update(null, false, 9_400_000_000) == 0f)
        val palette = intArrayOf(0xFF111111.toInt(), 0xFF445566.toInt(), 0xFF88AACC.toInt(), -1)
        val pixels = songArtwork(123, palette)
        check(pixels.contentEquals(songArtwork(123, palette)))
        check(!pixels.contentEquals(songArtwork(456, palette)))
        check(pixels[32 * 64 + 32] ushr 24 == 255)
        for (y in 0 until 64) for (x in 0 until 64) {
            val outside = (x - 31.5) * (x - 31.5) + (y - 31.5) * (y - 31.5) > 31 * 31
            check((pixels[y * 64 + x] ushr 24 == 0) == outside)
        }
    }
}
