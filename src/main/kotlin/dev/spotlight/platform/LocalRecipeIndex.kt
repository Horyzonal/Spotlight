package dev.spotlight.platform

import com.google.gson.JsonElement

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft

import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

import net.minecraft.resources.FileToIdConverter

import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources

import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken



internal object LocalRecipeIndex {
    data class Entry(val id: String, val json: JsonElement)
    data class Loaded(val entries: List<Entry>, val registries: RegistryAccess,
                      val itemTags: Map<ResourceLocation, Collection<Item>>, val unsupported: Map<String, String>)

    fun packs(client: Minecraft): List<PackResources> = buildList {
        add(client.vanillaPackResources)

        for (mod in FabricLoader.getInstance().allMods.sortedBy { it.metadata.id }) {
            if (mod.metadata.id == "minecraft") continue
            for ((index, root) in mod.rootPaths.withIndex()) {

                val id = "spotlight-local:${mod.metadata.id}:$index"
                add(PathPackResources(id, root, false))
            }
        }
    }

    fun load(packs: List<PackResources>): Loaded {

        val resources = MultiPackResourceManager(PackType.SERVER_DATA, packs)
        val unsupported = linkedMapOf<String, String>()
        // Keep tag membership detached: Ingredient tag values otherwise read live global bindings.
        val loader = TagLoader<Item>({ id -> BuiltInRegistries.ITEM.getOptional(id) }, "tags/items")
        val definitions = loader.load(resources)
        val tags = loader.build(definitions)
        for (id in definitions.keys - tags.keys) unsupported["[C] tag $id"] = "Local tag has missing entries or a cycle"
        val registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
        val converter = FileToIdConverter.json("recipes")
        val entries = mutableListOf<Entry>()
        val files = converter.listMatchingResources(resources).entries.sortedBy { it.key.toString() }
        if (files.size > 50_000) unsupported["[C] limit"] = "Local recipe limit 50000; remaining files skipped"
        for ((file, resource) in files.take(50_000)) {
            val id = converter.fileToId(file).toString()
            try {
                val bytes = resource.open().use { it.readNBytes(512 * 1024 + 1) }
                require(bytes.size <= 512 * 1024) { "Recipe exceeds 512 KiB" }
                val reader = JsonReader(bytes.toString(Charsets.UTF_8).reader()).also { it.isLenient = false }
                val json = com.google.gson.internal.Streams.parse(reader)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON content" }
                require(json != null && json.isJsonObject) { "Recipe must be a JSON object" }

                require(!json.isJsonObject || !json.asJsonObject.has("fabric:load_conditions")) {
                    "Conditional local recipe needs Fabric reload context"
                }
                entries += Entry(id, json)
            } catch (exception: Exception) {
                unsupported["[C] $id"] = "Local JSON rejected (${resource.sourcePackId()}): ${exception.message}"
            }
        }
        return Loaded(entries, registries, tags, unsupported)
    }

    fun decode(entry: Entry, loaded: Loaded): net.minecraft.world.item.crafting.Recipe<*> {
        val json = entry.json.deepCopy().asJsonObject
        val type = ResourceLocation(json.get("type").asString)
        val serializer = BuiltInRegistries.RECIPE_SERIALIZER.getOptional(type).orElseThrow {
            IllegalArgumentException("Unknown recipe serializer: $type")
        }
        // ponytail: custom serializers may read live tags; add explicit detached adapters before allowing them.
        require(type.namespace == "minecraft" && serializer.javaClass.name.startsWith("net.minecraft.world.item.crafting.")) {
            "Unsupported local custom serializer: $type; detached tag adapter required"
        }
        // Expand only ingredient fields; native vanilla decoders receive no global tag references.
        fun ingredient(value: JsonElement): JsonElement {
            if (value.isJsonArray) return JsonArray().also { array ->
                for (part in value.asJsonArray) {
                    val expanded = ingredient(part)
                    if (expanded.isJsonArray) expanded.asJsonArray.forEach { array.add(it) } else array.add(expanded)
                }
            }
            require(value.isJsonObject) { "Ingredient must be an object or array" }
            val objectValue = value.asJsonObject
            require(objectValue.keySet() == setOf("item") || objectValue.keySet() == setOf("tag")) {
                "Unsupported local ingredient fields: ${objectValue.keySet()}"
            }
            if (objectValue.has("item")) {
                val id = ResourceLocation(objectValue.get("item").asString)
                require(BuiltInRegistries.ITEM.containsKey(id)) { "Unknown ingredient item: $id" }
                return value
            }
            val id = ResourceLocation(objectValue.get("tag").asString)
            val items = loaded.itemTags[id] ?: throw IllegalArgumentException("Missing local item tag: $id")
            require(items.isNotEmpty()) { "Empty local item tag: $id" }
            return JsonArray().also { array -> items.forEach { item ->
                array.add(JsonObject().also { it.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString()) })
            } }
        }
        for (field in listOf("ingredient", "base", "addition", "template"))
            if (json.has(field)) json.add(field, ingredient(json.get(field)))
        if (json.has("ingredients")) json.add("ingredients", JsonArray().also { array ->
            json.getAsJsonArray("ingredients").forEach { array.add(ingredient(it)) }
        })
        if (json.has("key")) json.getAsJsonObject("key").entrySet().forEach { it.setValue(ingredient(it.value)) }
        return serializer.fromJson(ResourceLocation(entry.id), json)
    }
}
