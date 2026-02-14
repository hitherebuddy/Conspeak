package com.conspeak.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CryptoUtilsTest {

    @Test
    fun `pairing code is 6 digits`() {
        val code = CryptoUtils.derivePairingCode("abc123", "def456")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `pairing code is deterministic`() {
        val code1 = CryptoUtils.derivePairingCode("abc123", "def456")
        val code2 = CryptoUtils.derivePairingCode("abc123", "def456")
        assertEquals(code1, code2)
    }

    @Test
    fun `pairing code is order-independent`() {
        val code1 = CryptoUtils.derivePairingCode("abc123", "def456")
        val code2 = CryptoUtils.derivePairingCode("def456", "abc123")
        assertEquals(code1, code2, "Code should be the same regardless of fingerprint order")
    }

    @Test
    fun `different fingerprints produce different codes`() {
        val code1 = CryptoUtils.derivePairingCode("abc123", "def456")
        val code2 = CryptoUtils.derivePairingCode("xxx999", "yyy000")
        assertNotEquals(code1, code2)
    }

    @Test
    fun `self-signed SSL context can be created`() {
        val (context, fingerprint) = CryptoUtils.createSelfSignedSslContext()
        assertTrue(fingerprint.isNotEmpty())
        assertTrue(fingerprint.length == 64) // SHA-256 hex = 64 chars
        // Context should be usable
        val factory = context.socketFactory
        assertTrue(factory != null)
    }
}
