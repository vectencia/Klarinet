package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteChangeObserverTest {

    @Test
    fun twoObserveClearCyclesDoNotLeaveObserverInstalled() {
        val session = AudioSessionManager()
        repeat(2) {
            session.observeRouteChanges { }
            assertTrue(isRouteChangeObserverInstalled())
            session.clearRouteChanges()
            assertFalse(isRouteChangeObserverInstalled())
        }
    }
}
