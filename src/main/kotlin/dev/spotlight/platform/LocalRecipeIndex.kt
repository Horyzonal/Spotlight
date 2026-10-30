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
import net.minecraft.util.StrictJsonParser
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
            val loader = TagLoader(TagLoader.ElementLookup.fromFrozenRegistry(copy),
                net.minecraft.core.registries.Registries.tagsDirPath(copy.key()))
            val definitions = loader.load(resources)
            val tags = loader.build(definitions)
            for (id in definitions.keys - tags.keys) unsupported["[C] tag $id"] = "Local tag has missing entries or a cycle"
            tags.forEach { (id, holders) -> copy.bindTag(net.minecraft.tags.TagKey.create(copy.key(), id), holders) }
            copy.freeze()
            return copy
        }
        val copies = BuiltInRegistries.REGISTRY.map { copyRegistry(it) }
        val registries = RegistryAccess.ImmutableRegistryAccess(copies).freeze()

        val defaults = VanillaRegistries.createLookup()
        val lookup = HolderLookup.Provider.create(java.util.stream.Stream.concat(
            registries.listRegistries(), defaults.listRegistries().filter { registries.lookup(it.key()).isEmpty }))

        val converter = FileToIdConverter.json("recipe")
        val entries = mutableListOf<Entry>()
        val files = converter.listMatchingResources(resources).entries.sortedBy { it.key.toString() }
        if (files.size > 50_000) unsupported["[C] limit"] = "Local recipe limit 50000; remaining files skipped"
        for ((file, resource) in files.take(50_000)) {
            val id = converter.fileToId(file).toString()
            try {
                val bytes = resource.open().use { it.readNBytes(512 * 1024 + 1) }
                require(bytes.size <= 512 * 1024) { "Recipe exceeds 512 KiB" }
                val json = StrictJsonParser.parse(bytes.toString(Charsets.UTF_8).reader())

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

    fun decode(entry: Entry, loaded: Loaded) = net.minecraft.world.item.crafting.Recipe.CODEC
        .parse(loaded.lookup.createSerializationContext(JsonOps.INSTANCE), entry.json).getOrThrow()
}
