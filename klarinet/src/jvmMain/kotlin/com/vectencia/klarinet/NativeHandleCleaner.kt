package com.vectencia.klarinet

import java.lang.ref.Cleaner

internal object NativeHandleCleaner {
    private val cleaner: Cleaner = Cleaner.create()

    fun register(referent: Any, handle: Long, destroy: (Long) -> Unit) {
        cleaner.register(referent, Cleanup(handle, destroy))
    }

    private class Cleanup(
        private val handle: Long,
        private val destroy: (Long) -> Unit,
    ) : Runnable {
        override fun run() {
            destroy(handle)
        }
    }
}
