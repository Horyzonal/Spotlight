package dev.spotlight.core


data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val eventIds: Set<String>,
) {
    val label: String get() = "$title — ${artist ?: "Unknown artist"}"
}

object MusicMetadata {
    
    private val known = buildMap<String, Pair<String, String>> {
        fun tracks(artist: String, folder: String, names: String) {
            names.split('|').forEach { entry ->
                val parts = entry.split('=', limit = 2)
                put("minecraft:music/$folder/${parts[0]}", (parts.getOrNull(1) ?: humanize(parts[0])) to artist)
            }
        }
        tracks("C418", "game", "clark|danny|dry_hands|haggstrom=Häggström|key|living_mice|mice_on_venus|minecraft=Minecraft|oxygene|subwoofer_lullaby|sweden|wet_hands")
        tracks("C418", "game/creative", "aria_math|biome_fest|blind_spots|dreiton|haunt_muskie|taswell")
        tracks("C418", "game/nether", "ballad_of_the_cats|concrete_halls|dead_voxel|warmth")
        tracks("C418", "game/end", "alpha|boss|the_end")
        tracks("C418", "game/water", "axolotl|dragon_fish|shuniji")
        tracks("C418", "menu", "beginning_2|floating_trees|moog_city_2|mutation")
        tracks("Lena Raine", "game/nether/crimson_forest", "chrysopoeia")
        tracks("Lena Raine", "game/nether/nether_wastes", "rubedo")
        tracks("Lena Raine", "game/nether/soulsand_valley", "so_below")
        tracks("Lena Raine", "game", "infinite_amethyst|left_to_bloom|one_more_day|stand_tall|wending|ancestry")
        tracks("Lena Raine", "game/swamp", "aerie|firebugs|labyrinthine")
        tracks("Kumi Tanioka", "game", "an_ordinary_day|comforting_memories|floating_dream")
        tracks("Aaron Cherof", "game", "a_familiar_room|bromeliad|crescent_dunes|echo_in_the_wind")
    }

    fun track(id: String, eventIds: Set<String>): MusicTrack {
        val metadata = known[id]
        return MusicTrack(id, metadata?.first ?: humanize(id.substringAfterLast('/').substringAfter(':')),
            metadata?.second, eventIds.toSet())
    }

    private fun humanize(name: String): String = name.split('_', '-', '.')
        .filter(String::isNotEmpty).joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
}
