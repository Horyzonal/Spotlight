package dev.spotlight.platform

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.ItemStack

object RecipeIndexCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()


        val globalBindings = BuiltInRegistries.REGISTRY.flatMap { registry ->
            registry.holders().toList().map { holder ->
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
        val named = output.copy().also { it.setHoverName(net.minecraft.network.chat.Component.literal("Different")) }
        check(mergeRecipeViews(listOf(client, client.copy(result = named))).size == 2)
        val namedIngredient = a.copy().also { it.setHoverName(net.minecraft.network.chat.Component.literal("Different")) }
        check(mergeRecipeViews(listOf(view(listOf(listOf(a))), view(listOf(listOf(namedIngredient))))).size == 2)
        check(mergeRecipeViews(listOf(view(listOf(listOf(a)), true), view(listOf(listOf(a), listOf(a)), true))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(shapeless = true))).size == 2)
        check(mergeRecipeViews(listOf(client, client.copy(width = 1, height = 2))).size == 2)
        val alias = server.copy(id = "test:alias", recipeIds = setOf("test:alias"))
        check(mergeRecipeViews(listOf(server, alias, client)).single().recipeIds == setOf("test:recipe", "test:alias"))
        val root = java.nio.file.Files.createTempDirectory("spotlight-local-recipes")
        try {
            fun pack(name: String, recipes: Map<String, String>): net.minecraft.server.packs.PackResources {
                val path = root.resolve(name)
                for ((id, json) in recipes) {
                    val file = path.resolve("data/test/recipes/$id.json")
                    java.nio.file.Files.createDirectories(file.parent)
                    java.nio.file.Files.writeString(file, json)
                }
                return net.minecraft.server.packs.PathPackResources(name, path, false)
            }
            val vanilla = """{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:stone"}],"result":{"item":"minecraft:cobblestone"}}"""
            val mod = vanilla.replace("minecraft:cobblestone", "minecraft:dirt")
            val first = pack("vanilla", mapOf("same" to vanilla, "nested/local" to vanilla))
            val second = pack("mod", mapOf("same" to mod, "bad" to "{", "large" to " ".repeat(512 * 1024 + 1),
                "missing_tag" to vanilla.replace("\"item\":\"minecraft:stone\"", "\"tag\":\"test:missing\""),
                                "trailing" to "$vanilla {}", "lenient" to "{type:'minecraft:crafting_shapeless'}", "null" to "null"))
            val tags = root.resolve("mod/data/test/tags/items/local.json")
            java.nio.file.Files.createDirectories(tags.parent)
            java.nio.file.Files.writeString(tags, """{"values":["#test:nested",{"id":"test:absent","required":false}]}""")
            java.nio.file.Files.writeString(tags.resolveSibling("nested.json"), """{"values":["minecraft:stone"]}""")
            java.nio.file.Files.writeString(tags.resolveSibling("cycle.json"), """{"values":["#test:cycle"]}""")
            java.nio.file.Files.writeString(root.resolve("mod/data/test/recipes/tagged.json"), vanilla.replace("\"item\":\"minecraft:stone\"", "\"tag\":\"test:local\""))
            val custom = LocalRecipeIndex.Entry("test:custom", com.google.gson.JsonParser.parseString(
                vanilla.replace("minecraft:crafting_shapeless", "test:custom")))
            val loaded = LocalRecipeIndex.load(listOf(first, second))
            check(runCatching { LocalRecipeIndex.decode(custom, loaded) }.exceptionOrNull()?.message ==
                "Unknown recipe serializer: test:custom")
            check("[C] tag test:cycle" in loaded.unsupported)
            checkGlobalBindings()
            check(loaded.itemTags[net.minecraft.resources.ResourceLocation("test:local")]?.toList() == listOf(a.item))
            check(loaded.unsupported.keys.containsAll(listOf("[C] test:bad", "[C] test:large", "[C] test:trailing", "[C] test:lenient", "[C] test:null")))
            check(loaded.entries.any { it.id == "test:nested/local" })
            val recipe = LocalRecipeIndex.decode(loaded.entries.single { it.id == "test:same" }, loaded)
            check(recipe.getResultItem(loaded.registries).item === net.minecraft.world.item.Items.DIRT)
            val tagged = LocalRecipeIndex.decode(loaded.entries.single { it.id == "test:tagged" }, loaded)
            check(tagged.ingredients.single().items.single().item === net.minecraft.world.item.Items.STONE)
            check(runCatching { LocalRecipeIndex.decode(loaded.entries.single { it.id == "test:missing_tag" }, loaded) }.isFailure)

            val vanillaPack = net.minecraft.server.packs.VanillaPackResourcesBuilder().pushJarResources().exposeNamespace("minecraft")
                .build()
            val shipped = LocalRecipeIndex.load(listOf(vanillaPack))
            check(shipped.entries.any { it.id == "minecraft:crafting_table" })
            for (entry in shipped.entries) LocalRecipeIndex.decode(entry, shipped)
            val book = net.minecraft.client.ClientRecipeBook()
            book.setupCollections(listOf(recipe), loaded.registries)
            check(book.collections.flatMap { it.recipes }.single() === recipe)
            check(book.collections.single().recipes.single().getResultItem(loaded.registries).item === net.minecraft.world.item.Items.DIRT)
            val unsupported = linkedMapOf<String, String>()
            var crafting = 0
            var resolved = 0
            val allViews = mutableListOf<CraftingRecipeView>()
            for (entry in shipped.entries) {
                val native = LocalRecipeIndex.decode(entry, shipped)
                if (native !is CraftingRecipe) continue
                crafting++
                val views = CraftingRecipes.resolve(entry.id, native, RecipeSource.C, shipped.registries, unsupported)
                check(views.size == 1 || "[C] ${entry.id}" in unsupported)
                for (view in views) {
                    val serverView = view.copy(sources = setOf(RecipeSource.S), recipeIds = setOf(entry.id))
                    val mergedView = mergeRecipeViews(listOf(view, serverView, view)).single()
                    check(mergedView.sourceTag == "[C] [S]" && mergedView.recipeIds == setOf(entry.id))
                }
                allViews += views
                resolved += views.size
            }
            check(shipped.entries.size == 1174 && crafting == 836 && resolved == 822 && unsupported.size == 14)
            check(crafting == resolved + unsupported.size)
            val serverViews = allViews.map { it.copy(sources = setOf(RecipeSource.S), recipeIds = setOf(it.id)) }
            val allMerged = mergeRecipeViews(allViews + serverViews)
            check(allMerged.size == mergeRecipeViews(allViews).size)
            check(allMerged.all { it.sourceTag == "[C] [S]" })
            check(allMerged.flatMap { it.recipeIds }.toSet() == allViews.map { it.id }.toSet())
            check(unsupported.containsKey("[C] minecraft:map_extending"))
            println("Vanilla recipes: ${shipped.entries.size}; crafting: $crafting; static views: $resolved; unsupported: ${unsupported.size}")
            println("Unsupported vanilla crafting: ${unsupported.keys.joinToString()}")
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
