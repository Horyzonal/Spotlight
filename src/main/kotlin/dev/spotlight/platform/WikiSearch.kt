package dev.spotlight.platform

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow

object WikiSearch {
    const val ENDPOINT = "https://minecraft.wiki/api.php"
    const val MAX_BYTES = 65536
    data class Result(val title: String, val url: URI)
    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build() }
    private val cache = object : LinkedHashMap<String, List<Result>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Result>>?) = size > 32
    }

    fun articleUrl(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme == "https" && it.host == "minecraft.wiki" && it.port == -1 && it.rawUserInfo == null &&
            (it.rawPath.startsWith("/w/") || it.rawPath.startsWith("/wiki/")) && !it.path.split('/').any { part -> part == ".." || part == "." } &&
            !it.rawPath.contains("%2f", true) && !it.rawPath.contains("%5c", true) && !it.path.contains('\\') && it.rawQuery == null
    }

    fun parse(bytes: ByteArray): List<Result> {
        require(bytes.size <= MAX_BYTES) { "Wiki response exceeds 64 KiB" }
        val root = JsonParser.parseString(String(bytes, UTF_8)).asJsonArray
        require(root.size() == 4) { "Invalid OpenSearch response" }
        val titles = root[1].asJsonArray
        val urls = root[3].asJsonArray
        require(titles.size() == urls.size() && titles.size() <= 10) { "Invalid OpenSearch results" }
        return titles.mapIndexedNotNull { index, title ->
            require(title.isJsonPrimitive && title.asJsonPrimitive.isString) { "Invalid title" }
            require(urls[index].isJsonPrimitive && urls[index].asJsonPrimitive.isString) { "Invalid URL" }
            val text = title.asString
            require(text.isNotBlank() && text.length <= 512 && text.none { it.isISOControl() }) { "Invalid title" }
            articleUrl(urls[index].asString)?.let { Result(text, it) }
        }
    }

    fun browserSearchUrl(term: String): URI {
        val query = term.trim()
        require(query.isNotEmpty() && query.length <= 200 && query.none { it.isISOControl() }) { "Enter 1–200 printable characters" }
        return URI("https://minecraft.wiki/w/Special:Search?search=${URLEncoder.encode(query, UTF_8)}")
    }

    fun search(term: String): CompletableFuture<List<Result>> {
        val query = term.trim()
        require(query.isNotEmpty() && query.length <= 200 && query.none { it.isISOControl() }) { "Enter 1–200 printable characters" }
        synchronized(cache) { cache[query]?.let { return CompletableFuture.completedFuture(it) } }
        val uri = URI("$ENDPOINT?action=opensearch&format=json&namespace=0&limit=10&search=${URLEncoder.encode(query, UTF_8)}")
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json").header("User-Agent", "Spotlight/0.1 (Minecraft wiki search)").build()
        val exchange = client.sendAsync(request, HttpResponse.BodyHandler { BoundedBody() })
        val result = exchange.thenApply { response ->
            require(response.statusCode() == 200) { "Wiki HTTP ${response.statusCode()}" }
            parse(response.body()).also { synchronized(cache) { cache[query] = it } }
        }.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        result.whenComplete { _, failure -> if (failure != null) exchange.cancel(true) }
        return result
    }

    private class BoundedBody : HttpResponse.BodySubscriber<ByteArray> {
        private val delegate = HttpResponse.BodySubscribers.ofByteArray()
        private lateinit var subscription: Flow.Subscription
        private var count = 0
        override fun getBody() = delegate.body
        override fun onSubscribe(value: Flow.Subscription) { subscription = value; delegate.onSubscribe(value) }
        override fun onNext(items: List<ByteBuffer>) {
            val received = items.sumOf { it.remaining().toLong() }
            if (count + received > MAX_BYTES) {
                subscription.cancel()
                delegate.onError(IllegalArgumentException("Wiki response exceeds 64 KiB"))
            } else { count += received.toInt(); delegate.onNext(items) }
        }
        override fun onError(error: Throwable) = delegate.onError(error)
        override fun onComplete() = delegate.onComplete()
    }
}
