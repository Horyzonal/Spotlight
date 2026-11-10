package dev.spotlight.platform

import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.registries.VanillaRegistries

import net.minecraft.server.Bootstrap
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object ItemFactsCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val mock = """["apple",["Apple","Bad"],["",""],["https://minecraft.wiki/wiki/Apple","https://evil.example/wiki/Bad"]]"""
        check(WikiSearch.parse(mock.toByteArray()).map { it.title } == listOf("Apple"))
        listOf("http://minecraft.wiki/wiki/A", "https://minecraft.wiki.evil/wiki/A", "https://minecraft.wiki@evil.example/wiki/A",
            "javascript:alert(1)", "https://minecraft.wiki/wiki/../api.php", "https://minecraft.wiki/wiki/%2e%2e/api.php",
            "https://minecraft.wiki/wiki/A?redirect=evil", "https://minecraft.wiki:444/wiki/A").forEach {
            check(WikiSearch.articleUrl(it) == null) { it }
        }
        check(WikiSearch.articleUrl("https://minecraft.wiki/wiki/Apple#Food") != null)
        check(WikiSearch.articleUrl("https://minecraft.wiki/w/Diamond") != null)
        check(WikiSearch.articleUrl("https://minecraft.wiki/w/%2e%2e/api.php") == null)
        val liveShape = """["diamond",["Diamond","Diamond Ore"],["",""],["https://minecraft.wiki/w/Diamond","https://minecraft.wiki/w/Diamond_Ore"]]"""
        check(WikiSearch.parse(liveShape.toByteArray()).map { it.title } == listOf("Diamond", "Diamond Ore"))
        check(runCatching { WikiSearch.parse(ByteArray(65537)) }.isFailure)
        check(runCatching { WikiSearch.parse("[]".toByteArray()) }.isFailure)
        check(runCatching { WikiSearch.parse("[\"x\",[\"A\"],[],[]]".toByteArray()) }.isFailure)
        check(WikiSearch.parse("[\"missing\",[],[],[]]".toByteArray()).isEmpty())
        val fallback = WikiSearch.browserSearchUrl(" diamond & emerald #雪 ")
        check(fallback.host == "minecraft.wiki" && fallback.path == "/w/Special:Search")
        check(fallback.rawQuery == "search=diamond+%26+emerald+%23%E9%9B%AA" && fallback.fragment == null)
        check(runCatching { WikiSearch.browserSearchUrl(" ") }.isFailure)
        check(runCatching { WikiSearch.browserSearchUrl("x".repeat(201)) }.isFailure)
        check(runCatching { WikiSearch.browserSearchUrl("bad\nquery") }.isFailure)
        check(runCatching { WikiSearch.search(" ") }.isFailure)
        check(runCatching { WikiSearch.search("x".repeat(201)) }.isFailure)
        check(InstalledMods.webLink("file:///tmp/test") == null)
        check(InstalledMods.webLink("https://example.org/issues") != null)
        val installed = InstalledMods.search("spotlight").single { it.id == "spotlight" }
        check(installed.name == "Spotlight" && installed.version == "1.0.3")
        check(installed.homepage.toString() == "https://codeberg.org/horyzon/Spotlight/")
        val metadata = net.minecraftforge.fml.ModList.get().getModContainerById("spotlight").orElseThrow().modInfo
        check(metadata.config.getConfigElement<String>("authors").orElseThrow() == "Horyzon")
        check(InstalledMods.search("kotlinforforge").any { it.id == "kotlinforforge" })
        check(!InstalledMods.hasConfigScreen("spotlight:missing"))

        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        check(ItemFacts.describe(ItemStack.EMPTY).isEmpty())
        val apple = ItemStack(Items.APPLE)
        val food = FoodProperties.Builder().nutrition(4).saturationMod(0.3f).build()
        check(kotlin.math.abs(food.nutrition * food.saturationModifier * 2 - 2.4f) < 0.0001f)
        val facts = ItemFacts.describe(apple).map { it.string }
        check("Nutrition: 4 hunger points" in facts)
        check("Saturation: 2.4 points (before hunger cap)" in facts)
        check(ItemFacts.summary(apple) == "Hunger 4 · Saturation 2.4")
        check(ItemFacts.summary(ItemStack.EMPTY) == null)
        check(ItemFacts.describe(apple).any { it.string.startsWith("Consume duration:") })
        val sword = ItemStack(Items.DIAMOND_SWORD)
        check(ItemFacts.describe(sword).any { it.string.startsWith("Durability:") })
        check(ItemFacts.summary(sword)?.startsWith("Durability ") == true)
        check(ItemFacts.describe(sword).any { it.string.startsWith("Attribute bonus") })
        val disc = ItemStack(Items.MUSIC_DISC_13)
        check(ItemFacts.describe(disc).any { it.string.startsWith("Comparator output:") })
        val song = disc.item as net.minecraft.world.item.RecordItem
        check(ItemFacts.summary(disc) == ItemFacts.summary(disc.copy()))
        val seconds = song.lengthInTicks / 20
        check(ItemFacts.summary(disc) == "Comparator ${song.analogOutput} · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}")
        check(ItemFacts.describe(disc).any { it.string == "Track duration: ${song.lengthInTicks / 20} seconds" })
        println("ItemFactsCheck passed: native facts, catalog summaries, offline wiki parsing and URL validation")
    }
}
