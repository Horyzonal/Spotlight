package dev.spotlight.ui

import dev.spotlight.platform.ClientItemStorage
import dev.spotlight.platform.HeadDatabase
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag

class PlayerSkullScreen(parent: Screen? = null) : StorageScreen(parent, "Get player skull") {
    private var value = ""
    private var generation = 0
    private var loading = false
    private var raw: CompoundTag? = null
    private var description = "No player resolved"
    override fun build() {
        text("Online/current profiles resolve locally.")
        text("Submitting another name/UUID sends it to Mojang via Minecraft.")
        text("${value.ifEmpty { "Enter username or dashed UUID" }} · ${if (loading) "Looking up…" else description}")
        row("Enter player", "Resolve", "Back") { when (it) {
            0 -> prompt("Minecraft username or dashed UUID", value) {
                require(HeadDatabase.validPlayer(it)) { "Invalid Minecraft username or dashed UUID" }
                value = it; generation++; loading = false; raw = null; description = "Ready to resolve"
            }
            1 -> lookup()
            2 -> onClose()
        } }
        grid(raw?.let { mapOf(0 to it) } ?: emptyMap())
        row("Give skull", "Head database") { when (it) {
            0 -> { ClientItemStorage.give(listOf(requireNotNull(raw) { "Resolve a player first" })); notice = "Creative copy requested" }
            1 -> minecraft!!.setScreen(HeadDatabaseScreen(this))
        } }
        text("Give needs Creative mode and free inventory capacity; never replaces slots.")
    }
    private fun lookup() {
        check(!loading) { "Lookup already pending" }
        val future = HeadDatabase.resolve(value)
        val request = ++generation
        val connection = minecraft!!.connection
        loading = true
        raw = null
        description = ""
        rebuild()
        future.whenComplete { profile, error -> minecraft!!.execute {
            if (generation != request || minecraft!!.screen !== this) return@execute
            loading = false
            if (minecraft!!.connection !== connection || connection == null) {
                description = "Connection changed; resolve again"
            } else if (error != null || profile == null) {
                description = "Lookup failed or timed out; retry explicitly"
                notice = error?.cause?.message ?: error?.message ?: "Profile unavailable"
            } else attempt {
                raw = ClientItemStorage.encode(HeadDatabase.profileStack(profile))
                description = "${profile.name} · ${if (HeadDatabase.hasTexture(profile)) "texture present" else "default skin (no texture supplied)"}"
            }
            rebuild()
        } }
    }
    override fun removed() { generation++; loading = false; super.removed() }
    override fun onClose() { generation++; loading = false; super.onClose() }
}
