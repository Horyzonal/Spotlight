package dev.spotlight.platform

import dev.spotlight.core.allocateIngredients
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.util.context.ContextMap
import java.util.concurrent.CompletableFuture
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.item.crafting.display.SlotDisplayContext


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
    StackKey(stack.item, stack.immutableComponents().toSet(), if (output) stack.count else 1)
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
    private var skipped: Map<String, String> = emptyMap()


    fun unsupported(): Map<String, String> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe search requires the client thread" }
        syncSession(client)
        return skipped.toMap()
    }

    private data class ServerSnapshot(val views: List<CraftingRecipeView>, val unsupported: Map<String, String>)
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
            skipped = emptyMap()
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
                        serverStatus = "Integrated-server crafting index ready; unsupported displays listed by unsupported()"
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
                    val context = SlotDisplayContext.fromLevel(server.overworld())
                    val views = mutableListOf<CraftingRecipeView>()
                    val unsupported = linkedMapOf<String, String>()
                    for (holder in server.recipeManager.recipes) {
                        if (holder.value() !is CraftingRecipe) continue
                        val id = holder.id().identifier().toString()
                        try {
                            val displays = holder.value().display()
                            if (displays.isEmpty()) unsupported["[S] $id"] = "Crafting recipe provides no displays"
                            for (display in displays) views += resolve(id, display, RecipeSource.S, context, unsupported)
                        } catch (exception: RuntimeException) {
                            unsupported["[S] $id"] = "Recipe display failed: ${exception.message ?: exception.javaClass.name}"
                        }
                    }
                    future.complete(ServerSnapshot(views.toList(), unsupported.toMap()))
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

            val context = ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, loaded.registries).create(SlotDisplayContext.CONTEXT)
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
                            val displays = recipe.display()
                            if (displays.isEmpty()) unsupported["[C] ${entry.id}"] = "Local crafting recipe provides no displays"
                            for (display in displays) views += resolve(entry.id, display, RecipeSource.C, context, unsupported)
                        }
                    } catch (exception: Exception) {
                        unsupported["[C] ${entry.id}"] = "Local decode/display failed: ${exception.message}"
                    }
                } while (System.nanoTime() < deadline)
                if (offset < loaded.entries.size) {

                    CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute { client.schedule(Runnable { chunk() }) }
                } else {
                    localSnapshot = ServerSnapshot(views.toList(), unsupported.toMap())
                    localStatus = "[C] ready: ${views.size} local views, ${unsupported.size} skipped; local tags/default registries; " +
                        "vanilla then mod ID order, not server pack order; no world packs or generated recipes"
                    onUpdate()
                }
            }
            client.schedule(Runnable { chunk() })
        } }
    }


    fun list(): List<CraftingRecipeView> {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Recipe search requires the client thread" }
        syncSession(client)
        skipped = emptyMap()
        skipped = localSnapshot.unsupported + snapshot.unsupported

        val views = (localSnapshot.views + snapshot.views).map { view -> view.copy(
            ingredients = view.ingredients.map { alternatives -> alternatives.map { it.copy() } }, result = view.result.copy(),
        ) }
        return mergeRecipeViews(views)
    }

    private fun resolve(id: String, display: RecipeDisplay, source: RecipeSource, context: ContextMap,
                        unsupported: MutableMap<String, String>): List<CraftingRecipeView> {
        val reportId = "[$source] $id"
        fun reject(reason: String): List<CraftingRecipeView> {
            unsupported[reportId] = reason
            return emptyList()
        }
        try {
            val slots: List<SlotDisplay>
            val width: Int
            val height: Int
            val shapeless: Boolean
            when (display) {
                is ShapedCraftingRecipeDisplay -> {
                    width = display.width()
                    height = display.height()
                    slots = display.ingredients()
                    shapeless = false
                    if (width !in 1..3 || height !in 1..3 || slots.size != width * height)
                        return reject("Shaped grid outside 3x3 or inconsistent dimensions")
                }
                is ShapelessCraftingRecipeDisplay -> {
                    slots = display.ingredients()
                    if (slots.size !in 1..9) return reject("Shapeless ingredient count outside 1..9")
                    width = minOf(3, slots.size)
                    height = (slots.size + width - 1) / width
                    shapeless = true
                }
                else -> return reject("Unsupported display type: ${display.javaClass.name}")
            }
            val ingredients = slots.map { slot ->
                slot.resolveForStacks(context).filterNot { it.isEmpty }.map { it.copy() }
            }
            if (slots.indices.any { ingredients[it].isEmpty() && (shapeless || slots[it] !is SlotDisplay.Empty) })
                return reject("Ingredient display has no resolved alternatives")
            val results = display.result().resolveForStacks(context).filterNot { it.isEmpty }
            if (results.isEmpty()) return reject("Result display has no resolved stacks")
            return results.map { result ->
                CraftingRecipeView(
                    id, "${result.hoverName.string} (${BuiltInRegistries.ITEM.getKey(result.item)})",
                    width, height, ingredients.map { alternatives -> alternatives.map { it.copy() } },
                    result.copy(), shapeless, setOf(source),
                )
            }
        } catch (exception: RuntimeException) {
            return reject("Display resolution failed (${display.javaClass.name}): ${exception.message ?: exception.javaClass.simpleName}")
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
