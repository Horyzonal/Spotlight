package dev.spotlight.platform

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

internal fun parseExtendedFov(text: String): Int = requireNotNull(text.toIntOrNull()) {
    "FOV must be an integer between -2147483648 and 2147483647"
}

internal fun parseExtendedGamma(text: String): Double {
    val decimal = text.toBigDecimalOrNull()
    require(decimal != null && decimal >= BigDecimal.valueOf(Int.MIN_VALUE.toLong()) &&
        decimal <= BigDecimal.valueOf(Int.MAX_VALUE.toLong())) {
        "Gamma must be a finite decimal between -2147483648 and 2147483647"
    }
    return decimal.toDouble()
}

internal class ExtendedOptionsStore(private val path: Path) {
    var gamma: Double? = null
        private set
    var fov: Int? = null
        private set

    init {
        if (Files.exists(path)) {
            val properties = Properties().apply { Files.newBufferedReader(path).use { load(it) } }
            val loadedGamma = properties.getProperty("gamma")?.let(::parseExtendedGamma)
            val loadedFov = properties.getProperty("fov")?.let(::parseExtendedFov)
            gamma = loadedGamma
            fov = loadedFov
        }
    }

    fun save(gamma: Double?, fov: Int?) {
        gamma?.let { parseExtendedGamma(it.toString()) }
        fov?.let { parseExtendedFov(it.toString()) }
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, "spotlight-options-", ".tmp")
        try {
            val properties = Properties().apply {
                gamma?.let { setProperty("gamma", it.toString()) }
                fov?.let { setProperty("fov", it.toString()) }
            }
            Files.newBufferedWriter(temporary).use { properties.store(it, "Spotlight extended options") }
            
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
            this.gamma = gamma
            this.fov = fov
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

object ExtendedOptions {
    private var store: ExtendedOptionsStore? = null
    private var loadFailure: Exception? = null
    private var initialized = false
    private val vanillaRead = ThreadLocal.withInitial { false }

    @JvmStatic
    fun vanillaValue(option: OptionInstance<*>): Any? {
        val previous = vanillaRead.get()
        vanillaRead.set(true)
        return try { option.get() } finally { vanillaRead.set(previous) }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        try {
            store = ExtendedOptionsStore(FabricLoader.getInstance().configDir.resolve("spotlight-extended-options.properties"))
        } catch (failure: Exception) {
            loadFailure = failure
            LoggerFactory.getLogger("spotlight").error("Cannot load extended options; original preserved", failure)
            var reported = false
            ClientTickEvents.END_CLIENT_TICK.register {
                if (!reported) {
                    reported = true
                    notifySpotlight("Cannot load extended options: ${failure.message}. Original preserved; overrides disabled.")
                }
            }
        }
    }

    private fun settings(): ExtendedOptionsStore {
        initialize()
        return store ?: error("Cannot load extended options: ${loadFailure?.message}. Original preserved.")
    }

    fun setGamma(text: String) {
        val value = parseExtendedGamma(text)
        val settings = settings()
        try { settings.save(value, settings.fov) } catch (failure: Exception) {
            error("Cannot save gamma: ${failure.message}. Previous value preserved.")
        }
    }

    fun setFov(text: String) {
        val value = parseExtendedFov(text)
        val settings = settings()
        try { settings.save(settings.gamma, value) } catch (failure: Exception) {
            error("Cannot save FOV: ${failure.message}. Previous value preserved.")
        }
    }

    @JvmStatic
    fun gammaActive(): Boolean = store?.gamma != null

    @JvmStatic
    fun value(option: OptionInstance<*>): Any? {
        if (vanillaRead.get()) return null
        val settings = store ?: return null
        if (settings.gamma == null && settings.fov == null) return null
        val options = Minecraft.getInstance()?.options ?: return null
        return when {
            option === options.gamma() -> settings.gamma
            option === options.fov() -> settings.fov
            else -> null
        }
    }

    @JvmStatic
    fun clear(option: OptionInstance<*>) {
        val settings = store ?: return
        val options = Minecraft.getInstance()?.options ?: return
        val gamma = option === options.gamma() && settings.gamma != null
        val fov = option === options.fov() && settings.fov != null
        if (!gamma && !fov) return
        try {
            settings.save(if (gamma) null else settings.gamma, if (fov) null else settings.fov)
        } catch (failure: Exception) {
            notifySpotlight("Cannot reset extended option: ${failure.message}. Previous override preserved.")
        }
    }
}
