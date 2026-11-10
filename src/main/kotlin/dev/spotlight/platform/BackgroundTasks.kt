package dev.spotlight.platform

import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

internal object BackgroundTasks {
    data class Task(val id: Long, val title: String, val detail: String)
    private data class Job(var task: Task, val world: Any, val tick: () -> Boolean, val cancel: () -> Unit)
    private val jobs = linkedMapOf<Long, Job>()
    private var nextId = 0L
    private val logger = LoggerFactory.getLogger("spotlight.tasks")

    fun register(title: String, detail: String, tick: () -> Boolean, cancel: () -> Unit = {}): Long {
        val world = requireNotNull(Minecraft.getInstance().level) { "Join a world first" }
        val id = ++nextId
        jobs[id] = Job(Task(id, title, detail), world, tick, cancel)
        return id
    }

    fun snapshot(): List<Task> = jobs.values.map { it.task }
    fun update(id: Long, detail: String) { jobs[id]?.let { it.task = it.task.copy(detail = detail) } }
    fun stop(id: Long) {
        val job = jobs.remove(id) ?: return
        try { job.cancel() } catch (error: Exception) { logger.error("Cannot stop Spotlight task ${job.task.title}", error) }
    }
    fun stopAll() { jobs.keys.toList().forEach(::stop) }
    fun tick() {
        val world = Minecraft.getInstance().level
        jobs.keys.toList().forEach { id ->
            val job = jobs[id] ?: return@forEach
            if (world !== job.world) { stop(id); return@forEach }
            try {
                if (job.tick()) stop(id)
            } catch (error: Exception) {
                logger.error("Spotlight task ${job.task.title} failed", error)
                notifySpotlight("${job.task.title} stopped: ${error.message ?: error.javaClass.simpleName}")
                stop(id)
            }
        }
    }
}
