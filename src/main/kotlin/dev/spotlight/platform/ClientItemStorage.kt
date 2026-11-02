package dev.spotlight.platform

import dev.spotlight.commands.creativePlan
import dev.spotlight.core.storage.ItemVault
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.network.chat.Component
import net.minecraft.nbt.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import java.security.MessageDigest

object ClientItemStorage {
    private val client get() = Minecraft.getInstance()
    private val vault by lazy { ItemVault(FabricLoader.getInstance().configDir.resolve("spotlight/item-storage")) }
    var failure: String? = null
        private set
    private var archiveStopped = false
    private var previousObserved = emptyList<ItemStack>()
    fun initialize() { try { vault.revision() } catch (e: Exception) { failure = "Vault unavailable: ${e.message}. Original preserved." } }
    private fun store(): ItemVault { check(failure == null) { failure.orEmpty() }; return vault }
    fun revision() = store().revision()
    private fun ops() = requireNotNull(client.connection) { "Join a world first" }.registryAccess().createSerializationContext(NbtOps.INSTANCE)
    fun encode(stack: ItemStack): CompoundTag {
        require(!stack.isEmpty) { "Empty stack" }
        return ItemStack.CODEC.encodeStart(ops(), stack).getOrThrow { IllegalArgumentException(it) } as CompoundTag
    }
    fun decode(raw: CompoundTag): ItemStack = ItemStack.CODEC.parse(ops(), raw).getOrThrow { IllegalArgumentException("Cannot restore with current registries: $it. Raw NBT preserved.") }.also {
        require(!it.isEmpty && it.count in 1..it.maxStackSize) { "Invalid stack count; raw NBT preserved" }
    }
    private fun digest(raw: CompoundTag): String {
        fun canonical(tag: Tag): String = when (tag) {
            is CompoundTag -> tag.allKeys.sorted().joinToString(prefix = "{", postfix = "}") { key -> StringTag.valueOf(key).toString() + ":" + canonical(requireNotNull(tag.get(key))) }
            is ListTag -> tag.joinToString(prefix = "[", postfix = "]") { canonical(it) }
            else -> tag.toString()
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical(raw).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun kits(): Map<String, CompoundTag> = section("Kits")
    fun archive(): Map<String, CompoundTag> = section("Archive")
    private fun section(key: String): Map<String, CompoundTag> = store().snapshot().getCompound(key).let { section -> section.allKeys.associateWith { section.getCompound(it).copy() } }
    fun pages(query: String = ""): List<Pair<Int, String>> = section("Pages").map { (key, tag) -> key.toInt() to tag.getString("Name") }.filter { query.isBlank() || it.second.contains(query, true) || it.first.toString() == query }.sortedBy { it.first }
    fun page(number: Int): CompoundTag { ItemVault.page(number); return section("Pages")[number.toString()] ?: CompoundTag().apply { putString("Name", "Page $number"); put("Items", ListTag()) } }
    fun items(container: CompoundTag): Map<Int, CompoundTag> {
        val result = linkedMapOf<Int, CompoundTag>()
        val entries = container.get("Items")
        require(entries == null || entries is ListTag && (entries.isEmpty() || entries.elementType.toInt() == 10)) { "Items must contain compounds" }
        for (entry in container.getList("Items", 10)) {
            require(entry is CompoundTag) { "Items must contain compounds" }
            require(entry.contains("Slot", 1)) { "Missing byte Slot" }
            val slot = entry.getByte("Slot").toInt() and 255
            require(slot in 0..53 && slot !in result) { "Duplicate or invalid Slot" }
            val raw = entry.copy().apply { remove("Slot") }
            require(raw.contains("id", 8) && raw.getInt("count") > 0) { "Expected 26.3 ItemStack id/count/components" }
            require(!raw.contains("components") || raw.get("components") is CompoundTag) { "Invalid components" }
            result[slot] = raw
        }
        return result
    }
    private fun container(name: String, stacks: Map<Int, CompoundTag>): CompoundTag = CompoundTag().apply {
        putString("Name", name)
        put("Items", ListTag().apply { stacks.toSortedMap().forEach { (slot, raw) -> add(raw.copy().apply { putByte("Slot", slot.toByte()) }) } })
    }
    fun renamePage(number: Int, name: String) {
        require(name.isNotBlank() && name.length <= 128) { "Page name must be 1..128 characters" }
        val page = page(number).apply { putString("Name", name.trim()) }
        store().mutate { it.getCompound("Pages").put(number.toString(), page) }
    }
    fun cursor(): CompoundTag? = (store().snapshot().get("Cursor") as? CompoundTag)?.copy()
    fun cursorSource(): Pair<Int, Int>? = (store().snapshot().get("CursorSource") as? CompoundTag)?.let {
        it.getInt("Page") to it.getInt("Slot")
    }?.takeIf { it.first > 0 && it.second in 0..53 }
    internal fun validatePageReplacement(previous: Map<Int, CompoundTag>, next: Map<Int, CompoundTag>, decodeStack: (CompoundTag) -> ItemStack) {
        require(next.size <= 54 && next.keys.all { it in 0..53 }) { "Page holds slots 0..53" }
        previous.forEach { (slot, raw) -> if (raw != next[slot]) decodeStack(raw) }
        next.forEach { (slot, raw) ->
            if (raw != previous[slot]) {
                val stack = decodeStack(raw)
                require(!stack.isEmpty && stack.count in 1..stack.maxStackSize) { "Invalid stack count" }
                ItemStack.validateComponents(stack.components).getOrThrow { IllegalArgumentException(it) }
            }
        }
    }
    fun localInventory(): Map<Int, CompoundTag> = items(store().snapshot().getCompound("LocalInventory"))
        fun atomicReplacePageItems(number: Int, stacks: Map<Int, CompoundTag>, cursor: CompoundTag?, source: Pair<Int, Int>?, localInventory: Map<Int, CompoundTag>? = null) {
        val previous = page(number)
        localInventory?.let {
            require(it.keys.all { slot -> slot in 0..35 }) { "Local inventory holds slots 0..35" }
            validatePageReplacement(this.localInventory(), it, ::decode)
        }
        val oldCursor = this.cursor()
        if (oldCursor != null && cursor != oldCursor) decode(oldCursor)
        validatePageReplacement(items(previous), stacks, ::decode)
        if (cursor != null && cursor != oldCursor) decode(cursor).also { ItemStack.validateComponents(it.components).getOrThrow { IllegalArgumentException(it) } }
        source?.let { require(it.first > 0 && it.second in 0..53) { "Invalid cursor source" } }
        val updated = previous.copy().apply { put("Items", container("", stacks).getList("Items", 10)) }
        store().mutatePage(number) { saved, root ->
            localInventory?.let { root.put("LocalInventory", container("Local inventory", it)) }
            if (updated != previous) saved.put("Items", updated.getList("Items", 10))
            if (cursor == null) { root.remove("Cursor"); root.remove("CursorSource") }
            else {
                root.put("Cursor", cursor.copy())
                if (source == null) root.remove("CursorSource") else root.put("CursorSource", CompoundTag().apply { putInt("Page", source.first); putInt("Slot", source.second) })
            }
        }
    }
    fun copyToPage(number: Int, slot: Int, stack: ItemStack) {
        require(slot in 0..53) { "Invalid slot" }
        val previous = page(number)
        val stacks = items(previous).toMutableMap()
        require(slot !in stacks) { "Slot occupied; remove saved copy first" }
        stacks[slot] = encode(stack.copy())
        store().mutate { it.getCompound("Pages").put(number.toString(), container(previous.getString("Name"), stacks)) }
    }
    fun copyArchivePage(number: Int, raw: List<CompoundTag>) {
        require(raw.size <= 54) { "Page holds 54 slots" }
        val previous = page(number)
        require(items(previous).isEmpty()) { "Destination page must be empty" }
        store().mutate { it.getCompound("Pages").put(number.toString(), container(previous.getString("Name"), raw.mapIndexed { index, tag -> index to tag.copy() }.toMap())) }
    }
    fun removeCopy(number: Int, slot: Int) {
        val previous = page(number)
        val stacks = items(previous).toMutableMap().apply { remove(slot) }
        store().mutate { it.getCompound("Pages").put(number.toString(), container(previous.getString("Name"), stacks)) }
    }
    fun saveKit(name: String): String {
        require(name.isNotBlank() && name.length <= 128) { "Kit name must be 1..128 characters" }
        val snapshot = captureKit(name.trim())
        val id = MessageDigest.getInstance("SHA-256").digest(name.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        check(id !in kits()) { "Kit name exists; original preserved" }
        store().mutate { it.getCompound("Kits").put(id, snapshot) }
        return id
    }
    fun deleteKit(id: String) {
        store().mutate {
            val kits = it.getCompound("Kits")
            check(kits.contains(id)) { "Kit no longer exists" }
            kits.remove(id)
        }
    }
    fun replaceKit(id: String) {
        val kit = requireNotNull(kits()[id]) { "Kit no longer exists" }
        val snapshot = captureKit(kit.getString("Name"))
        store().mutate {
            val kits = it.getCompound("Kits")
            check(kits.contains(id)) { "Kit no longer exists" }
            kits.put(id, snapshot)
        }
    }
    internal fun planKit(existing: List<ItemStack>, incoming: List<ItemStack>, limit: (ItemStack) -> Int): List<ItemStack> {
        val planned = existing.map { it.copy() }.toMutableList()
        incoming.forEach { stack -> creativePlan(planned, stack, stack.count, limit).forEach { (slot, result) -> planned[slot] = result } }
        return planned
    }
    fun give(raw: Collection<CompoundTag>) {
        check(client.isSameThread) { "Client thread required" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val gameMode = requireNotNull(client.gameMode)
        check(gameMode.playerMode == GameType.CREATIVE && player.abilities.instabuild) { "Creative mode required" }
        check(client.connection?.connection?.isConnected == true) { "Connection unavailable" }
        check(player.containerMenu === player.inventoryMenu) { "Close container first" }
        val inventory = player.inventory
        val original = (0 until 36).map { inventory.getItem(it).copy() }
        check(player.inventoryMenu.carried.isEmpty) { "Clear carried stack first" }
        val incoming = raw.map(::decode)
        incoming.forEach { require(it.isItemEnabled(requireNotNull(client.level).enabledFeatures())) { "Item disabled in this world" } }
        val planned = planKit(original, incoming) { inventory.getMaxStackSize(it) }
        planned.forEach { if (!it.isEmpty) ItemStack.validateComponents(it.components).getOrThrow { message -> IllegalArgumentException(message) } }
        check(client.player === player && player.containerMenu === player.inventoryMenu) { "Inventory changed" }
        planned.forEachIndexed { slot, stack ->
            if (!ItemStack.matches(stack, original[slot])) {
                inventory.setItem(slot, stack.copy())
                gameMode.handleCreativeModeItemAdd(stack.copy(), if (slot < 9) slot + 36 else slot)
            }
        }
        inventory.setChanged()
    }
    fun giveToEmptySlot(raw: CompoundTag, slot: Int) {
        check(client.isSameThread) { "Client thread required" }
        require(slot in 0..35) { "Invalid inventory slot" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val mode = requireNotNull(client.gameMode)
        val level = requireNotNull(client.level)
        check(mode.playerMode == GameType.CREATIVE && player.abilities.instabuild) { "Creative mode required" }
        check(client.connection?.connection?.isConnected == true) { "Connection unavailable" }
        check(player.containerMenu === player.inventoryMenu && player.inventoryMenu.carried.isEmpty) { "Close container and clear carried stack first" }
        val stack = decode(raw)
        val target = player.inventoryMenu.getSlot(kitMenuSlot(slot))
        require(target.mayPlace(stack) && stack.count <= target.getMaxStackSize(stack) && stack.isItemEnabled(level.enabledFeatures())) { "Item cannot occupy inventory slot" }
        ItemStack.validateComponents(stack.components).getOrThrow { IllegalArgumentException(it) }
        check(target.item.isEmpty) { "Occupied inventory slot; overwrite blocked" }
        mode.handleCreativeModeItemAdd(stack.copy(), kitMenuSlot(slot))
        target.set(stack.copy())
        player.inventory.setChanged()
    }
    internal fun kitMenuSlot(slot: Int): Int {
        require(slot in 0..40) { "Invalid kit slot" }
        return when (slot) { in 0..8 -> slot + 36; in 9..35 -> slot; in 36..39 -> 44 - slot; else -> 45 }
    }
    fun isFullKit(kit: CompoundTag) = kit.getInt("KitVersion") == 1 && kit.getInt("SavedSlots") == 41
    private fun captureKit(name: String): CompoundTag {
        val inventory = requireNotNull(client.player) { "Join a world first" }.inventory
        return container(name, (0..40).filter { !inventory.getItem(it).isEmpty }.associateWith { encode(inventory.getItem(it).copy()) }).apply {
            putInt("KitVersion", 1); putInt("SavedSlots", 41)
        }
    }
    internal fun kitRestorePlan(kit: CompoundTag, decodeStack: (CompoundTag) -> ItemStack, validate: (Int, ItemStack) -> Unit): Map<Int, ItemStack> {
        require(!kit.contains("KitVersion") || isFullKit(kit)) { "Unsupported kit snapshot version or slot count" }
        require(kit.get("Items") is ListTag) { "Kit Items must be a list" }
        val raw = items(kit)
        require(raw.keys.all { it in 0..40 }) { "Kit contains unsupported BODY/SADDLE or invalid slots" }
        val slots = if (isFullKit(kit)) (0..40).toList() else (0..35).toList() + raw.keys.filter { it in 36..40 }.sorted()
        return slots.associate { slot ->
            val stack = raw[slot]?.let(decodeStack) ?: ItemStack.EMPTY
            val menuSlot = kitMenuSlot(slot)
            if (!stack.isEmpty) {
                require(stack.count in 1..stack.maxStackSize) { "Invalid kit stack count" }
                ItemStack.validateComponents(stack.components).getOrThrow { IllegalArgumentException(it) }
                validate(menuSlot, stack)
            }
            menuSlot to stack.copy()
        }
    }
    fun lastKitBackup(): CompoundTag? = (store().snapshot().get("LastKitRestoreBackup") as? CompoundTag)?.copy()
    fun restoreLastKitBackup() = requestKitRestore(requireNotNull(lastKitBackup()) { "No rollback backup saved" })
    fun restoreKit(id: String) = requestKitRestore(requireNotNull(kits()[id]) { "Kit not found" })
    private fun requestKitRestore(kit: CompoundTag) {
        check(client.isSameThread) { "Client thread required" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val level = client.level
        val connection = client.connection
        val parent = client.screen
        val snapshot = kit.copy()
        val scope = if (isFullKit(snapshot)) "all 41 inventory, armor and offhand slots" else "36 main slots and any explicitly saved equipment; unsaved legacy armor/offhand stay unchanged"
        client.setScreen(ConfirmScreen({ accepted ->
            client.setScreen(parent)
            if (accepted) try {
                check(client.player === player && client.level === level && client.connection === connection) { "World changed; request restore again" }
                performKitRestore(snapshot)
            } catch (e: Exception) { notifySpotlight("Kit restore stopped: ${e.message}") }
        }, Component.literal("Overwrite inventory: ${snapshot.getString("Name")}?"),
            Component.literal("Replaces $scope, including empty slots. Current inventory is saved as last rollback backup before sending. Creative only. Server may reject some packets; this is not atomic."),
            Component.literal("Overwrite inventory"), Component.literal("Cancel")))
    }
    private fun performKitRestore(kit: CompoundTag) {
        check(client.isSameThread) { "Client thread required" }
        val player = requireNotNull(client.player) { "Join a world first" }
        val gameMode = requireNotNull(client.gameMode)
        val connection = client.connection
        val level = client.level
        val selected = player.inventory.selected
        fun preflight() {
            check(client.player === player && client.level === level && client.gameMode === gameMode && client.connection === connection && connection?.connection?.isConnected == true) { "World or connection changed" }
            check(gameMode.playerMode == GameType.CREATIVE && player.abilities.instabuild) { "Creative mode required" }
            check(player.containerMenu === player.inventoryMenu && player.inventoryMenu.carried.isEmpty) { "Close container and clear carried stack first" }
            check(player.inventory.selected == selected) { "Selected slot changed" }
        }
        preflight()
        val planned = kitRestorePlan(kit, ::decode) { slot, stack ->
            val target = player.inventoryMenu.getSlot(slot)
            require(target.mayPlace(stack) && stack.count <= target.getMaxStackSize(stack)) { "Item cannot occupy saved slot $slot" }
            require(stack.isItemEnabled(requireNotNull(level).enabledFeatures())) { "Item disabled in this world" }
        }
        preflight()
        val backup = captureKit("Before kit restore")
        store().mutate { it.put("LastKitRestoreBackup", backup) }
        preflight()
        try {
            planned.forEach { (slot, stack) ->
                gameMode.handleCreativeModeItemAdd(stack.copy(), slot)
                player.inventoryMenu.getSlot(slot).set(stack.copy())
            }
            player.inventory.setChanged()
        } catch (e: Exception) {
            throw IllegalStateException("Some slots may have changed; rollback backup saved. ${e.message}", e)
        }
        notifySpotlight("Kit replacement requested; server may reject individual slots. Rollback backup saved in Kits.")
    }
    fun kitCommands(): List<SpotlightCommand> = try {
        kits().map { (id, kit) -> SpotlightCommand("kit:$id", "Restore kit: ${kit.getString("Name")}", "Confirm slot replacement in Creative; saves rollback backup", unavailable = ::worldUnavailable) { restoreKit(id) } }
    } catch (e: Exception) { failure = "Vault unavailable: ${e.message}. Original preserved."; emptyList() }
    fun exportPage(number: Int, filename: String) = store().export(filename, page(number).apply { putString("id", "minecraft:chest") })
    fun importPage(number: Int, filename: String) {
        ItemVault.page(number)
        val imported = ItemVault.read(store().localFile(filename))
        require(imported.get("Items") is ListTag) { "Expected vanilla chest Items list; mod-specific formats not supported" }
        val stacks = items(imported)
        require(items(page(number)).isEmpty()) { "Import needs an empty page; original preserved" }
        store().mutate { it.getCompound("Pages").put(number.toString(), container(imported.getString("Name"), stacks)) }
    }
    fun history() = store().history()
    fun restoreRevision(name: String) { store().restoreRevision(name); previousObserved = emptyList() }
    fun tick() {
        if (archiveStopped || failure != null || client.player == null || client.connection == null) return
        try {
            val player = requireNotNull(client.player)
            val observed = (0 until player.inventory.containerSize).map { player.inventory.getItem(it) } + player.containerMenu.slots.map { it.item } + listOf(player.containerMenu.carried)
            if (observed.size == previousObserved.size && observed.indices.all { ItemStack.matches(observed[it], previousObserved[it]) }) return
            val saved = archive()
            val additions = linkedMapOf<String, CompoundTag>()
            observed.filter { !it.isEmpty && !it.componentsPatch.isEmpty }.forEach { stack ->
                val raw = encode(stack.copy())
                val id = digest(raw)
                if (id !in saved) additions[id] = raw
            }
            if (additions.isNotEmpty()) store().mutate { root -> appendArchive(root, additions) }
            previousObserved = observed.map { it.copy() }
        } catch (e: Exception) {
            archiveStopped = true
            notifySpotlight("Item archive stopped: ${e.message}. Originals preserved. Reopen archive to retry.")
        }
    }
    internal fun appendArchive(root: CompoundTag, additions: Map<String, CompoundTag>) {
        val archive = root.getCompound("Archive")
        val order = (root.get("ArchiveOrder") as? CompoundTag)?.copy() ?: CompoundTag()
        var sequence = maxOf(root.getLong("ArchiveSequence"), order.allKeys.maxOfOrNull { order.getLong(it) } ?: 0)
        additions.forEach { (id, raw) -> if (!archive.contains(id)) {
            sequence = Math.addExact(sequence, 1)
            archive.put(id, raw.copy()); order.putLong(id, sequence)
        } }
        root.put("ArchiveOrder", order); root.putLong("ArchiveSequence", sequence)
    }
    internal fun orderedArchive(root: CompoundTag): List<Pair<String, CompoundTag>> {
        val archive = root.getCompound("Archive")
        val order = root.getCompound("ArchiveOrder")
        return archive.allKeys.sortedWith(compareByDescending<String> { order.getLong(it) }.thenBy { it }).map { it to archive.getCompound(it).copy() }
    }
    fun archiveNewest() = orderedArchive(store().snapshot())
    fun resumeArchive() { archiveStopped = false; previousObserved = emptyList() }
}
