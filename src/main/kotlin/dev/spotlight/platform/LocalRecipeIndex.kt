package dev.spotlight.platform

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import com.mojang.serialization.Lifecycle
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.MappedRegistry
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.FileToIdConverter
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.util.Optional


internal object LocalRecipeIndex {
    data class Entry(val id: String, val json: JsonElement)
    data class Loaded(val entries: List<Entry>, val registries: RegistryAccess, val lookup: HolderLookup.Provider,
                      val unsupported: Map<String, String>)

    fun packs(client: Minecraft): List<PackResources> = buildList {
        add(client.vanillaPackResources)

        for (mod in FabricLoader.getInstance().allMods.sortedBy { it.metadata.id }) {
            if (mod.metadata.id == "minecraft") continue
            for ((index, root) in mod.rootPaths.withIndex()) {

                val id = "spotlight-local:${mod.metadata.id}:$index"
                add(PathPackResources(PackLocationInfo(id, Component.literal(id), PackSource.BUILT_IN, Optional.empty()), root))
            }
        }
    }

    fun load(packs: List<PackResources>): Loaded {

        val resources = MultiPackResourceManager(PackType.SERVER_DATA, packs)
        val unsupported = linkedMapOf<String, String>()
        fun <T : Any> copyRegistry(source: Registry<T>): MappedRegistry<T> {
            val copy = MappedRegistry<T>(source.key(), Lifecycle.stable())
            for ((key, value) in source.entrySet()) {
                copy.register(key, value, RegistrationInfo.BUILT_IN)
            }
            val loader = TagLoader<net.minecraft.core.Holder<T>>({ id -> copy.getHolder(id) },
                net.minecraft.core.registries.Registries.tagsDirPath(copy.key()))
            val definitions = loader.load(resources)
            val tags = loader.build(definitions)
            for (id in definitions.keys - tags.keys) unsupported["[C] tag $id"] = "Local tag has missing entries or a cycle"
            copy.bindTags(tags.mapKeys { (id, _) -> net.minecraft.tags.TagKey.create(copy.key(), id) }
                            .mapValues { (_, holders) -> holders.toList() })
            copy.freeze()
            return copy
        }
        val copies = BuiltInRegistries.REGISTRY.map { copyRegistry(it) }
        val registries = RegistryAccess.ImmutableRegistryAccess(copies).freeze()

        val defaults = VanillaRegistries.createLookup()
        val lookup = HolderLookup.Provider.create(java.util.stream.Stream.concat(
            registries.registries().map { it.value().asLookup() },
                        defaults.listRegistries().filter { registries.lookup(it).isEmpty }.map { defaults.lookupOrThrow(it) }))

        val converter = FileToIdConverter.json("recipe")
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
        return Loaded(entries, registries, lookup, unsupported)
    }

    fun decode(entry: Entry, loaded: Loaded): net.minecraft.world.item.crafting.Recipe<*> {
        val json = entry.json.deepCopy().asJsonObject
        // 1.21.1 native Ingredient tags resolve against global items, not RegistryOps.
        // Expand only ingredient fields so detached local tags never change live bindings or result NBT.
        fun ingredient(value: JsonElement): JsonElement {
            if (value.isJsonArray) return JsonArray().also { array ->
                for (part in value.asJsonArray) {
                    val expanded = ingredient(part)
                    if (expanded.isJsonArray) expanded.asJsonArray.forEach { array.add(it) } else array.add(expanded)
                }
            }
            if (!value.isJsonObject || !value.asJsonObject.has("tag")) return value
            val id = net.minecraft.resources.ResourceLocation.parse(value.asJsonObject.get("tag").asString)
            val key = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id)
            val holders = loaded.lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).get(key)
                .orElseThrow { IllegalArgumentException("Missing local item tag: $id") }
            require(holders.size() > 0) { "Empty local item tag: $id" }
            return JsonArray().also { array -> holders.forEach { holder ->
                array.add(JsonObject().also { it.addProperty("item", BuiltInRegistries.ITEM.getKey(holder.value()).toString()) })
            } }
        }
        for (field in listOf("ingredient", "base", "addition", "template"))
            if (json.has(field)) json.add(field, ingredient(json.get(field)))
        if (json.has("ingredients")) json.add("ingredients", JsonArray().also { array ->
            json.getAsJsonArray("ingredients").forEach { array.add(ingredient(it)) }
        })
        if (json.has("key")) json.getAsJsonObject("key").entrySet().forEach { it.setValue(ingredient(it.value)) }
        return net.minecraft.world.item.crafting.Recipe.CODEC
            .parse(loaded.lookup.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow()
    }
}
