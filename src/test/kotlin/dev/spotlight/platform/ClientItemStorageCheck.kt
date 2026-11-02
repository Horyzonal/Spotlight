package dev.spotlight.platform

import dev.spotlight.core.storage.ItemVault
import net.minecraft.nbt.*
import java.nio.file.Files


object ClientItemStorageCheck {
    @JvmStatic fun main(args: Array<String>) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val registries = net.minecraft.data.registries.VanillaRegistries.createLookup()
        dev.spotlight.ui.StorageUICheck.main(emptyArray())
        val ops = registries.createSerializationContext(NbtOps.INSTANCE)
        val stack = net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD)
        stack.damageValue = 7
        val encoded = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, stack).getOrThrow() as CompoundTag
        check(encoded.getCompound("components").contains("minecraft:damage"))
        check(net.minecraft.world.item.ItemStack.matches(stack, net.minecraft.world.item.ItemStack.CODEC.parse(ops, encoded).getOrThrow()))
        check(!stack.componentsPatch.isEmpty)
        fun rawStack(item: net.minecraft.world.item.Item, count: Int = 1) = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, net.minecraft.world.item.ItemStack(item, count)).getOrThrow() as CompoundTag
        fun kit(stacks: Map<Int, CompoundTag> = emptyMap(), fullSnapshot: Boolean = true) = CompoundTag().apply {
            if (fullSnapshot) { putInt("KitVersion", 1); putInt("SavedSlots", 41) }
            put("Items", ListTag().apply { stacks.forEach { (slot, raw) -> add(raw.copy().apply { putByte("Slot", slot.toByte()) }) } })
        }
        val readStack: (CompoundTag) -> net.minecraft.world.item.ItemStack = { net.minecraft.world.item.ItemStack.CODEC.parse(ops, it).getOrThrow() }

        dev.spotlight.ui.ClientChestMenuCheck.run({ net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, it).getOrThrow() as CompoundTag }, readStack)
                dev.spotlight.ui.VaultInteractionCheck.run({ net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, it).getOrThrow() as CompoundTag }, readStack)

        val snapshot = kit(mapOf(
            0 to encoded, 9 to rawStack(net.minecraft.world.item.Items.ENDER_PEARL, 16),
            36 to rawStack(net.minecraft.world.item.Items.DIAMOND_BOOTS), 37 to rawStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS),
            38 to rawStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE), 39 to rawStack(net.minecraft.world.item.Items.DIAMOND_HELMET),
            40 to rawStack(net.minecraft.world.item.Items.SHIELD)
        ))
        val replacement = ClientItemStorage.kitRestorePlan(snapshot, readStack) { _, _ -> }
        check(replacement.keys == (5..45).toSet())
        check(net.minecraft.world.item.ItemStack.matches(replacement.getValue(36), stack))
        check(replacement.getValue(9).count == 16)
        check(replacement.getValue(5).`is`(net.minecraft.world.item.Items.DIAMOND_HELMET))
        check(replacement.getValue(6).`is`(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE))
        check(replacement.getValue(7).`is`(net.minecraft.world.item.Items.DIAMOND_LEGGINGS))
        check(replacement.getValue(8).`is`(net.minecraft.world.item.Items.DIAMOND_BOOTS))
        check(replacement.getValue(45).`is`(net.minecraft.world.item.Items.SHIELD))
        check(replacement.getValue(37).isEmpty && replacement.getValue(35).isEmpty)
        check(ClientItemStorage.kitRestorePlan(kit(), readStack) { _, _ -> }.values.all { it.isEmpty })
        val legacy = ClientItemStorage.kitRestorePlan(kit(fullSnapshot = false), readStack) { _, _ -> }
        check(legacy.keys == (9..44).toSet())
        check(runCatching { ClientItemStorage.kitMenuSlot(41) }.isFailure)
        check(runCatching { ClientItemStorage.kitRestorePlan(kit(mapOf(41 to encoded)), readStack) { _, _ -> } }.isFailure)
        val legacyEquipment = ClientItemStorage.kitRestorePlan(kit(mapOf(40 to encoded), false), readStack) { _, _ -> }
        check(legacyEquipment.size == 37 && net.minecraft.world.item.ItemStack.matches(legacyEquipment.getValue(45), stack))
        check(5 !in legacyEquipment && 8 !in legacyEquipment)
        check(ClientItemStorage.kitMenuSlot(39) == 5)
        check(ClientItemStorage.kitMenuSlot(36) == 8)
        check(ClientItemStorage.kitMenuSlot(40) == 45)
        check(runCatching { ClientItemStorage.kitRestorePlan(snapshot.copy().apply { putInt("KitVersion", 2) }, readStack) { _, _ -> } }.isFailure)
        check(runCatching { ClientItemStorage.kitRestorePlan(CompoundTag(), readStack) { _, _ -> } }.isFailure)
        var sent = 0
        fun preflightThenSend(raw: CompoundTag, validate: (Int, net.minecraft.world.item.ItemStack) -> Unit = { _, _ -> }) {
            val plan = ClientItemStorage.kitRestorePlan(raw, readStack, validate)
            plan.forEach { _ -> sent++ }
        }
        check(runCatching { preflightThenSend(kit(mapOf(35 to CompoundTag().apply { putString("id", "missing_mod:item"); putInt("count", 1) }))) }.isFailure)
        check(runCatching { preflightThenSend(kit(mapOf(35 to rawStack(net.minecraft.world.item.Items.ENDER_PEARL).apply { putInt("count", 17) }))) }.isFailure)
        check(runCatching { preflightThenSend(snapshot) { slot, _ -> require(slot != 45) { "Slot rejected" } } }.isFailure)
        val malformed = snapshot.copy().apply { put("Items", net.minecraft.nbt.ListTag().apply { add(StringTag.valueOf("bad")) }) }
        check(runCatching { preflightThenSend(malformed) }.isFailure)
        check(sent == 0)
        val full = List(36) { net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64) }
        val almostFull = full.map { it.copy() }.toMutableList().apply { this[35] = net.minecraft.world.item.ItemStack.EMPTY }
        val incoming = listOf(net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 64), stack)
        check(runCatching { ClientItemStorage.planKit(almostFull, incoming) { 64 } }.isFailure)
        check(almostFull[35].isEmpty)
        val room = almostFull.toMutableList().apply { this[34] = net.minecraft.world.item.ItemStack.EMPTY }
        val planned = ClientItemStorage.planKit(room, incoming) { 64 }
        check(!planned[34].isEmpty && !planned[35].isEmpty)
        check(room[34].isEmpty && room[35].isEmpty)
        val directory = Files.createTempDirectory("spotlight-vault-check")
        try {
            val vault = ItemVault(directory)
            check(vault.revision() == 0L)
            val max = ItemVault.page(Int.MAX_VALUE)
            check(runCatching { ItemVault.page(0) }.isFailure)
            vault.mutate { it.getCompound("Pages").put(max.toString(), CompoundTag().apply { putString("Name", "Far page"); put("Items", ListTag()) }) }
            check(vault.snapshot().getCompound("Pages").size() == 1)
            check(ItemVault(directory).revision() == 1L)
            val firstRevision = vault.history().single()
            vault.mutate { it.put("LastKitRestoreBackup", snapshot) }
            check(ItemVault(directory).snapshot().getCompound("LastKitRestoreBackup") == snapshot)
            check(vault.snapshot().getCompound("Kits").isEmpty)
            val before = vault.snapshot()
            check(runCatching { vault.mutate { it.putString("Broken", "draft"); error("Edit rejected") } }.isFailure)
            check(vault.snapshot() == before)
            vault.export("chest.nbt", CompoundTag().apply { put("Items", ListTag()) })
            val originalExport = Files.readAllBytes(vault.localFile("chest.nbt"))
            check(runCatching { vault.export("chest.nbt", CompoundTag()) }.isFailure)
            check(Files.readAllBytes(vault.localFile("chest.nbt")).contentEquals(originalExport))
            check(runCatching { vault.localFile("../escape.nbt") }.isFailure)
            check(runCatching { vault.localFile("/tmp/escape.nbt") }.isFailure)
            val link = vault.localFile("link.nbt")
            Files.createSymbolicLink(link, vault.localFile("chest.nbt"))
            check(runCatching { ItemVault.read(link) }.isFailure)
            val root = directory.resolve("vault.nbt")
            val originalRoot = Files.readAllBytes(root)
            Files.delete(root)
            Files.createSymbolicLink(root, vault.localFile("chest.nbt"))
            check(runCatching { vault.mutate { it.putString("Attempt", "blocked") } }.isFailure)
            check(vault.snapshot() == before)
            check(Files.readAllBytes(vault.localFile("chest.nbt")).contentEquals(originalExport))
            Files.delete(root)
            Files.write(root, originalRoot)
            repeat(103) { index -> vault.mutate { it.putInt("Counter", index) } }
            check(vault.history().size == 100)
            check(ItemVault(directory).revision() == 105L)
            check(firstRevision !in vault.history())
            val restorePoint = vault.history().last()
            vault.mutate { it.getCompound("Pages").put("1", CompoundTag().apply { putString("Name", "Temporary") }) }
            vault.restoreRevision(restorePoint)
            check(vault.revision() == 107L)
            check(!vault.snapshot().getCompound("Pages").contains("1"))
            val unknown = CompoundTag().apply { putByte("Slot", 53); putString("id", "missing_mod:item"); putInt("count", 3); put("components", CompoundTag().apply { putString("missing_mod:component", "opaque") }) }
            val container = CompoundTag().apply { put("Items", ListTag().apply { add(unknown) }) }
            val raw = ClientItemStorage.items(container).getValue(53)
            check(raw.getString("id") == "missing_mod:item")
            check(raw.getCompound("components").getString("missing_mod:component") == "opaque")
            check(!raw.contains("Slot"))
            val duplicate = container.copy().apply { getList("Items", 10).add(unknown.copy()) }
            check(runCatching { ClientItemStorage.items(duplicate) }.isFailure)
            val blocker = directory.resolve("cap-test.bin")
            java.io.RandomAccessFile(blocker.toFile(), "rw").use { it.setLength(ItemVault.DISK_CAP) }
            val beforeCap = vault.snapshot()
            check(runCatching { vault.mutate { it.putString("Cap", "rejected") } }.isFailure)
            check(vault.snapshot() == beforeCap)
            check(ItemVault(directory).snapshot() == beforeCap)
            Files.delete(blocker)
            Files.write(root, byteArrayOf(1, 2, 3))
            check(runCatching { ItemVault(directory) }.isFailure)
            check(Files.readAllBytes(root).contentEquals(byteArrayOf(1, 2, 3)))
            println("ClientItemStorageCheck passed: 41-slot replacement, armor/offhand mapping, empty clears, legacy scope, preflight failures without sends, persistent rollback, vault isolation, paths/revisions/corrupt originals")
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
