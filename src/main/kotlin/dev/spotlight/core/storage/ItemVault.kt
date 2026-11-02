package dev.spotlight.core.storage

import net.minecraft.nbt.*
import java.io.ByteArrayOutputStream
import java.nio.file.*
import java.util.UUID

class ItemVault(val directory: Path) {
    companion object {
        const val MAX_BYTES = 16 * 1024 * 1024
        const val DISK_CAP = 256L * 1024 * 1024
        const val MAX_PAGE = Int.MAX_VALUE
        fun page(number: Int): Int { require(number > 0) { "Page must be 1..2147483647" }; return number }
        fun read(path: Path): CompoundTag {
            require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "Not a regular local NBT file" }
            require(Files.size(path) <= MAX_BYTES) { "NBT file exceeds 16 MiB" }
            return Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input -> java.util.zip.GZIPInputStream(input).use { gzip -> NbtIo.read(java.io.DataInputStream(gzip), NbtAccounter(64L * 1024 * 1024)) } }
        }
    }
    private val current = directory.resolve("vault.nbt")
    private var state: CompoundTag
    init {
        Files.createDirectories(directory)
        require(!Files.isSymbolicLink(directory)) { "Vault directory cannot be a symlink" }
        state = if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) read(current) else CompoundTag().apply {
            putInt("Version", 1); putLong("Revision", 0)
            put("Pages", CompoundTag()); put("Kits", CompoundTag()); put("Archive", CompoundTag())
        }
        require(state.getInt("Version") == 1) { "Unsupported vault version; original preserved" }
        listOf("Pages", "Kits", "Archive").forEach { require(state.get(it) is CompoundTag) { "Invalid vault $it; original preserved" } }
    }
    fun snapshot(): CompoundTag = state.copy()
    fun revision(): Long = state.getLong("Revision")
    fun mutate(edit: (CompoundTag) -> Unit) {
        val next = state.copy()
        edit(next)
        if (next == state) return
        next.putLong("Revision", Math.addExact(revision(), 1))
        require(next.sizeInBytes() <= 64 * 1024 * 1024) { "Vault capacity exceeded; edit not saved" }
        val bytes = ByteArrayOutputStream().also { NbtIo.writeCompressed(next, it) }.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Vault capacity exceeded; edit not saved" }
        val used = diskUsage()
        check(used + bytes.size * 2L <= DISK_CAP) { "Vault disk cap 256 MiB reached; edit not saved" }
        val revision = directory.resolve("revision-${next.getLong("Revision")}-${UUID.randomUUID()}.nbt")
        atomicWrite(revision, bytes, false)
        atomicWrite(current, bytes, true)
        state = next
        runCatching {
            val revisions = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("revision-") && it.fileName.toString().endsWith(".nbt") }.sorted(compareByDescending<Path> { it.fileName.toString().substringAfter("revision-").substringBefore('-').toLongOrNull() ?: -1 }).toList() }
            revisions.drop(100).forEach { Files.delete(it) }
        }
    }
    fun mutatePage(number: Int, edit: (CompoundTag, CompoundTag) -> Unit) {
        page(number)
        mutate { root ->
            val pages = root.getCompound("Pages")
            val existing = (pages.get(number.toString()) as? CompoundTag)
            val draft = existing?.copy() ?: CompoundTag().apply { putString("Name", "Page $number"); put("Items", ListTag()) }
            val before = draft.copy()
            edit(draft, root)
            if (draft != before) pages.put(number.toString(), draft)
        }
    }
    fun history(): List<String> = Files.list(directory).use { paths -> paths.filter { it.fileName.toString().startsWith("revision-") && it.fileName.toString().endsWith(".nbt") }.map { it.fileName.toString() }.sorted(compareBy<String> { it.substringAfter("revision-").substringBefore('-').toLongOrNull() ?: -1 }).toList() }
    fun restoreRevision(name: String) {
        check(!state.contains("Cursor")) { "Place saved local cursor before restoring revisions" }
        require(name in history()) { "Unknown revision" }
        val restored = read(directory.resolve(name))
        require(restored.getInt("Version") == 1) { "Unsupported revision" }
        mutate { target ->
            listOf("Pages", "Kits", "Archive").forEach { key -> target.put(key, requireNotNull(restored.get(key) as? CompoundTag).copy()) }
            listOf("Cursor", "CursorSource", "ArchiveOrder", "ArchiveSequence").forEach { key ->
                val value = restored.get(key)
                if (value == null) target.remove(key) else target.put(key, value.copy())
            }
        }
    }
    fun localFile(name: String): Path {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,80}\\.nbt"))) { "Use a local filename ending .nbt (letters, digits, _ or -)" }
        val exports = directory.resolve("exports")
        Files.createDirectories(exports)
        require(!Files.isSymbolicLink(exports)) { "Exports directory cannot be a symlink" }
        return exports.resolve(name)
    }
    fun export(name: String, tag: CompoundTag) {
        val bytes = ByteArrayOutputStream().also { NbtIo.writeCompressed(tag, it) }.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Export exceeds 16 MiB" }
        check(diskUsage() + bytes.size <= DISK_CAP) { "Vault disk cap 256 MiB reached" }
        atomicWrite(localFile(name), bytes, false)
    }
    private fun diskUsage(): Long = Files.walk(directory).use { paths -> paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.mapToLong { Files.size(it) }.sum() }
    private fun atomicWrite(path: Path, bytes: ByteArray, replace: Boolean) {
        require(!Files.isSymbolicLink(path)) { "Refusing symlink" }
        if (!replace) require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "File exists; choose another filename" }
        val temp = Files.createTempFile(path.parent, ".vault-", ".tmp")
        try {
            java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            if (replace) Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            else {
                Files.createLink(path, temp)
            }
        } finally { Files.deleteIfExists(temp) }
    }
}
