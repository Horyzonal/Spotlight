package dev.spotlight.platform

object CommandSuggestionTextCheck {
    @JvmStatic fun main(args: Array<String>) {
        check(commandTokenStart("/give @a[name=\"A B\",nbt={foo:'C D'}] dia") == 37)
        val selector = "/kill @e[name=\"A \\\"B\",type=minecraft:zombie"
        check(commandTokenStart(selector) == 6)
        check(commandTokenStart("/say \"hello world") == 5)
        check(commandTokenStart("/give @s minecraft:stone[custom_name={text:'A B'}] ") == 51)
        check(commandTokenStart("/give @s ") == 9)
        check(commandTokenStart("/") == 1)
        check(validatedSlash("/time query daytime   ") == "time query daytime")
        check(validatedSlash("/say hi\u00a0 ") == "say hi\u00a0")
        check(validatedSlash("/" + "x".repeat(32767)).length == 32767)
        for (text in listOf("/say hi\r ", "/say hi\n ", "/say hi\r\n", "/say hi\t ", "/say hi\u007f ",
            "/" + "x".repeat(32768), "/" + " ".repeat(32768), "/ ", "//say hi")) {
            check(runCatching { validatedSlash(text) }.isFailure) { "Accepted invalid raw command" }
        }
        for ((alias, selector) in listOf("me" to "@s", "m" to "@s", "ME" to "@s", "M" to "@s",
            "everyone" to "@a", "random" to "@r", "nearest" to "@p", "entities" to "@e")) {
            check(commandAlias(alias) == selector)
        }
        check(commandAlias("\"me\"") == null)
        check(commandAlias("@s") == null)
        val tokens = listOf(
            "/" to "", "/give " to "", "/give  " to "", "/give @s stone" to "stone",
            "/say \"a [ b ] c\"" to "\"a [ b ] c\"",
            "/kill @e[nbt={a:[{b:\"x ] } y\"}],name='a b']" to "@e[nbt={a:[{b:\"x ] } y\"}],name='a b']",
            "/say \"escaped \\\" quote\" next" to "next",
            "/say \"slash \\\\\" next" to "next",
            "/kill @e[name='a b'" to "@e[name='a b'",
            "/give @s stone[foo={bar:[1,2]}] next" to "next",
            "/give @s stone[foo={] still" to "stone[foo={] still",
            "/say a\tb" to "a\tb"
        )
        for ((text, expected) in tokens) check(text.substring(commandTokenStart(text)) == expected) { text }
        check(runCatching { commandTokenStart("") }.isFailure)
        val s = CommandSuggestion("minecraft:diamond", "Diamond", start = 9, end = 12)
        check(replaceCommandSuggestion("/give @s dia 3", s) == "/give @s minecraft:diamond 3")
        check(replaceCommandSuggestion("/", s.copy(start = 1, end = 1)) == "/minecraft:diamond")
        for ((start, end) in listOf(0 to 1, -1 to 1, 3 to 2, 1 to 6)) {
            check(runCatching { replaceCommandSuggestion("/give", s.copy(start = start, end = end)) }.isFailure)
        }
        check(runCatching { replaceCommandSuggestion("give", s.copy(start = 1, end = 2)) }.isFailure)
        val nested = "/kill @e[name=\"A B\",type=zom]"
        val offset = nested.indexOf("zom")
        check(replaceCommandSuggestion(nested, s.copy(value = "minecraft:zombie", start = offset, end = offset + 3)) ==
            "/kill @e[name=\"A B\",type=minecraft:zombie]")
        check(commandMatch("dmnd", "minecraft:diamond") == 3)
        check(commandMatch("gove", "give") == 4)
        check(commandMatch("zzz", "give") == Int.MAX_VALUE)
        for (ip in listOf("127.0.0.1", "0.0.0.0", "255.255.255.255", "::1", "::", "2001:db8::1", "::ffff:192.0.2.1")) {
            check(validCommandIp(ip)) { "Rejected $ip" }
        }
        for (ip in listOf("", "localhost", "example.org", "256.0.0.1", "1.2.3.-1", "127.1", "01.2.3.4",
            "1.2.3.4:25565", "[::1]", "fe80::1%en0", "1::2::3", "2001:db8:1", "::gg", " ::1", "::1\n")) {
            check(!validCommandIp(ip)) { "Accepted $ip" }
        }
        println("Command suggestion text checks passed")
    }
}
