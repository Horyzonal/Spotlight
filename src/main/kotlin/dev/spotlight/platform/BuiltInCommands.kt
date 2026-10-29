package dev.spotlight.platform

import dev.spotlight.commands.registerUnbreakable

import dev.spotlight.commands.registerFov
import dev.spotlight.commands.registerGamma
import dev.spotlight.commands.registerEntities
import dev.spotlight.commands.registerBlocks
import dev.spotlight.commands.registerTeleport
import dev.spotlight.commands.registerLocationSync
import dev.spotlight.commands.registerLocation
import dev.spotlight.commands.registerCopyItem
import dev.spotlight.commands.registerGive
import dev.spotlight.commands.registerInventory
import dev.spotlight.commands.registerCoordinates
import dev.spotlight.commands.registerSeed
import dev.spotlight.commands.registerUuid
import dev.spotlight.commands.registerGamemode
import dev.spotlight.commands.registerPing
import dev.spotlight.commands.registerSettings
import dev.spotlight.commands.registerCatalog
import dev.spotlight.commands.registerLight
import dev.spotlight.commands.registerBiome
import dev.spotlight.commands.registerChunk
import dev.spotlight.commands.registerDimension
import dev.spotlight.commands.registerWorldTime
import dev.spotlight.commands.registerSnake
import dev.spotlight.commands.registerDino
import dev.spotlight.commands.register2048
import dev.spotlight.commands.registerTetris
import dev.spotlight.commands.registerBanner
import dev.spotlight.commands.registerHeightmap
import dev.spotlight.commands.registerFacing
import dev.spotlight.commands.registerPosition
import dev.spotlight.commands.registerFps
import dev.spotlight.commands.registerMemory
import dev.spotlight.commands.registerPlayerInfo
import dev.spotlight.commands.registerTargetBlock
import dev.spotlight.commands.registerTargetEntity
import dev.spotlight.commands.registerEffects
import dev.spotlight.commands.registerConnection
import dev.spotlight.commands.registerPlugins
import dev.spotlight.commands.registerText
import dev.spotlight.commands.registerFill
import dev.spotlight.commands.registerClone
import dev.spotlight.commands.registerRecipes
import dev.spotlight.commands.registerSounds
import dev.spotlight.commands.registerMusic
import dev.spotlight.commands.registerCurrentSong
import dev.spotlight.commands.registerThemes

internal fun registerBuiltIns() {
    registerFov()
    registerGamma()
    registerEntities()
    registerBlocks()
    registerTeleport()
    registerLocationSync()
    registerLocation()
    registerCopyItem()
    registerGive()
    registerInventory()
    registerCoordinates()
    registerSeed()
    registerUuid()
    registerPing()
    registerGamemode()
    registerSettings()
    registerCatalog()
    registerLight()
    registerBiome()
    registerChunk()
    registerDimension()
    registerWorldTime()
    registerHeightmap()
    registerFacing()
    registerPosition()
    registerFps()
    registerMemory()
    registerPlayerInfo()
    registerTargetBlock()
    registerTargetEntity()
    registerEffects()
    registerConnection()
    registerPlugins()
    registerText()
    registerFill()
    registerClone()
    registerSnake()
    registerDino()
    register2048()
    registerTetris()
    registerBanner()
    registerRecipes()
    registerSounds()
    registerMusic()
    registerCurrentSong()
    registerThemes()
    registerUnbreakable()
}
