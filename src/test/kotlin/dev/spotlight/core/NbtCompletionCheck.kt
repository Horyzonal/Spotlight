package dev.spotlight.core

object NbtCompletionCheck {
    @JvmStatic
    fun main(args: Array<String>) {
        val flag = NbtShape("boolean", choices = listOf("false", "true"))
        val child = NbtShape("compound", mapOf("flag" to flag, "quoted key" to NbtShape("string")))
        val root = NbtShape("compound", mapOf("nested" to child, "list" to NbtShape("list", element = child), "name" to NbtShape("string"), "a\"b" to flag))
        fun complete(text: String, cursor: Int = text.length) = NbtCompletion.complete(text, 0, root, cursor)
        check(complete("{ne").single().value == "nested:")
        check(complete("{nested:{fl").single().let { it.value == "flag:" && it.type == "boolean" && it.parentTypes == listOf("compound", "compound") })
        check(complete("{list:[{fl").single().parentTypes == listOf("compound", "list", "compound"))
        check(complete("{nested:{flag:tr").single().value == "true")
        check(complete("{name:\"secret,}\\\"x\",ne").single().value == "nested:")
        check(complete("{list:[{flag:true}],ne").single().value == "nested:")
        check(complete("{bytes:[B;1b,-2b],ints:[I;1,2],longs:[L;3L],ne").single().value == "nested:")
        check(complete("{nested:{'quo").single().value == "'quoted key':")
        check(complete("{\"a\\\"").single().value == "\"a\\\"b\":")
        val text = "{nested:{flag:false},name:\"keep\"}"
        val row = complete(text, text.indexOf("flag") + 2).single()
        check(row.start == text.indexOf("flag") && row.end == text.indexOf("flag") + 4)
        check(text.replaceRange(row.start, row.end, row.value) == text)
        val quoted = "{nested:{\"quoted key\":\"keep\"},name:\"suffix\"}"
        val q = complete(quoted, quoted.indexOf("quoted") + 3).single()
        check(quoted.replaceRange(q.start, q.end, q.value) == quoted)
        check(complete("{name:\"private").isEmpty())
        check(complete("{nested:{flag:true}").isEmpty())
        check(complete("{list:[B;").isEmpty())
        check(complete("{".repeat(40)).isEmpty())
        check(complete(" ".repeat(NbtCompletion.MAX_INPUT + 1)).isEmpty())
        check(NbtCompletion.complete("", 1, root).isEmpty())
        check(NbtCompletion.complete("nested.fl", 0, root, path = true).single().value == "flag")
        check(NbtCompletion.complete("list[0].fl", 0, root, path = true).single().value == "flag")
        check(NbtCompletion.complete("nested.\"quo", 0, root, path = true).single().value == "\"quoted key\"")
        val many = NbtShape("compound", (0..200).associate { "key$it" to flag })
        check(NbtCompletion.complete("{", 0, many).size == 128)
        println("NbtCompletionCheck passed")
    }
}
