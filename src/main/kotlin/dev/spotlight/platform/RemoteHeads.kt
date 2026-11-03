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
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal data class RemoteHead(val id: UUID, val name: String, val texture: String)

/** Public API documented at https://headdb.org/api. No commands or NBT from provider are used. */
internal class RemoteHeads(private val directory: Path) {
    companion object {
        const val ENDPOINT = "https://headdb.org/api/category/all"
        const val MAX_BYTES = 4 * 1024 * 1024
        const val MAX_HEADS = 10_000
        fun parse(json: String): List<RemoteHead> {
            require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "HeadDB response exceeds 4 MiB" }
            val root = JsonParser.parseString(json).asJsonObject
            require(root.size() in 1..MAX_HEADS) { "HeadDB must contain 1..10000 heads" }
            val textures = HashSet<String>()
            return root.entrySet().mapNotNull { (key, element) ->
                val obj = element.asJsonObject
                val uuid = obj.get("uuid").asString
                require(uuid.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) && key.equals(uuid, true)) { "Invalid HeadDB UUID" }
                val name = obj.get("name").asString
                require(name.isNotBlank() && name.length <= 128 && name.none { it.isISOControl() || it == '§' }) { "Invalid HeadDB name" }
                val value = obj.get("value").asString
                require(value.length in 1..8192) { "Invalid HeadDB texture size" }
                val decoded = JsonParser.parseString(Base64.getDecoder().decode(value).toString(Charsets.UTF_8)).asJsonObject
                val url = decoded.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").asString
                // Historical API values use HTTP; rebuild only allowlisted texture URL as HTTPS.
                require(url.matches(Regex("https?://textures\\.minecraft\\.net/texture/[0-9a-fA-F]{32,64}"))) { "Invalid HeadDB texture URL" }
                val safeUrl = "https://textures.minecraft.net/texture/${url.substringAfterLast('/').lowercase(java.util.Locale.ROOT)}"
                val safe = JsonObject().apply { add("textures", JsonObject().apply { add("SKIN", JsonObject().apply { addProperty("url", safeUrl) }) }) }
                if (textures.add(safeUrl)) RemoteHead(UUID.fromString(uuid), name, Base64.getEncoder().encodeToString(safe.toString().toByteArray(Charsets.UTF_8))) else null
            }
        }
    }
    @Volatile var heads: List<RemoteHead> = emptyList()
        private set
    @Volatile var status = "HeadDB not loaded · Refresh online to connect"
        private set
    private var pending: CompletableFuture<List<RemoteHead>>? = null
    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build() }
    private fun cache(): Path {
        require(!Files.isSymbolicLink(directory)) { "Head directory cannot be a symlink" }
        return directory.resolve("remote-heads.json").also { require(!Files.isSymbolicLink(it)) { "HeadDB cache cannot be a symlink" } }
    }
    init {
        runCatching {
            val file = cache()
            if (Files.exists(file, NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) <= MAX_BYTES) { "Invalid HeadDB cache" }
                heads = parse(Files.newInputStream(file, NOFOLLOW_LINKS).use {
                    val bytes = it.readNBytes(MAX_BYTES + 1)
                    require(bytes.size <= MAX_BYTES) { "HeadDB cache exceeds 4 MiB" }
                    bytes.toString(Charsets.UTF_8)
                })
                status = "HeadDB cached · ${heads.size} heads · Refresh online to update"
            }
        }.onFailure { status = "HeadDB cache unreadable · Refresh online to retry" }
    }
    internal fun accept(json: String): List<RemoteHead> {
        val parsed = parse(json)
        Files.createDirectories(directory)
        val destination = cache()
        val temporary = Files.createTempFile(directory, "remote-heads-", ".tmp")
        try {
            Files.writeString(temporary, json)
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
        heads = parsed
        status = "HeadDB updated · ${parsed.size} heads"
        return parsed
    }
    @Synchronized fun refresh(): CompletableFuture<List<RemoteHead>> {
        pending?.takeUnless { it.isDone }?.let { return it }
        status = "Loading HeadDB.org… · cached/local heads remain available"
        val request = HttpRequest.newBuilder(URI.create(ENDPOINT)).timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json").header("User-Agent", "Spotlight/HeadDatabase").GET().build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(Charsets.UTF_8), MAX_BYTES.toLong()))
            .thenApply { response ->
                check(response.statusCode() == 200) { "HeadDB HTTP ${response.statusCode()}" }
                accept(response.body())
            }.whenComplete { _, error ->
                if (error != null) status = "HeadDB refresh failed · cached/local heads retained"
            }.also { pending = it }
    }
}
