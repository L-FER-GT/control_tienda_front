package com.lfergt.controltienda.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `lee el tag con o sin v`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("v1.2.3"))
        assertEquals(AppVersion(10, 0, 0), AppVersion.parse(" 10.0.0 "))
    }

    @Test
    fun `rechaza textos que no son una versión publicada`() {
        listOf("1.2", "1.2.3-debug", "v1.100.0", "latest", "").forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test
    fun `el versionCode crece con cada versión`() {
        assertEquals(10_203, AppVersion(1, 2, 3).code)
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.99")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
    }
}
