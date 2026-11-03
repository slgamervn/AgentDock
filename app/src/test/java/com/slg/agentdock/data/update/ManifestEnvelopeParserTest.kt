package com.slg.agentdock.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestEnvelopeParserTest {
    @Test
    fun parsesValidEnvelope() {
        val envelope = ManifestEnvelopeParser.parse(validJson())

        assertEquals(1, envelope.schemaVersion)
        assertEquals("agentdock-update-2026", envelope.keyId)
        assertEquals("ECDSA_P256_SHA256_DER", envelope.algorithm)
        assertEquals("cGF5bG9hZA", envelope.payload)
        assertEquals("c2ln", envelope.signature)
    }

    @Test
    fun rejectsDuplicateKeys() {
        assertRejected(validJson().replace(
            "\"keyId\":\"agentdock-update-2026\"",
            "\"keyId\":\"agentdock-update-2026\",\"keyId\":\"other\"",
        ))
    }

    @Test
    fun rejectsUnknownField() {
        assertRejected(validJson().replace("\"signature\":\"c2ln\"", "\"signature\":\"c2ln\",\"extra\":true"))
    }

    @Test
    fun rejectsMissingField() {
        assertRejected(validJson().replace(",\"signature\":\"c2ln\"", ""))
    }

    @Test
    fun rejectsWrongType() {
        assertRejected(validJson().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""))
    }

    @Test
    fun rejectsFractionalSchemaVersion() {
        assertRejected(validJson().replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"))
    }

    @Test
    fun rejectsTrailingContent() {
        assertRejected(validJson() + "{}")
    }

    @Test
    fun rejectsNonObjectEnvelope() {
        assertRejected("[]")
    }

    private fun assertRejected(json: String) {
        try {
            ManifestEnvelopeParser.parse(json)
            throw AssertionError("Expected malformed manifest envelope to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun validJson(): String = """
        {"schemaVersion":1,"keyId":"agentdock-update-2026","algorithm":"ECDSA_P256_SHA256_DER","payload":"cGF5bG9hZA","signature":"c2ln"}
    """.trimIndent()
}
