package dev.spotlight.core

object MacroCompletionCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        fun replace(marked: String, option: String, expected: String) {
            val cursor = marked.indexOf('|')
            val text = marked.replace("|", "")
            val edit = checkNotNull(MacroCompletion.accept(text, cursor, option)) { marked }
            check(edit.text == expected) { "${edit.text} != $expected" }
            check(edit.cursor == edit.text.indexOf(option) + option.length)
            check(MacroCompletion.find(edit.text, edit.cursor) == null)
        }
        replace("{|", "{Player}", "{Player}")
        replace("/say {Ar| suffix", "{Argument=text}", "/say {Argument=text} suffix")
        replace("/say {Pl|ayer} tail", "{Player}", "/say {Player} tail")
        replace("/say {Pl|} {Look}", "{Player}", "/say {Player} {Look}")
        replace("/data merge entity @s {data:{name:\"{Pl|}\"}}", "{Player}",
            "/data merge entity @s {data:{name:\"{Player}\"}}")
        replace("/say '{Ar|'", "{Argument=location}", "/say '{Argument=location}'")
        replace("/say {Argument=target:pl|}", "{Argument=target:player}", "/say {Argument=target:player}")
        replace("/say {Argument=message:te|}", "{Argument=message:text}", "/say {Argument=message:text}")
        for (text in listOf("/say {unknown", "/say {foo:bar", "/say {\"text\":", "/say {Argument=target:unknown")) {
            check(MacroCompletion.find(text, text.length) == null) { text }
        }
        val escaped = "/say \"\\{Pl"
        check(MacroCompletion.find(escaped, escaped.length) == null)
        val outside = "/say \\{Pl"
        check(MacroCompletion.find(outside, outside.length) != null)
        val doubleEscape = "/say \"\\\\{Pl"
        check(MacroCompletion.find(doubleEscape, doubleEscape.length) != null)
        check(MacroCompletion.find("{Pl", 3, 0) == null)
        check(MacroCompletion.accept("{Pl", 3, "{Player}", 0) == null)
        check(MacroCompletion.find("{", -1) == null)
        check(MacroCompletion.find("{", 2) == null)
        check(MacroCompletion.accept("{Pl", 3, "{Look}") == null)
        check(MacroCompletion.find("{Ar", 3)!!.options.contains("{Argument=location}"))
        check(MacroCompletion.find("{pyr", 4)!!.options.contains("{Player}"))
        check(MacroCompletion.find("{", 1)!!.options == MacroTemplates.placeholderOptions())
        for (type in MacroArgumentType.entries) {
            check("{Argument=${type.name.lowercase()}}" in MacroTemplates.placeholderOptions())
        }
        fun ghost(marked: String, option: String, expected: String?) {
            val cursor = marked.indexOf('|')
            val text = marked.replace("|", "")
            val suffix = MacroCompletion.ghostSuffix(text, cursor, option)
            check(suffix == expected) { "$marked: $suffix != $expected" }
            if (suffix != null) {
                val edit = checkNotNull(MacroCompletion.accept(text, cursor, option))
                check(edit.text == text.substring(0, cursor) + suffix + text.substring(cursor))
            }
        }
        ghost("{|", "{Player}", "Player}")
        ghost("/say {pl|", "{Player}", "ayer}")
        ghost("/say {ARGument=target:pl|", "{Argument=target:player}", "ayer}")
        ghost("/say {LookE|", "{LookEntity}", "ntity}")
        ghost("/say first\n{Pl| tail", "{Player}", "ayer}")
        ghost("{pyr|", "{Player}", null)
        ghost("{Pl|ayer}", "{Player}", null)
        ghost("{Pl|}", "{Player}", null)
        ghost("{Player}|", "{Player}", null)
        ghost("{|", "{Unknown}", null)
        check(MacroCompletion.ghostSuffix("{Pl", 3, "{Player}", 0) == null)
        check(MacroCompletion.ghostSuffix("{Pl", -1, "{Player}") == null)
        check(MacroCompletion.ghostSuffix("{Pl", 4, "{Player}") == null)
        check(MacroCompletion.accept("{pyr", 4, "{Player}")?.text == "{Player}")
        check(MacroCompletion.accept("{pl}", 3, "{Player}")?.text == "{player}")
        val recursive = MacroTemplates.expand("/say {Player}", emptyMap(), mapOf("Player" to "{Look}"))
        check(recursive == "/say {Look}")
        println("MacroCompletionCheck passed")
    }
}
