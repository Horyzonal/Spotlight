package dev.spotlight.ui

object SelectorModelCheck {
    @JvmStatic fun main(args: Array<String>) {
        val raw = "@e[type=minecraft:zombie,name=\"A \\\"B\",future={a:[1,2]}]"
        val model = SelectorModel.parse(raw)
        check(model.text() == raw)
        check(model.set("type", "minecraft:pig").text() == raw.replace("minecraft:zombie", "minecraft:pig"))
        val command = "/execute as $raw run say hello"
        val span = requireNotNull(selectorSpan(command, 15))
        check(command.replaceRange(span.first, span.last + 1, "@s") == "/execute as @s run say hello")
        check(selectorSpan("/say \"@e[type=pig]\"", 12) == null)
        check(selectorSpan("/kill @e[type=", 14) == 6..13)
        for (bad in listOf("@e[name=\"bad]", "@e[tag=x\n]", "@e[tag={a:1]]", "@e[tag=x,]", "@e[tag=foo\\]")) {
            check(runCatching { SelectorModel.parse(bad) }.isFailure) { bad }
        }
        check(runCatching { model.set("tag", "x,limit=1") }.isFailure)
        println("Selector model checks passed")
    }
}
