package dev.spotlight.commands

import dev.spotlight.platform.worldUnavailable
import dev.spotlight.ui.RecipeScreen
import net.minecraft.client.Minecraft

internal fun registerRecipes() {
    registerCommand("recipes", "Crafting recipes", "Search crafting outputs and recipe IDs; preview ingredients and inventory availability", unavailable = ::worldUnavailable) {
        val client = Minecraft.getInstance()
        client.setScreen(RecipeScreen(client.screen))
    }
}
