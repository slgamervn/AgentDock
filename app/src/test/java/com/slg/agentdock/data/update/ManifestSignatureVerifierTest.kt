package com.slg.agentdock.data.update

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ManifestSignatureVerifierTest {
    private lateinit var keyPair: KeyPair
    private lateinit var verifier: ManifestSignatureVerifier
    private val codec = JvmBase64UrlCodec()

    @Before
    fun setUp() {
        keyPair = generateEcKeyPair("secp256r1")
        verifier = ManifestSignatureVerifier(
            trustedKeys = mapOf(TEST_KEY_ID to keyPair.public),
            base64UrlCodec = codec,
        )
    }

    @Test
    fun verifiesPayloadAndReturnsOriginalBytes() {
        val payload = """{"schemaVersion":1,"channel":"stable","versionCode":5}"""
            .toByteArray(StandardCharsets.UTF_8)

        val verified = verifier.verify(signedEnvelope(payload))

        assertArrayEquals(payload, verified)
        assertEquals(payload.size, verified.size)
    }

    @Test
    fun rejectsPayloadModifiedAfterSigning() {
        val original = """{"schemaVersion":1,"channel":"stable"}"""
            .toByteArray(StandardCharsets.UTF_8)
        val envelope = signedEnvelope(original)
        val changedPayload = """{"schemaVersion":1,"channel":"preview"}"""
            .toByteArray(StandardCharsets.UTF_8)

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(envelope.copy(payload = codec.encode(changedPayload)))
        }
    }

    @Test
    fun rejectsUnknownKeyId() {
        val envelope = signedEnvelope("{}".toByteArray()).copy(keyId = "unknown-key")

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(envelope)
        }
    }

    @Test
    fun rejectsUnsupportedAlgorithm() {
        val envelope = signedEnvelope("{}".toByteArray()).copy(algorithm = "SHA1withECDSA")

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(envelope)
        }
    }

    @Test
    fun rejectsUnsupportedEnvelopeSchema() {
        val envelope = signedEnvelope("{}".toByteArray()).copy(schemaVersion = 2)

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(envelope)
        }
    }

    @Test
    fun rejectsPaddedBase64Url() {
        val envelope = signedEnvelope("{}".toByteArray())
        val padded = envelope.copy(payload = envelope.payload + "=")

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(padded)
        }
    }

    @Test
    fun rejectsOversizedPayloadBeforeDecoding() {
        val oversized = SignedManifestEnvelope(
            schemaVersion = ManifestSignatureVerifier.SCHEMA_VERSION,
            keyId = TEST_KEY_ID,
            algorithm = ManifestSignatureVerifier.ALGORITHM,
            payload = "A".repeat(MAX_PAYLOAD_BASE64_CHARS + 1),
            signature = codec.encode(ByteArray(70)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(oversized)
        }
    }

    @Test
    fun rejectsSignatureFromAnotherPublicKey() {
        val otherKey = generateEcKeyPair("secp256r1")
        val otherVerifier = ManifestSignatureVerifier(
            trustedKeys = mapOf(TEST_KEY_ID to otherKey.public),
            base64UrlCodec = codec,
        )

        assertThrows(IllegalArgumentException::class.java) {
            otherVerifier.verify(signedEnvelope("{}".toByteArray()))
        }
    }

    @Test
    fun rejectsNonP256EcPublicKey() {
        val p384 = generateEcKeyPair("secp384r1")
        val envelope = signedEnvelope("{}".toByteArray())
        val verifierWithWrongCurve = ManifestSignatureVerifier(
            trustedKeys = mapOf(TEST_KEY_ID to p384.public),
            base64UrlCodec = codec,
        )

        assertThrows(IllegalArgumentException::class.java) {
            verifierWithWrongCurve.verify(envelope)
        }
    }

    @Test
    fun rejectsRsaPublicKey() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()
        val envelope = signedEnvelope("{}".toByteArray())
        val verifierWithRsa = ManifestSignatureVerifier(
            trustedKeys = mapOf(TEST_KEY_ID to rsa.public),
            base64UrlCodec = codec,
        )

        assertThrows(IllegalArgumentException::class.java) {
            verifierWithRsa.verify(envelope)
        }
    }

    @Test
    fun rejectsMalformedBase64Url() {
        val envelope = signedEnvelope("{}".toByteArray()).copy(payload = "bad+base64")

        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(envelope)
        }
    }

    private fun signedEnvelope(payload: ByteArray): SignedManifestEnvelope {
        val payloadEncoded = codec.encode(payload)
        val unsigned = SignedManifestEnvelope(
            schemaVersion = ManifestSignatureVerifier.SCHEMA_VERSION,
            keyId = TEST_KEY_ID,
            algorithm = ManifestSignatureVerifier.ALGORITHM,
            payload = payloadEncoded,
            signature = "",
        )
        val signingInput = listOf(
            SIGNING_DOMAIN,
            unsigned.schemaVersion.toString(),
            unsigned.keyId,
            unsigned.algorithm,
            unsigned.payload,
        ).joinToString("\n").toByteArray(StandardCharsets.US_ASCII)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(signingInput)
        return unsigned.copy(signature = codec.encode(signer.sign()))
    }

    private fun generateEcKeyPair(curve: String): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(curve))
        }.generateKeyPair()

    private class JvmBase64UrlCodec : Base64UrlCodec {
        override fun encode(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        override fun decode(value: String): ByteArray =
            Base64.getUrlDecoder().decode(value)
    }

    private companion object {
        const val TEST_KEY_ID = "test-key-2026"
        const val SIGNING_DOMAIN = "AgentDock-Update-Manifest"
        const val MAX_PAYLOAD_BASE64_CHARS = (64 * 1024 * 4 + 2) / 3
    }
}
