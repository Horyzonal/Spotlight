package dev.spotlight.core

import java.nio.file.Files
import java.util.Properties

object MusicPreferencesCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val directory = Files.createTempDirectory("spotlight-music-check")
        try {
            val path = directory.resolve("spotlight/music.properties")
            val preferences = MusicPreferences(path)
            check(!preferences.endless && preferences.error == null)
            check(!Files.exists(path))
            check(preferences.setEndless(true))
            check(preferences.endless && preferences.error == null)
            check(MusicPreferences(path).endless)
            Files.writeString(path, "endless=true\nother=preserved\n")
            check(preferences.setEndless(false))
            check(!MusicPreferences(path).endless)
            val saved = Properties().apply { Files.newInputStream(path).use { load(it) } }
            check(saved.getProperty("other") == "preserved")
            check(preferences.setEndless(true))
            Files.delete(path)
            Files.createDirectory(path)
            Files.writeString(path.resolve("occupied"), "keep")
            check(!preferences.setEndless(false))
            check(preferences.endless && preferences.error?.contains("could not save") == true)
            check(Files.readString(path.resolve("occupied")) == "keep")
            check(MusicPreferences(path).let { !it.endless && it.error != null })
            Files.delete(path.resolve("occupied"))
            Files.delete(path)
            Files.writeString(path, "endless=invalid\n")
            check(MusicPreferences(path).let { !it.endless && it.error?.contains("Invalid endless") == true })
            Files.writeString(path, "endless=\\uXXXX\n")
            check(!preferences.setEndless(false))
            check(preferences.endless)
            check(Files.readString(path) == "endless=\\uXXXX\n")
            Files.writeString(path, "endless=true\n")
            check(preferences.setEndless(false))
            check(!preferences.endless && preferences.error == null)
            Files.list(path.parent).use { files -> check(files.noneMatch { it.fileName.toString().endsWith(".tmp") }) }
            println("MusicPreferencesCheck passed")
        } finally {
            Files.walk(directory).use { files -> files.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }
}
