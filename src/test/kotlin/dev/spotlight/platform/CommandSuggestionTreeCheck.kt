package dev.spotlight.platform

import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.ResourceKeyArgument
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.core.registries.Registries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.server.commands.GameRuleCommand
import net.minecraft.world.flag.FeatureFlags
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


object CommandSuggestionTreeCheck {
    @JvmStatic fun main(args: Array<String>) {
        CommandSuggestionTextCheck.main(emptyArray())
        val dispatcher = CommandDispatcher<Boolean>()
        val root = dispatcher.register(literal<Boolean>("give").then(argument<Boolean, Int>("count", IntegerArgumentType.integer())))
        dispatcher.register(literal<Boolean>("secret").requires { it })
        dispatcher.register(literal<Boolean>("alias").redirect(root))
        dispatcher.register(literal<Boolean>("locked").requires { it }.redirect(root))
        fun names(text: String, allowed: Boolean = false) = commandSuggestionChildren(dispatcher, allowed, text, commandTokenStart(text)).map { it.name }
        check(names("/").toSet() == setOf("give", "alias"))
        check("secret" in names("/", true))
        check(names("/gove").contains("give"))
        check(names("/give ") == listOf("count")) { "give children: ${names("/give ")}" }
        check(names("/alias ") == listOf("count"))
                check(names("/give  ") == listOf("count"))
                check(names("/ ").toSet() == setOf("give", "alias"))
        check(names("/locked ").isEmpty())
        check(names("/unknown ").isEmpty())
        check(names("/give nope ").isEmpty())
        check(commandRegistryId(JsonObject().also { it.add("registry", JsonObject()) }) == null)
        check(commandRegistryId(JsonObject().also { it.addProperty("registry", true) }) == null)

        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val aliases = CommandDispatcher<Boolean>()
        val targets = aliases.register(literal<Boolean>("target")
            .then(argument<Boolean, net.minecraft.commands.arguments.selector.EntitySelector>("who", EntityArgument.entities())))
        aliases.register(literal<Boolean>("redirect").redirect(targets))
        aliases.register(literal<Boolean>("player").then(argument("who", EntityArgument.player())))
        aliases.register(literal<Boolean>("profile").then(argument("who", GameProfileArgument.gameProfile())))
        aliases.register(literal<Boolean>("say").then(argument("message", StringArgumentType.greedyString())))
        aliases.register(literal<Boolean>("locked").requires { it }.redirect(targets))
        fun expand(text: String, cursor: Int = text.length) = expandCommandAlias(aliases, false, text, cursor) { argument, selector ->
            when (argument) {
                is EntityArgument -> selector
                is GameProfileArgument -> if (selector == "@s") "LocalPlayer" else null
                else -> null
            }
        }
        for ((alias, selector) in listOf("me" to "@s", "m" to "@s", "ME" to "@s", "M" to "@s",
            "everyone" to "@a", "random" to "@r", "nearest" to "@p", "entities" to "@e")) {
            val result = "/target $selector "
            check(expand("/target $alias ") == result to result.length)
        }
        check(expand("/redirect me ")?.first == "/redirect @s ")
        check(expand("/player me ")?.first == "/player @s ")
        check(expand("/player everyone ") == null)
        check(expand("/player entities ") == null)
        check(expand("/profile m ")?.first == "/profile LocalPlayer ")
        check(expand("/profile everyone ") == null)
        check(expand("/target me  tail", 11) == "/target @s  tail" to 11)
        check(expand("/target  me ")?.first == "/target  @s ")
        check(expand("/profile me ")?.let { expand(it.first, it.second) } == null)
        check(CommandSuggestions.executionText("/give 2   ") == "/give 2")
        check(CommandSuggestions.executionText("/say hi\r\n ") == "/say hi\r\n")
        for (text in listOf("/target me", "/target @s ", "/target \"me\" ", "/target @e[name=me ",
            "/target {name:me ", "/say me ", "/say hello me ", "/unknown me ", "/locked me ",
            "/target nope me ", "/target me\n ")) {
            check(expand(text) == null) { "Unexpected expansion: $text" }
        }
        check(expand("/target me trailing", 11) == null)
        check(expand("/target me ", -1) == null)
        check(expand("/target me ", 100) == null)
        val completion = CommandDispatcher<Boolean>()
        completion.register(literal<Boolean>("tp").then(argument("who", StringArgumentType.word())))
        completion.register(literal<Boolean>("target").then(argument("who", EntityArgument.player())))
        completion.register(literal<Boolean>("profile").then(argument("who", GameProfileArgument.gameProfile())))
        completion.register(literal<Boolean>("say").then(argument("message", StringArgumentType.greedyString())))
        completion.register(literal<Boolean>("quote").then(argument("message", StringArgumentType.string())))
        completion.register(literal<Boolean>("message").then(argument("message", net.minecraft.commands.arguments.MessageArgument.message())))
        completion.register(literal<Boolean>("component").then(argument("value", net.minecraft.commands.arguments.ComponentArgument.textComponent(
            CommandBuildContext.simple(VanillaRegistries.createWorldLookup(), FeatureFlags.DEFAULT_FLAGS)))))
        completion.register(literal<Boolean>("mode").then(literal<Boolean>("creative")))
        completion.register(literal<Boolean>("bool").then(argument("value", BoolArgumentType.bool())))
        completion.register(literal<Boolean>("number").then(argument("value", IntegerArgumentType.integer())))
        completion.register(literal<Boolean>("locked").requires { it }.then(argument("who", StringArgumentType.word())))
        fun complete(text: String, value: String = "Player778", cursor: Int = text.length): Pair<String, Int>? {
            val end = cursor - 1
            val start = commandTokenStart(text.substring(0, end))
            return completeCommandOnSpace(completion, false, text, cursor,
                listOf(CommandSuggestion(value, value, start = start, end = end)))
        }
        check(complete("/tp Pl ") == "/tp Player778 " to 14)
        check(complete("/target Pl ")?.first == "/target Player778 ")
        check(complete("/profile Pl ")?.first == "/profile Player778 ")
        check(complete("/tp pL ")?.first == "/tp Player778 ")
        check(complete("/tp Plyr778 ")?.first == "/tp Player778 ")
        check(complete("/mode cr ", "creative")?.first == "/mode creative ")
        check(complete("/bool tr ", "true")?.first == "/bool true ")
        check(complete("/number 1 ", "10") == null)
        check(complete("/tp Pl tail", cursor = 7) == "/tp Player778 tail" to 14)
        check(complete("/tp Pl  tail", cursor = 7) == "/tp Player778  tail" to 14)
        check(complete("/tp Player778 ") == null)
        for (text in listOf("/say hi ", "/say hi there ", "/quote Pl ", "/message Pl ", "/component Pl ",
            "/tp \"Pl\" ", "/tp @e[name=Pl ", "/tp stone[foo=Pl ", "/tp {name:Pl ", "/locked Pl ",
            "/unknown Pl ", "/tp Pl next ", "/tp  ", "/Pl ")) {
            check(complete(text) == null) { "Unsafe space completion: $text" }
        }
        check(complete("/tp zzz ") == null)
        check(complete("/tp Pl ", "Player778 extra") == null)
        check(complete("/tp Pl ", "Player778[foo]") == null)
        check(completeCommandOnSpace(completion, false, "/tp Pl ", 7,
            listOf(CommandSuggestion("Player778", "Player778", start = 4, end = 5))) == null)
        check(completeCommandOnSpace(completion, false, "/tp Pl ", 7,
            listOf(CommandSuggestion("Player778", "Player778", start = 0, end = 0))) == null)
        check(completeCommandOnSpace(completion, false, "/tp Pl ", 7) { query ->
            check(query == "/tp Pl")
            listOf(CommandSuggestion("Player778", "Player778", start = 4, end = 6))
        }?.first == "/tp Player778 ")
        check(expand("/target m ")?.first == "/target @s ")
        check(singleTokenCommandArgument(ResourceKeyArgument.key(Registries.RECIPE)))
        check(!singleTokenCommandArgument(net.minecraft.commands.arguments.CompoundTagArgument.compoundTag()))
        check(commandArgumentHint(dispatcher, false, "/give ") == "Enter a number")
        check(commandArgumentHint(dispatcher, false, "/give nope") == "Enter a number")
        check(commandArgumentHint(dispatcher, false, "/give 2 ") == null)
        check(commandArgumentHint(dispatcher, false, "/give nope ") == null)
        check(commandArgumentHint(dispatcher, false, "/unknown ") == null)
        check(commandArgumentHint(dispatcher, false, "/give \n") == null)
        for (numeric in listOf(LongArgumentType.longArg(), FloatArgumentType.floatArg(), DoubleArgumentType.doubleArg())) {
            val numbers = CommandDispatcher<Boolean>()
            numbers.register(literal<Boolean>("number").then(argument("value", numeric)))
            check(commandArgumentHint(numbers, false, "/number ") == "Enter a number")
            check(commandArgumentHint(numbers, false, "/number 2 ") == null)
        }
        val type = ResourceKeyArgument.key(Registries.RECIPE)
        val info = ArgumentTypeInfos.byClass(type)
        val json = JsonObject()
        fun <A : com.mojang.brigadier.arguments.ArgumentType<*>, T : net.minecraft.commands.synchronization.ArgumentTypeInfo.Template<A>> serialize(
            i: net.minecraft.commands.synchronization.ArgumentTypeInfo<A, T>, a: A
        ) { i.serializeToJson(i.unpack(a), json) }
        serialize(info, type)
        check(commandRegistryId(json) == "minecraft:recipe")
        val rules = CommandDispatcher<CommandSourceStack>()
        GameRuleCommand.register(rules, CommandBuildContext.simple(VanillaRegistries.createWorldLookup(), FeatureFlags.DEFAULT_FLAGS))
        val types = rules.root.getChild("gamerule").children.flatMap { it.children }.mapNotNull {
            (it as? com.mojang.brigadier.tree.ArgumentCommandNode<*, *>)?.type
        }
        check(types.any { it is BoolArgumentType } && types.any { it is IntegerArgumentType })

        val unresolved = CompletableFuture<String>()
        check(boundedCommandCompletion(unresolved, 20).get(1, TimeUnit.SECONDS) == null)
        check(!unresolved.isDone)
                unresolved.complete("late")
                check(unresolved.get(1, TimeUnit.SECONDS) == "late")
        check(boundedCommandCompletion(CompletableFuture.completedFuture("ready")).get(1, TimeUnit.SECONDS) == "ready")
        val cancelled = CompletableFuture<String>().also { it.cancel(false) }
        check(boundedCommandCompletion(cancelled).get(1, TimeUnit.SECONDS) == null)
        println("Command suggestion tree checks passed")
    }
}
