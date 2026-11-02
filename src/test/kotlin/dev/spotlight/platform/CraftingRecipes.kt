package dev.spotlight.platform

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.util.context.ContextMap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplayContext

object RecipeIndexCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val registries = net.minecraft.data.registries.VanillaRegistries.createLookup()

        val globalBindings = BuiltInRegistries.REGISTRY.flatMap { registry ->
            registry.listElements().toList().map { holder ->
                holder to holder.tags().toList().toSet()
            }
        }
        fun checkGlobalBindings() {
            for ((holder, tags) in globalBindings) {
                check(holder.tags().toList().toSet() == tags)
            }
        }
        val a = ItemStack(net.minecraft.world.item.Items.STONE)
        val b = ItemStack(net.minecraft.world.item.Items.DIRT)
        val output = ItemStack(net.minecraft.world.item.Items.COBBLESTONE)
        fun view(slots: List<List<ItemStack>>, shapeless: Boolean = false) =
            CraftingRecipeView("display:1", "Test", slots.size, 1, slots, output.copy(), shapeless)
        val client = view(listOf(listOf(a, b), listOf(a))).copy(id = "test:local")
        val server = client.copy(id = "test:recipe", sources = setOf(RecipeSource.S), recipeIds = setOf("test:recipe"))
        val merged = mergeRecipeViews(listOf(client, server, client)).single()
        check(merged.id == "test:recipe" && merged.sourceTag == "[C] [S]")
        check(mergeRecipeViews(listOf(server, client)).single().id == merged.id)
        check(mergeRecipeViews(listOf(client, client.copy(ingredients = listOf(listOf(b, a, a), listOf(a))))).size == 1)
        val reversed = client.copy(ingredients = client.ingredients.reversed())
        check(mergeRecipeViews(listOf(client, reversed)).size == 2)
        check(mergeRecipeViews(listOf(client.copy(shapeless = true), reversed.copy(shapeless = true))).size == 1)
        check(mergeRecipeViews(listOf(view(listOf(listOf(a))), view(listOf(listOf(b))))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(result = output.copyWithCount(2)))).size == 2)
        val named = output.copy().also { it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Different")) }
        check(mergeRecipeViews(listOf(client, client.copy(result = named))).size == 2)
        val namedIngredient = a.copy().also { it.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Different")) }
        check(mergeRecipeViews(listOf(view(listOf(listOf(a))), view(listOf(listOf(namedIngredient))))).size == 2)
        check(mergeRecipeViews(listOf(view(listOf(listOf(a)), true), view(listOf(listOf(a), listOf(a)), true))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(shapeless = true))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(width = 1, height = 2))).size == 2)
        val alias = server.copy(id = "test:alias", recipeIds = setOf("test:alias"))
        check(mergeRecipeViews(listOf(server, alias, client)).single().recipeIds == setOf("test:recipe", "test:alias"))
        check(mergeRecipeViews(listOf(client, client.copy(station = RecipeStation.STONECUTTING))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(fuel = listOf(a)))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(stationItems = listOf(b)))).size == 2)
        val nativeContext = ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)).withParameter(SlotDisplayContext.FUEL_VALUES, net.minecraft.world.level.block.entity.FuelValues.Builder(registries, net.minecraft.world.flag.FeatureFlags.VANILLA_SET).add(net.minecraft.world.item.Items.COAL, 1600).build()).create(SlotDisplayContext.CONTEXT)
        val unsupported = linkedMapOf<String, String>()
        fun slot(item: net.minecraft.world.item.Item) = net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(item)
        val furnace = net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay(slot(net.minecraft.world.item.Items.STONE),
            net.minecraft.world.item.crafting.display.SlotDisplay.AnyFuel.INSTANCE, slot(net.minecraft.world.item.Items.COBBLESTONE), slot(net.minecraft.world.item.Items.FURNACE), 200, 0f)
        val furnaceView = CraftingRecipes.resolve("test:furnace", furnace, RecipeSource.C, nativeContext, unsupported).single()
        check(furnaceView.station == RecipeStation.FURNACE && furnaceView.ingredients.size == 1 && furnaceView.fuel.isNotEmpty())
        val stonecutting = net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay(slot(net.minecraft.world.item.Items.STONE), slot(net.minecraft.world.item.Items.COBBLESTONE), slot(net.minecraft.world.item.Items.STONECUTTER))
        check(CraftingRecipes.resolve("test:stonecutting", stonecutting, RecipeSource.C, nativeContext, unsupported).single().station == RecipeStation.STONECUTTING)
        val smithing = net.minecraft.world.item.crafting.display.SmithingRecipeDisplay(slot(net.minecraft.world.item.Items.STONE), slot(net.minecraft.world.item.Items.DIRT), slot(net.minecraft.world.item.Items.COBBLESTONE), slot(net.minecraft.world.item.Items.STONE), slot(net.minecraft.world.item.Items.SMITHING_TABLE))
        check(CraftingRecipes.resolve("test:smithing", smithing, RecipeSource.C, nativeContext, unsupported).single().ingredients.size == 3)
        check(unsupported.isEmpty())

        val root = java.nio.file.Files.createTempDirectory("spotlight-local-recipes")
        try {
            fun pack(name: String, recipes: Map<String, String>): net.minecraft.server.packs.PackResources {
                val path = root.resolve(name)
                for ((id, json) in recipes) {
                    val file = path.resolve("data/test/recipe/$id.json")
                    java.nio.file.Files.createDirectories(file.parent)
                    java.nio.file.Files.writeString(file, json)
                }
                return net.minecraft.server.packs.PathPackResources(net.minecraft.server.packs.PackLocationInfo(
                    name, net.minecraft.network.chat.Component.literal(name),
                    net.minecraft.server.packs.repository.PackSource.BUILT_IN, java.util.Optional.empty()), path)
            }
            val vanilla = """{"type":"minecraft:crafting_shapeless","ingredients":["minecraft:stone"],"result":{"id":"minecraft:cobblestone"}}"""
            val mod = vanilla.replace("minecraft:cobblestone", "minecraft:dirt")
            val first = pack("vanilla", mapOf("same" to vanilla, "nested/local" to vanilla))
            val second = pack("mod", mapOf("same" to mod, "bad" to "{", "large" to " ".repeat(512 * 1024 + 1),
                "missing_tag" to vanilla.replace("minecraft:stone", "#test:missing"),
                                "trailing" to "$vanilla {}", "lenient" to "{type:'minecraft:crafting_shapeless'}", "null" to "null"))
            val tags = root.resolve("mod/data/test/tags/item/local.json")
            java.nio.file.Files.createDirectories(tags.parent)
            java.nio.file.Files.writeString(tags, """{"values":["minecraft:stone"]}""")
            java.nio.file.Files.writeString(root.resolve("mod/data/test/recipe/tagged.json"), vanilla.replace("minecraft:stone", "#test:local"))
            val loaded = LocalRecipeIndex.load(listOf(first, second))
            checkGlobalBindings()
            val localItems = loaded.registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
            check(loaded.lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM) === localItems)
            check(loaded.lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).listElements().findAny().isPresent)
            val stoneKey = BuiltInRegistries.ITEM.getResourceKey(net.minecraft.world.item.Items.STONE).orElseThrow()
            val localStone = localItems.get(stoneKey).orElseThrow()
            val globalStone = BuiltInRegistries.ITEM.get(stoneKey).orElseThrow()
            check(localStone !== globalStone && localStone.value() === globalStone.value())
            check(localStone.value().components() === globalStone.value().components())
            check(localStone.tags().toList().any { it.location().toString() == "test:local" })
            check(globalStone.tags().toList().none { it.location().toString() == "test:local" })
            check(loaded.unsupported.keys.containsAll(listOf("[C] test:bad", "[C] test:large", "[C] test:trailing", "[C] test:lenient", "[C] test:null")))
            check(loaded.entries.any { it.id == "test:nested/local" })
            val recipe = LocalRecipeIndex.decode(loaded.entries.single { it.id == "test:same" }, loaded)
            val context = ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, loaded.registries).withParameter(SlotDisplayContext.FUEL_VALUES, net.minecraft.world.level.block.entity.FuelValues.Builder(registries, net.minecraft.world.flag.FeatureFlags.VANILLA_SET).add(net.minecraft.world.item.Items.COAL, 1600).build()).create(SlotDisplayContext.CONTEXT)
            check(recipe.display().single().result().resolveForStacks(context).single().item === net.minecraft.world.item.Items.DIRT)
            val tagged = LocalRecipeIndex.decode(loaded.entries.single { it.id == "test:tagged" }, loaded)
            check((tagged.display().single() as ShapelessCraftingRecipeDisplay).ingredients().single()
                .resolveForStacks(context).single().item === net.minecraft.world.item.Items.STONE)
            check(runCatching { LocalRecipeIndex.decode(loaded.entries.single { it.id == "test:missing_tag" }, loaded) }.isFailure)
            check(loaded.registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM) !== BuiltInRegistries.ITEM)
            val vanillaPack = net.minecraft.server.packs.VanillaPackResourcesBuilder().pushJarResources().exposeNamespace("minecraft")
                .build(net.minecraft.server.packs.PackLocationInfo("vanilla", net.minecraft.network.chat.Component.literal("vanilla"),
                    net.minecraft.server.packs.repository.PackSource.BUILT_IN, java.util.Optional.empty()))
            val shipped = LocalRecipeIndex.load(listOf(vanillaPack))
            check(shipped.entries.any { it.id == "minecraft:crafting_table" })
            for (entry in shipped.entries) LocalRecipeIndex.decode(entry, shipped)
            val book = net.minecraft.client.ClientRecipeBook()
            val display = recipe.display().single()
            book.add(net.minecraft.world.item.crafting.display.RecipeDisplayEntry(
                net.minecraft.world.item.crafting.display.RecipeDisplayId(7), display, java.util.OptionalInt.empty(),
                net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC, java.util.Optional.empty()))
            book.rebuildCollections()
            check(book.collections.flatMap { it.recipes }.single().display() == display)
            check(book.collections.single().recipes.single().resultItems(context).single().item === net.minecraft.world.item.Items.DIRT)
            check(LocalRecipeIndex.decode(shipped.entries.single { it.id == "minecraft:crafting_table" }, shipped) is CraftingRecipe)
            checkGlobalBindings()
            val reloaded = LocalRecipeIndex.load(listOf(first, second))
            check(LocalRecipeIndex.decode(reloaded.entries.single { it.id == "test:tagged" }, reloaded) is CraftingRecipe)
            checkGlobalBindings()
        } finally {
            java.nio.file.Files.walk(root).use { paths -> paths.sorted(java.util.Comparator.reverseOrder()).forEach { java.nio.file.Files.delete(it) } }
        }
        println("RecipeIndexCheck passed")
    }
}
