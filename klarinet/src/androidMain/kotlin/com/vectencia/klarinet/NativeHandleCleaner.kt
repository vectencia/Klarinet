package com.vectencia.klarinet

import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Frees a native analysis handle after its Kotlin owner is collected.
 * `java.lang.ref.Cleaner` is API 33 and minSdk is 24.
 */
internal object NativeHandleCleaner {
    private val queue = ReferenceQueue<Any>()
    private val refs = Collections.synchronizedSet(mutableSetOf<Cleanup>())
    private val started = AtomicBoolean(false)

    fun register(referent: Any, handle: Long, destroy: (Long) -> Unit) {
        refs.add(Cleanup(referent, handle, destroy, queue))
        if (started.compareAndSet(false, true)) {
            CleanerThread.start()
        }
    }

    private object CleanerThread : Thread("klarinet-native-cleaner") {
        init {
            isDaemon = true
        }

        override fun run() {
            while (true) {
                val ref = try {
                    queue.remove() as Cleanup
                } catch (_: InterruptedException) {
                    continue
                }
                refs.remove(ref)
                ref.destroy(ref.handle)
                ref.clear()
            }
        }
    }

    private class Cleanup(
        referent: Any,
        val handle: Long,
        val destroy: (Long) -> Unit,
        queue: ReferenceQueue<Any>,
    ) : PhantomReference<Any>(referent, queue)
}
