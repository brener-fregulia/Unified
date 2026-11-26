package unified.optimizations.fastquit

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * FastQuit's pending world shutdowns (fastquit-v1.md §6). When the player leaves a singleplayer world, vanilla has
 * already asked the integrated server to stop (`halt(false)`); FastQuit only skips the client's wait for the server
 * thread to end. The server thread still runs its whole vanilla shutdown on its own: saving players and chunks, mod
 * shutdown hooks, closing the world and releasing `session.lock`.
 *
 * Safety rules:
 * - **Bounded:** at most one pending shutdown. Any world storage access (open, create, edit, delete, back up) and
 *   leaving another world first wait for it, so two integrated servers never run at once.
 * - **Exit:** closing the game waits for it before Minecraft releases its resources and `System.exit`; a JVM shutdown
 *   hook joins the server thread as a backstop for other exit paths.
 * - **Completion** is the end of the server thread, the same condition as vanilla's own wait (`isShutdown`).
 * - **Errors** are not hidden: a server crash is relayed to the client crash screen by vanilla; completion and
 *   failures are logged with the feature id.
 */
object PendingSaves {
    private val logger = LoggerFactory.getLogger("unified_optimizations")
    private val TOAST = SystemToast.SystemToastId()

    private class Pending(val thread: Thread, val levelName: String, val started: Long)

    /** Shutdowns tracked since start; for tests. */
    @JvmStatic
    @Volatile
    var trackedCount: Int = 0
        private set

    /**
     * Tracks [thread] (a server thread already asked to stop). At most one is pending: an older one is awaited first.
     * Returns false if the client must wait itself (never in practice: kept for a future bound check).
     */
    @JvmStatic
    fun begin(minecraft: Minecraft?, thread: Thread, levelName: String): Boolean {
        if (hasPending()) {
            awaitAll(minecraft) // bounded to one: finish the older one first, then track this one
        }
        installShutdownHook()
        pending = Pending(thread, levelName, System.nanoTime())
        trackedCount++
        logger.info("[fast_quit] {} is saving in the background", levelName)
        return true
    }

    @Volatile
    private var pending: Pending? = null
    private var hookInstalled = false

    /** Whether a world is still finishing its shutdown in the background. */
    @JvmStatic
    fun hasPending(): Boolean = pending?.thread?.isAlive == true

    /** The world name being saved, or null. */
    @JvmStatic
    fun pendingLevel(): String? = pending?.takeIf { it.thread.isAlive }?.levelName

    /**
     * Called instead of vanilla's wait after `halt(false)`. Returns true when the client may continue at once (the
     * shutdown is tracked), false when the client must wait as vanilla does.
     */
    @JvmStatic
    fun track(minecraft: Minecraft, server: MinecraftServer): Boolean {
        val thread = server.runningThread
        if (thread == null || !thread.isAlive) return true // already finished: nothing to wait for
        if (!begin(minecraft, thread, server.worldData.levelName)) return false
        SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, Component.translatable(SAVING_TITLE),
            Component.literal(server.worldData.levelName))
        return true
    }

    /** Client tick: reports a finished shutdown once. */
    @JvmStatic
    fun tick(minecraft: Minecraft) {
        val finished = finish() ?: return
        SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, Component.translatable(SAVED_TITLE),
            Component.literal(finished))
    }

    /** Clears a finished shutdown and returns its world name, or null when none finished. */
    private fun finish(): String? {
        val current = pending ?: return null
        if (current.thread.isAlive) return null
        pending = null
        val ms = (System.nanoTime() - current.started) / 1_000_000
        logger.info("[fast_quit] {} finished saving in the background ({} ms)", current.levelName, ms)
        return current.levelName
    }

    /**
     * Waits until the pending shutdown has finished. On the render thread it shows vanilla's "Saving world" screen and
     * keeps rendering, exactly like vanilla's own wait; on any other thread it joins the server thread.
     */
    @JvmStatic
    fun awaitAll(minecraft: Minecraft?) {
        val current = pending ?: return
        if (Thread.currentThread() === current.thread) return // the saving server itself: never wait on ourselves
        if (!current.thread.isAlive) {
            tickSafely(minecraft)
            return
        }
        logger.info("[fast_quit] waiting for {} to finish saving", current.levelName)
        if (minecraft != null && minecraft.isSameThread && Rendering.available()) {
            val previous: Screen? = minecraft.gui.screen()
            minecraft.gui.setScreen(GenericMessageScreen(Component.translatable(WAIT_TITLE)))
            while (current.thread.isAlive) {
                Rendering.frame(minecraft)
            }
            minecraft.gui.setScreen(previous)
        } else {
            joinQuietly(current.thread)
        }
        tickSafely(minecraft)
    }

    private fun tickSafely(minecraft: Minecraft?) {
        if (minecraft != null) tick(minecraft) else finish()
    }

    private fun joinQuietly(thread: Thread) {
        var interrupted = false
        while (thread.isAlive) {
            try {
                thread.join()
            } catch (e: InterruptedException) {
                interrupted = true // keep waiting: a world must never be left half-saved
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    @Synchronized
    private fun installShutdownHook() {
        if (hookInstalled) return
        hookInstalled = true
        Runtime.getRuntime().addShutdownHook(Thread({
            val current = pending
            if (current != null && current.thread.isAlive) {
                logger.warn("[fast_quit] exiting: waiting for {} to finish saving", current.levelName)
                joinQuietly(current.thread)
            }
        }, "Unified Optimizations FastQuit shutdown wait"))
    }

    /** How the render-thread wait draws frames; set by the Mixin accessor, so this object stays testable. */
    object Rendering {
        @JvmStatic
        var renderer: ((Minecraft) -> Unit)? = null

        fun available(): Boolean = renderer != null

        fun frame(minecraft: Minecraft) = renderer!!.invoke(minecraft)
    }

    const val SAVING_TITLE: String = "unified_optimizations.fast_quit.saving"
    const val SAVED_TITLE: String = "unified_optimizations.fast_quit.saved"
    const val WAIT_TITLE: String = "unified_optimizations.fast_quit.waiting"
}
