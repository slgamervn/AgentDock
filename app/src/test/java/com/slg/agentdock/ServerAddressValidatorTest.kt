package com.slg.agentdock

import com.slg.agentdock.domain.validation.normalizeServerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressValidatorTest {
    @Test
    fun normalizesTrailingSlash() {
        assertEquals("https://agentdock.example.com/api", normalizeServerAddress(" https://agentdock.example.com/api/ "))
    }

    @Test
    fun acceptsLocalDevelopmentAddress() {
        assertEquals("http://127.0.0.1:8000", normalizeServerAddress("http://127.0.0.1:8000/"))
    }

    @Test
    fun allowsClearingAddress() {
        assertEquals("", normalizeServerAddress("   "))
    }

    @Test
    fun rejectsCredentialsAndUnsupportedSchemes() {
        assertNull(normalizeServerAddress("https://user:password@example.com"))
        assertNull(normalizeServerAddress("ftp://agentdock.example.com"))
    }

    @Test
    fun rejectsQueryAndFragment() {
        assertNull(normalizeServerAddress("https://agentdock.example.com?token=abc"))
        assertNull(normalizeServerAddress("https://agentdock.example.com#fragment"))
    }
}
