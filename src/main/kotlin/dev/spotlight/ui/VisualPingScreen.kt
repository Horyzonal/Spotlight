package dev.spotlight.ui

import dev.spotlight.platform.worldUnavailable
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.component.ResolvableProfile
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun fluctuatingVisualPing(base: Int, tick: Int): Int {
    require(base >= 0)
    return (base * (1.0 + sin(tick * 0.22) * 0.25)).roundToInt()
}

internal fun visualPacketCycle(latency: Int): Int = if (latency <= 0) 0
    else ((latency * 20 + 999) / 1000).coerceAtLeast(2)

internal fun visualPacketProgress(tick: Int, latency: Int): Float? {
    if (latency <= 0) return null
    val cycle = visualPacketCycle(latency)
    val frame = tick % cycle
    val outboundTicks = kotlin.math.ceil(cycle / 2f).toInt().coerceAtLeast(1)
    return if (frame < outboundTicks) frame / outboundTicks.toFloat()
        else (cycle - 1 - frame) / (cycle - outboundTicks).coerceAtLeast(1).toFloat()
}

class VisualPingScreen(private val parent: Screen?) : Screen(catalogText("Visual ping")) {
    private var ticks = 0
    private var latency = -1
    private var lastLatency = -1
    private var roundTrips = 0
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0

    override fun isPauseScreen() = false

    override fun init() {
        panelWidth = minOf(560, (width - 16).coerceAtLeast(1))
        panelHeight = minOf(250, (height - 16).coerceAtLeast(1))
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        val half = (panelWidth - 36) / 2
        addRenderableWidget(catalogButton("Back", left + 16, top + panelHeight - 38, half) { onClose() })
        addRenderableWidget(catalogButton("Player list", left + 20 + half, top + panelHeight - 38, half) {
            minecraft.gui.setScreen(PingScreen(this))
        })
        refreshLatency()
    }

    private fun refreshLatency() {
        val testBase = System.getProperty("spotlight.visualPing")?.toIntOrNull()?.takeIf { it >= 0 }
        if (testBase != null) {
            latency = fluctuatingVisualPing(testBase, ticks)
            return
        }
        val player = minecraft.player ?: return
        latency = minecraft.connection?.getPlayerInfo(player.uuid)?.latency ?: -1
    }

    override fun tick() {
        super.tick()
        ticks++
        refreshLatency()
        val cycle = visualPacketCycle(latency)
        if (cycle > 0 && ticks % cycle == 0) {
            lastLatency = latency
            roundTrips++
        }
    }

    override fun onClose() { minecraft.gui.setScreen(parent) }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val foreground = SpotlightTheme.color(0xFFF2F5FC)
        val muted = SpotlightTheme.color(0xFF9DADC3)
        val accent = SpotlightTheme.color(0xFF81B8FF)
        graphics.fill(0, 0, width, height, SpotlightTheme.color(0xF0101722))
        graphics.fill(left, top, left + panelWidth, top + panelHeight, SpotlightTheme.color(0xFF161D29))
        graphics.outline(left, top, panelWidth, panelHeight, SpotlightTheme.color(0xFF415877))
        graphics.themeCenteredText(font, catalogText("VISUAL PING"), width / 2, top + 14, muted)
        val latencyText = latency.takeIf { it >= 0 }?.let { "$it ms" } ?: "Detecting…"
        graphics.themeCenteredText(font, catalogText(latencyText), width / 2, top + 38, foreground)

        val routeY = top + 112
        val clientX = left + 58
        val serverX = left + panelWidth - 82
        val routeStart = clientX + 32
        val routeEnd = serverX - 8
        graphics.horizontalLine(routeStart, routeY, routeEnd, SpotlightTheme.color(0xFF415877))
        for (x in routeStart until routeEnd step 16) graphics.fill(x, routeY + 8, x + 4, routeY + 10, SpotlightTheme.color(0xFF293F5B))

        val info = minecraft.player?.let { minecraft.connection?.getPlayerInfo(it.uuid) }
        if (info != null) PlayerFaceExtractor.extractRenderState(graphics, info.skin, clientX - 24, routeY - 48, 48)
        else PlayerFaceExtractor.extractRenderState(graphics, ResolvableProfile.createResolved(minecraft.gameProfile), clientX - 24, routeY - 48, 48)
        graphics.themeCenteredText(font, catalogText("client"), clientX, routeY + 12, foreground)
        graphics.fill(serverX - 24, routeY - 48, serverX + 24, routeY, SpotlightTheme.color(0xFF202630))
        graphics.outline(serverX - 24, routeY - 48, 48, 48, accent)
        graphics.fill(serverX - 13, routeY - 37, serverX + 13, routeY - 33, accent)
        graphics.fill(serverX - 13, routeY - 25, serverX + 13, routeY - 21, muted)
        graphics.fill(serverX - 13, routeY - 13, serverX + 5, routeY - 9, muted)
        graphics.themeCenteredText(font, catalogText("server"), serverX, routeY + 12, foreground)

        if (latency > 0) {
            val cycle = visualPacketCycle(latency)
            val returning = ticks % cycle >= kotlin.math.ceil(cycle / 2f).toInt()
            val progress = visualPacketProgress(ticks, latency) ?: 0f
            val packetX = routeStart + ((routeEnd - routeStart) * progress).roundToInt()
            for (trail in 1..3) {
                val trailX = packetX + if (returning) trail * 7 else -trail * 7
                if (trailX in routeStart..routeEnd) graphics.fill(trailX - 2, routeY - 2, trailX + 2, routeY + 2, SpotlightTheme.color(0xFF415877))
            }
            graphics.fill(packetX - 6, routeY - 6, packetX + 6, routeY + 6, accent)
            graphics.outline(packetX - 6, routeY - 6, 12, 12, foreground)
            val status = if (returning) "Packet returned · $latencyText" else "Sending packet…"
            graphics.themeCenteredText(font, catalogText(status), width / 2, top + 162, if (returning) accent else muted)
        } else if (latency == 0) {
            graphics.themeCenteredText(font, catalogText("$latencyText · Instant round trip"), width / 2, top + 162, accent)
        } else {
            graphics.themeCenteredText(font, catalogText(worldUnavailable() ?: "Waiting for player-list latency"), width / 2, top + 162, muted)
        }
        val trips = if (lastLatency >= 0) "Round trips: $roundTrips · Last: $lastLatency ms" else "Round trips: $roundTrips"
        graphics.themeCenteredText(font, catalogText(trips), width / 2, top + 182, muted)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

}
