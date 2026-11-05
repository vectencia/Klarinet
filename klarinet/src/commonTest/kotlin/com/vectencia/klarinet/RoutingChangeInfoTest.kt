package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingChangeInfoTest {
    @Test
    fun emptyDeviceListIsUnspecified() {
        assertEquals("unspecified", formatRoutedDeviceIds(intArrayOf()))
    }

    @Test
    fun deviceIdsStayInOrder() {
        assertEquals("4,9", formatRoutedDeviceIds(intArrayOf(4, 9)))
    }

    @Test
    fun routingChangeUsesPreviousDeviceIds() {
        val info = routingChangedInfo(intArrayOf(3))
        assertEquals(ROUTING_CHANGED_REASON, info.reason)
        assertEquals("3", info.previousRoute)
    }

    @Test
    fun routingChangeWithoutAPreviousDeviceIsUnspecified() {
        val info = routingChangedInfo(intArrayOf())
        assertEquals("RoutingChanged", info.reason)
        assertEquals("unspecified", info.previousRoute)
    }
}
