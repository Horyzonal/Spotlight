package dev.spotlight.platform

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal data class RemoteHead(val id: UUID, val name: String, val texture: String, val tags: List<String>)

internal class RemoteHeads(private val directory: Path, private val fetch: (String) -> CompletableFuture<String> = ::download) {
    companion object {
        val CATEGORIES = listOf("alphabet", "animals", "blocks", "decoration", "food-drinks", "humans", "humanoid", "miscellaneous", "monsters", "plants")
        private val logger = org.slf4j.LoggerFactory.getLogger("spotlight.heads")
        internal fun failureMessage(error: Throwable): String {
            var cause = error
            while ((cause is java.util.concurrent.CompletionException || cause is java.util.concurrent.ExecutionException) && cause.cause != null) cause = cause.cause!!
            return cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
        }
        const val ENDPOINT = "https://minecraft-heads.com/scripts/api.php"
        const val MAX_BYTES = 16 * 1024 * 1024
        const val MAX_HEADS = 50_000
        const val MAX_TOTAL_HEADS = 150_000
        private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build() }
        private fun download(category: String): CompletableFuture<String> {
            require(category in CATEGORIES)
            val request = HttpRequest.newBuilder(URI.create("$ENDPOINT?cat=$category&tags=true"))
                .timeout(Duration.ofSeconds(60)).header("Accept", "application/json")
                .header("User-Agent", "Spotlight/1.0.3 (Minecraft head browser)").GET().build()
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply { response ->
                    check(response.statusCode() == 200) { "Minecraft Heads HTTP ${response.statusCode()}" }
                    response.body().use { input ->
                        val bytes = input.readNBytes(MAX_BYTES + 1)
                        require(bytes.size <= MAX_BYTES) { "Category exceeds 16 MiB" }
                        bytes.toString(Charsets.UTF_8)
                    }
                }
        }
        fun parse(json: String): List<RemoteHead> {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Category exceeds 16 MiB" }
            val root = JsonParser.parseString(json).asJsonArray
            require(root.size() in 1..MAX_HEADS) { "Category must contain 1..50000 heads" }
            val textures = HashSet<String>()
            return root.mapNotNull { element ->
                val obj = element.asJsonObject
                fun string(key: String): String {
                    val value = obj.get(key)
                    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Expected string: $key" }
                    return value.asString
                }
                val uuid = string("uuid")
                require(uuid.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) { "Invalid head UUID" }
                val name = string("name").replace('§', '&')
                require(name.isNotBlank() && name.length <= 128 && name.none { it.isISOControl() || it == '§' }) { "Invalid head name" }
                val tags = string("tags")
                require(tags.length <= 4096 && tags.none { it.isISOControl() || it == '§' }) { "Invalid head tags" }
                val value = string("value")
                require(value.length in 1..8192) { "Invalid texture size" }
                val decoded = JsonParser.parseString(Base64.getDecoder().decode(value).toString(Charsets.UTF_8)).asJsonObject
                val url = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").asString
                require(url.matches(Regex("https?://textures\\.minecraft\\.net/texture/[0-9a-fA-F]{32,64}"))) { "Invalid texture URL" }
                val safeUrl = "https://textures.minecraft.net/texture/${url.substringAfterLast('/').lowercase(Locale.ROOT)}"
                val safe = JsonObject().apply { add("textures", JsonObject().apply { add("SKIN", JsonObject().apply { addProperty("url", safeUrl) }) }) }
                if (textures.add(safeUrl)) RemoteHead(UUID.fromString(uuid), name, Base64.getEncoder().encodeToString(safe.toString().toByteArray(Charsets.UTF_8)), tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }) else null
            }
        }
    }
    @Volatile private var categories: Map<String, List<RemoteHead>> = emptyMap()
    @Volatile var status = "Legacy API deprecated · Load online explicitly"
        private set
    val heads get() = heads(null)
    fun heads(category: String?): List<RemoteHead> = if (category == null) categories.values.flatten().distinctBy { it.texture } else categories[category].orEmpty()
    private var pending: CompletableFuture<List<RemoteHead>>? = null
    private var lastRequest = 0L
    private val refreshedAt = mutableMapOf<String, Long>()
    private fun cache(category: String): Path {
        require(category in CATEGORIES)
        require(!Files.isSymbolicLink(directory)) { "Head directory cannot be a symlink" }
        return directory.resolve("minecraft-heads-$category.json").also { require(!Files.isSymbolicLink(it)) { "Head cache cannot be a symlink" } }
    }
    init {
        CATEGORIES.forEach { category ->
            runCatching {
                val file = cache(category)
                if (Files.exists(file, NOFOLLOW_LINKS)) {
                    require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= MAX_BYTES) { "Invalid category cache" }
                    val json = Files.newInputStream(file, NOFOLLOW_LINKS).use {
                        val bytes = it.readNBytes(MAX_BYTES + 1)
                        require(bytes.size <= MAX_BYTES) { "Category cache exceeds 16 MiB" }
                        bytes.toString(Charsets.UTF_8)
                    }
                    val parsed = parse(json)
                    require(categories.values.sumOf { it.size } + parsed.size <= MAX_TOTAL_HEADS) { "Cache exceeds 150000 heads" }
                    categories = categories + (category to parsed)
                    status = "Cached categories available · Legacy API deprecated · Refresh explicitly"
                }
            }.onFailure {
                logger.warn("Cannot read Minecraft Heads cache for category {}", category, it)
                status = "Cache $category: ${failureMessage(it)} · Other cached heads retained"
            }
        }
    }
    @Synchronized internal fun accept(category: String, json: String): List<RemoteHead> {
        val destination = cache(category)
        val parsed = parse(json)
        require(categories.filterKeys { it != category }.values.sumOf { it.size } + parsed.size <= MAX_TOTAL_HEADS) { "Cache exceeds 150000 heads" }
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "minecraft-heads-", ".tmp")
        try {
            Files.writeString(temporary, json)
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        categories = categories + (category to parsed)
        return parsed
    }
    @Synchronized fun refresh(category: String? = null, force: Boolean = true): CompletableFuture<List<RemoteHead>> {
        require(category == null || category in CATEGORIES) { "Unknown category" }
        if (pending?.isDone == false) return CompletableFuture.failedFuture(IllegalStateException("Online load already running"))
        val now = System.nanoTime()
        val requested = (category?.let(::listOf) ?: CATEGORIES).filter {
            it !in categories || force && now - refreshedAt.getOrDefault(it, 0L) >= java.util.concurrent.TimeUnit.SECONDS.toNanos(60)
        }
        status = "Loading ${category ?: "all categories sequentially"} · Cached heads remain available"
        var chain = CompletableFuture.completedFuture(Unit)
        requested.forEach { key ->
            chain = chain.thenComposeAsync {
                status = "Loading $key · Legacy API deprecated"
                val delay = synchronized(this) {
                    val current = System.nanoTime()
                    val scheduled = maxOf(current, lastRequest + java.util.concurrent.TimeUnit.SECONDS.toNanos(1))
                    lastRequest = scheduled
                    scheduled - current
                }
                CompletableFuture.runAsync({}, CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.NANOSECONDS))
                    .thenCompose { fetch(key) }.thenApplyAsync { json ->
                        accept(key, json)
                        synchronized(this) { refreshedAt[key] = System.nanoTime() }
                        Unit
                    }.whenComplete { _, error ->
                    if (error != null) logger.error("Minecraft Heads category $key failed ($ENDPOINT?cat=$key&tags=true)", error)
                }
            }
        }
        return chain.thenApply { heads(category) }.whenComplete { _, error ->
            status = if (error == null) "Minecraft Heads loaded · Legacy API deprecated · Refresh explicitly" else "Online load failed: ${failureMessage(error)} · Prior category caches retained"
        }.also { pending = it }
    }
}
