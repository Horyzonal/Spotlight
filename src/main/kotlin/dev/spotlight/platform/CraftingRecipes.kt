package dev.spotlight.platform

import dev.spotlight.core.allocateIngredients
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.world.item.crafting.Ingredient
import java.util.concurrent.CompletableFuture
import net.minecraft.world.item.ItemStack


data class CraftingRecipeView(
    val id: String,
    val title: String,
    val width: Int,
    val height: Int,
    val ingredients: List<List<ItemStack>>,
    val result: ItemStack,
    val shapeless: Boolean,
    val sources: Set<RecipeSource> = setOf(RecipeSource.C),
    val recipeIds: Set<String> = if (RecipeSource.S in sources) setOf(id) else emptySet(),
) {
    val sourceTag: String get() = RecipeSource.entries.filter { it in sources }.joinToString(" ") { "[${it.name}]" }
}

enum class RecipeSource { C, S }


private data class StackKey(val item: net.minecraft.world.item.Item, val components: Set<net.minecraft.core.component.TypedDataComponent<*>>, val count: Int)
private fun stackKey(stack: ItemStack, output: Boolean = false) =
    StackKey(stack.item, stack.components.toSet(), if (output) stack.count else 1)
private data class RecipeKey(val shapeless: Boolean, val width: Int, val height: Int, val slots: Any, val result: StackKey)
private fun recipeKey(view: CraftingRecipeView): RecipeKey {
    val slots = view.ingredients.map { alternatives -> alternatives.filterNot { it.isEmpty }.map { stackKey(it) }.toSet() }

    return RecipeKey(view.shapeless, if (view.shapeless) 0 else view.width, if (view.shapeless) 0 else view.height,
        if (view.shapeless) slots.groupingBy { it }.eachCount() else slots, stackKey(view.result, true))
}

internal fun mergeRecipeViews(views: List<CraftingRecipeView>): List<CraftingRecipeView> {
    val merged = linkedMapOf<RecipeKey, CraftingRecipeView>()
    for (view in views) {
        val key = recipeKey(view)
        val previous = merged[key]
        if (previous == null) merged[key] = view else {
            val ids = previous.recipeIds + view.recipeIds
            merged[key] = previous.copy(
                id = ids.minOrNull() ?: minOf(previous.id, view.id),
                sources = previous.sources + view.sources, recipeIds = ids,
            )
        }
    }
    return merged.values.sortedWith(compareBy({ it.title }, { it.id }))
}

object CraftingRecipes {



