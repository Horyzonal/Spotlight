package dev.spotlight.platform

import com.mojang.blaze3d.platform.InputConstants
import dev.spotlight.ui.SpotlightTheme
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB

object RegionSelection {
    private class Session(val state: RegionSelectionState, val level: ClientLevel, val player: Any, val connection: Any)
    private var session: Session? = null
    private var enterDown = false
    private var ticks = 0

    fun startFill(blockId: String, mode: String, replaceBlockId: String? = null) {
        val id = requireNotNull(Identifier.tryParse(blockId)) { "Invalid block ID" }
        require(BuiltInRegistries.BLOCK.containsKey(id)) { "Unknown block: $blockId" }
        val state = RegionSelectionState(id.toString(), mode, replaceBlockId = replaceBlockId)
        replaceBlockId?.let {
            val filter = requireNotNull(Identifier.tryParse(it)) { "Invalid replacement block ID" }
            require(BuiltInRegistries.BLOCK.containsKey(filter)) { "Unknown replacement block: $it" }
        }
        start(state)
    }

    fun startClone(mask: String, mode: String) = start(RegionSelectionState(null, mode, mask))

    private fun start(state: RegionSelectionState) {
        val client = Minecraft.getInstance()
        val level = requireNotNull(client.level) { "Join a world first" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val connection = requireNotNull(client.connection) { "Join a world first" }
        require(player.level() === level) { "World changed; try again" }
        KeyMapping.releaseAll()
        player.stopUsingItem()
        client.gameMode?.stopDestroyBlock()
        client.gui.setScreen(null)
        session = Session(state, level, player, connection)
        enterDown = enterPressed()
        ticks = 0
        hint()
    }

    fun cancel() {
        if (session == null) return
        session = null
        message("Region selection cancelled")
    }

    fun active(): Boolean {
        val current = session ?: return false
        val client = Minecraft.getInstance()
        if (client.level !== current.level || client.player !== current.player || client.connection !== current.connection) {
            session = null
            return false
        }
        if (client.gui.screen() != null) {
            cancel()
            return false
        }
        return true
    }

    
    fun selectPoint(button: Int) {
        if (!active()) return
        val left = button == InputConstants.MOUSE_BUTTON_LEFT
        if (!left && button != InputConstants.MOUSE_BUTTON_RIGHT) return
        val client = Minecraft.getInstance()
        try {
            val point = MacroTargeting.capture(client, false).block
                ?: throw IllegalArgumentException("No loaded block within 256 blocks")
            session!!.state.select(left, RegionPoint(point.x, point.y, point.z))
            hint()
        } catch (failure: IllegalArgumentException) {
            message(failure.message ?: "Cannot select point")
        }
    }

    fun tick() {
        if (!active()) return
        val client = Minecraft.getInstance()
        if (!client.isWindowActive || client.gui.overlay() != null) {
            cancel()
            return
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_ESCAPE)) {
            cancel()
            return
        }
        val pressed = enterPressed()
        val submit = pressed && !enterDown
        enterDown = pressed
        if (submit) {
            val current = session ?: return
            try {
                val command = current.state.command(current.level.minY, current.level.maxY)
                
                session = null
                Spotlight.executeSlash(command)
                message("Submitted $command; server checks permissions, loaded chunks and volume limit")
            } catch (failure: RuntimeException) {
                message(failure.message ?: "Region command failed")
            }
        } else if (++ticks % 20 == 0) hint()
    }

    private fun enterPressed() = InputConstants.isKeyDown(InputConstants.KEY_RETURN) ||
        InputConstants.isKeyDown(InputConstants.KEY_NUMPADENTER)

    private fun message(text: String) {
        Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(text))
    }

    private fun hint() {
        val state = session?.state ?: return
        fun RegionPoint?.label() = this?.coordinates() ?: "unset"
        val volume = runCatching { state.volume() }.getOrNull()?.let { " | $it blocks" }.orEmpty()
        val clone = state.blockId == null
        message("${if (clone) "Clone" else "Fill"}: LMB ${if (clone && state.second != null) "destination" else "first"}, RMB second | " +
            "1: ${state.first.label()} | 2: ${state.second.label()}" +
            (if (clone) " | destination: ${state.destination.label()}" else
                            " | ${state.blockId} ${state.mode} | Replace only: ${state.replaceBlockId ?: "Any block"}") +
                        volume + " | Enter submit | Esc cancel" +
                        (if (state.replaceBlockId != null) " | Clear filter: Esc, then Any block" else ""))
    }

    fun initialize() {
        LevelRenderEvents.BEFORE_GIZMOS.register { context ->
            if (active()) {
                val state = session!!.state
                val color = SpotlightTheme.color(0xFF81B8FF)
                fun draw(bounds: Pair<RegionPoint, RegionPoint>, width: Float) {
                    val (a, b) = bounds
                    Gizmos.cuboid(AABB(a.x.toDouble(), a.y.toDouble(), a.z.toDouble(),
                        b.x.toDouble() + 1, b.y.toDouble() + 1, b.z.toDouble() + 1).inflate(0.01),
                        GizmoStyle.stroke(color, width)).setAlwaysOnTop()
                }
                context.levelRenderer().collectPerFrameRenderThreadGizmos().use {
                    state.first?.let { draw(it to it, 3f) }
                    state.second?.let { draw(it to it, 3f) }
                    state.bounds()?.let { draw(it, 2f) }
                    runCatching { state.destinationBounds() }.getOrNull()?.let { draw(it, 3f) }
                }
            }
        }
    }
}