    fun unsupported(): Map<String, String> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe search requires the client thread" }
        syncSession(client)
        return localSnapshot.unsupported + snapshot.unsupported
    }

    private data class ServerSnapshot(val views: List<CraftingRecipeView>, val unsupported: Map<String, String>,
                                      val recipeIds: Set<String> = emptySet())
    private var snapshot = ServerSnapshot(emptyList(), emptyMap())
    private var session: Any? = null
    private var serverSession: Any? = null
    private var generation = 0L
    private var serverStatus = "[S] unavailable: no integrated server"
    private var localSnapshot = ServerSnapshot(emptyList(), emptyMap())
    private var localStatus = "[C] not refreshed"

    private var localLoading: CompletableFuture<LocalRecipeIndex.Loaded>? = null

    private fun syncSession(client: Minecraft) {
        if (session !== client.connection || serverSession !== client.singleplayerServer) {
            session = client.connection
            serverSession = client.singleplayerServer
            generation++
            snapshot = ServerSnapshot(emptyList(), emptyMap())

            serverStatus = if (client.singleplayerServer == null)
                "[S] unavailable: remote servers do not expose full recipes"
            else "Server index not refreshed"
        }
    }

    fun status(): String {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe search requires the client thread" }
        syncSession(client)
        return "$localStatus; $serverStatus"
    }


    fun refresh(onUpdate: () -> Unit) {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe search requires the client thread" }
        syncSession(client)
        val server = client.singleplayerServer
        val ticket = ++generation
        refreshLocal(client, ticket, onUpdate)
        if (server == null) {
            snapshot = ServerSnapshot(emptyList(), emptyMap())
            serverStatus = "[S] unavailable: remote servers do not expose full recipes"
            onUpdate()
            return
        }
        serverStatus = "Loading integrated-server crafting index"
        val future = CompletableFuture<ServerSnapshot>()
        future.whenComplete { result, error ->
            client.execute {
                syncSession(client)
                if (ticket == generation && client.singleplayerServer === server) {
                    if (error == null) {
                        snapshot = result
                        serverStatus = "Integrated-server crafting index ready; unsupported recipes listed by unsupported()"
                    } else {
                        snapshot = ServerSnapshot(emptyList(), emptyMap())
                        serverStatus = "Server recipe snapshot failed: ${error.cause?.message ?: error.message}"
                    }
                    onUpdate()
                }
            }
        }
        try {
            server.execute {
                try {
                    val views = mutableListOf<CraftingRecipeView>()
                    val unsupported = linkedMapOf<String, String>()
                    for (holder in server.recipeManager.recipes) {
                        if (holder.value() !is CraftingRecipe) continue
                        val id = holder.id().toString()
                        try {
                            views += resolve(id, holder.value() as CraftingRecipe, RecipeSource.S, server.registryAccess(), unsupported)
                        } catch (exception: RuntimeException) {
                            unsupported["[S] $id"] = "Recipe resolution failed: ${exception.message ?: exception.javaClass.name}"
                        }
                    }
                    future.complete(ServerSnapshot(views.toList(), unsupported.toMap(),
                        server.recipeManager.recipes.mapTo(linkedSetOf()) { it.id().toString() }))
                } catch (exception: Exception) {
                    future.completeExceptionally(exception)
                }
            }
        } catch (exception: RuntimeException) {
            future.completeExceptionally(exception)
        }
    }

    private fun refreshLocal(client: Minecraft, ticket: Long, onUpdate: () -> Unit) {

        localStatus = "[C] loading shipped vanilla/mod data"
        val future = localLoading ?: run {
            val packs = LocalRecipeIndex.packs(client)
            CompletableFuture.supplyAsync { LocalRecipeIndex.load(packs) }.also { localLoading = it }
        }
        future.whenComplete { loaded, error -> client.execute {
            syncSession(client)
            if (ticket != generation) return@execute
            localLoading = null
            if (error != null) {
                localStatus = "[C] local data load failed: ${error.cause?.message ?: error.message}"
                onUpdate()
                return@execute
            }

            val views = mutableListOf<CraftingRecipeView>()
            val unsupported = loaded.unsupported.toMutableMap()
            var offset = 0
            fun chunk() {
                syncSession(client)
                if (ticket != generation) return
                val deadline = System.nanoTime() + 2_000_000
                do {
                    if (offset >= loaded.entries.size) break
                    val entry = loaded.entries[offset++]
                    try {
                        val recipe = LocalRecipeIndex.decode(entry, loaded)
                        if (recipe is CraftingRecipe) {
                            views += resolve(entry.id, recipe, RecipeSource.C, loaded.lookup, unsupported)
                        }
                    } catch (exception: Exception) {
                        unsupported["[C] ${entry.id}"] = "Local decode/resolution failed: ${exception.message}"
                    }
                } while (System.nanoTime() < deadline)
                if (offset < loaded.entries.size) {

                    CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute { client.execute { chunk() } }
                } else {
                    localSnapshot = ServerSnapshot(views.toList(), unsupported.toMap(), loaded.entries.mapTo(linkedSetOf()) { it.id })
                    localStatus = "[C] ready: ${views.size} local views, ${unsupported.size} skipped; local tags/default registries; " +
                        "vanilla then mod ID order, not server pack order; no world packs or generated recipes"
                    onUpdate()
                }
            }
            client.execute { chunk() }
        } }
    }


    fun catalog(): List<CommandSuggestion> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe catalog requires the client thread" }
        syncSession(client)
        if (localStatus == "[C] not refreshed" || serverStatus == "Server index not refreshed") refresh {}
        return (localSnapshot.recipeIds + snapshot.recipeIds).map { id ->
            CommandSuggestion(id, id, if (id in snapshot.recipeIds) "Integrated-server recipe"
                else "Local data recipe; server availability unknown", 0, 0, SelectionKind.RECIPES)
        }
    }

    fun list(): List<CraftingRecipeView> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe search requires the client thread" }
        syncSession(client)


        val views = (localSnapshot.views + snapshot.views).map { view -> view.copy(
            ingredients = view.ingredients.map { alternatives -> alternatives.map { it.copy() } }, result = view.result.copy(),
        ) }
        return mergeRecipeViews(views)
    }

    internal fun resolve(id: String, recipe: CraftingRecipe, source: RecipeSource, registries: HolderLookup.Provider,
                         unsupported: MutableMap<String, String>): List<CraftingRecipeView> {
        fun reject(reason: String): List<CraftingRecipeView> {
            unsupported["[$source] $id"] = reason
            return emptyList()
        }
        try {
            val width: Int
            val height: Int
            val shapeless: Boolean
            when (recipe) {
                is ShapedRecipe -> {
                    width = recipe.width
                    height = recipe.height
                    shapeless = false
                    if (width !in 1..3 || height !in 1..3 || recipe.ingredients.size != width * height)
                        return reject("Shaped grid outside 3x3 or inconsistent dimensions")
                }
                is ShapelessRecipe -> {
                    if (recipe.ingredients.size !in 1..9) return reject("Shapeless ingredient count outside 1..9")
                    width = minOf(3, recipe.ingredients.size)
                    height = (recipe.ingredients.size + width - 1) / width
                    shapeless = true
                }
                else -> return reject("Unsupported dynamic crafting recipe: ${recipe.javaClass.name}; no static shape/result")
            }
            // ponytail: static views cannot model input-dependent outputs; add dedicated resolvers when needed.
            if (recipe.isSpecial) return reject("Special crafting recipe requires input-dependent resolution: ${recipe.javaClass.name}")
            val ingredients = recipe.ingredients.map { slot -> slot.items.filterNot { it.isEmpty }.map { it.copy() } }
            if (recipe.ingredients.indices.any { ingredients[it].isEmpty() && (shapeless || recipe.ingredients[it] !== Ingredient.EMPTY) })
                return reject("Ingredient has no resolved alternatives")
            val result = recipe.getResultItem(registries)
            if (result.isEmpty) return reject("Recipe has no static result")
            return listOf(CraftingRecipeView(
                id, "${result.hoverName.string} (${BuiltInRegistries.ITEM.getKey(result.item)})",
                width, height, ingredients, result.copy(), shapeless, setOf(source),
            ))
        } catch (exception: RuntimeException) {
            return reject("Recipe resolution failed (${recipe.javaClass.name}): ${exception.message ?: exception.javaClass.simpleName}")
        }
    }

    fun inventoryStatus(recipe: CraftingRecipeView): List<Boolean> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe inventory checks require the client thread" }
        require(recipe.width in 1..3 && recipe.height in 1..3 && recipe.ingredients.size <= 9) {
            "Recipe exceeds a 3x3 crafting grid"
        }
        val inventory = client.player?.inventory
        val stacks = if (inventory == null) emptyList() else
            (0 until Inventory.INVENTORY_SIZE).map { inventory.getItem(it) } + inventory.getItem(Inventory.SLOT_OFFHAND)
        val occupied = recipe.ingredients.indices.filter { recipe.ingredients[it].isNotEmpty() }
        val candidates = occupied.map { slot ->
            stacks.indices.filterTo(linkedSetOf()) { index ->
                !stacks[index].isEmpty && recipe.ingredients[slot].any { alternative ->
                    !alternative.isEmpty && ItemStack.isSameItemSameComponents(alternative, stacks[index])
                }
            }
        }
        val missing = allocateIngredients(candidates, stacks.map { if (it.isEmpty) 0 else it.count })
        val status = MutableList(recipe.ingredients.size) { false }
        occupied.forEachIndexed { index, slot -> status[slot] = missing[index] }
        return status.toList()
    }
}
