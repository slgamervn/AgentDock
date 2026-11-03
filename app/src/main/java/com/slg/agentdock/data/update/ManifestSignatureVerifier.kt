package com.slg.agentdock.data.update

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp

/**
 * Signed outer envelope. The payload remains opaque until its signature passes.
 *
 * The parser that creates this value must reject duplicate and unknown JSON keys.
 */
data class SignedManifestEnvelope(
    val schemaVersion: Int,
    val keyId: String,
    val algorithm: String,
    val payload: String,
    val signature: String,
)

/**
 * Verifies the exact payload bytes before any payload parsing or URL handling.
 *
 * Protocol v1 uses a domain-separated ECDSA P-256 / SHA-256 signature encoded
 * as the ASN.1 DER ECDSA-Sig-Value structure produced by Java's
 * SHA256withECDSA provider. This is deliberately not labelled JWS/ES256:
 * JWS ES256 uses a fixed-width R || S signature representation.
 */
class ManifestSignatureVerifier(
    trustedKeys: Map<String, PublicKey>,
    private val base64UrlCodec: Base64UrlCodec,
) {
    private val trustedKeys: Map<String, PublicKey> = trustedKeys.toMap()

    init {
        require(this.trustedKeys.isNotEmpty()) { "Trusted manifest key ring is empty" }
        require(this.trustedKeys.size <= MAX_TRUSTED_KEYS) { "Too many trusted manifest keys" }
        this.trustedKeys.keys.forEach { keyId ->
            require(KEY_ID_PATTERN.matches(keyId)) { "Invalid trusted manifest key ID" }
        }
    }

    /**
     * @return a defensive copy of the original signed payload bytes.
     * @throws IllegalArgumentException when the envelope is malformed or untrusted.
     */
    fun verify(envelope: SignedManifestEnvelope): ByteArray {
        require(envelope.schemaVersion == SCHEMA_VERSION) { "Unsupported manifest envelope version" }
        require(envelope.algorithm == ALGORITHM) { "Unsupported manifest signature algorithm" }
        require(KEY_ID_PATTERN.matches(envelope.keyId)) { "Invalid manifest key ID" }

        require(envelope.payload.length <= MAX_PAYLOAD_BASE64_CHARS) {
            "Manifest payload exceeds the size limit"
        }
        require(envelope.signature.length <= MAX_SIGNATURE_BASE64_CHARS) {
            "Manifest signature exceeds the size limit"
        }

        val payloadBytes = decodeCanonicalBase64Url(envelope.payload, "payload")
        require(payloadBytes.isNotEmpty()) { "Manifest payload is empty" }
        require(payloadBytes.size <= MAX_PAYLOAD_BYTES) { "Manifest payload exceeds the size limit" }

        val signatureBytes = decodeCanonicalBase64Url(envelope.signature, "signature")
        require(signatureBytes.size in MIN_DER_SIGNATURE_BYTES..MAX_DER_SIGNATURE_BYTES) {
            "Manifest signature has an invalid length"
        }

        val publicKey = trustedKeys[envelope.keyId]
            ?: throw IllegalArgumentException("Manifest key is not trusted")

        require(isP256PublicKey(publicKey)) {
            "Trusted manifest key is not an EC P-256 public key"
        }

        val verifier = Signature.getInstance(SIGNATURE_JCA_ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(signingInput(envelope))
        require(verifier.verify(signatureBytes)) { "Manifest signature verification failed" }

        return payloadBytes.copyOf()
    }

    private fun decodeCanonicalBase64Url(value: String, fieldName: String): ByteArray {
        require(value.isNotEmpty()) { "Manifest $fieldName is empty" }
        require(BASE64_URL_PATTERN.matches(value)) {
            "Manifest $fieldName is not unpadded Base64URL"
        }

        val decoded = try {
            base64UrlCodec.decode(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Manifest $fieldName is malformed")
        }

        require(base64UrlCodec.encode(decoded) == value) {
            "Manifest $fieldName is not canonically encoded"
        }
        return decoded
    }

    private fun signingInput(envelope: SignedManifestEnvelope): ByteArray {
        val value = buildString {
            append(SIGNING_DOMAIN)
            append('\n')
            append(envelope.schemaVersion)
            append('\n')
            append(envelope.keyId)
            append('\n')
            append(envelope.algorithm)
            append('\n')
            append(envelope.payload)
        }
        return value.toByteArray(StandardCharsets.US_ASCII)
    }

    private fun isP256PublicKey(publicKey: PublicKey): Boolean {
        val ecKey = publicKey as? ECPublicKey ?: return false
        val parameters = ecKey.params
        val field = parameters.curve.field as? ECFieldFp ?: return false
        val curve = parameters.curve

        return field.p == P256_P &&
            curve.a == P256_A &&
            curve.b == P256_B &&
            parameters.generator.affineX == P256_GX &&
            parameters.generator.affineY == P256_GY &&
            parameters.order == P256_ORDER &&
            parameters.cofactor == 1
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val ALGORITHM = "ECDSA_P256_SHA256_DER"

        private const val SIGNING_DOMAIN = "AgentDock-Update-Manifest"
        private const val SIGNATURE_JCA_ALGORITHM = "SHA256withECDSA"
        private const val MAX_PAYLOAD_BYTES = 64 * 1024
        private const val MAX_PAYLOAD_BASE64_CHARS = (MAX_PAYLOAD_BYTES * 4 + 2) / 3
        private const val MIN_DER_SIGNATURE_BYTES = 8
        private const val MAX_DER_SIGNATURE_BYTES = 72
        private const val MAX_SIGNATURE_BASE64_CHARS = 96
        private const val MAX_TRUSTED_KEYS = 8

        private val KEY_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,64}")
        private val BASE64_URL_PATTERN = Regex("[A-Za-z0-9_-]+")

        private val P256_P = BigInteger(
            "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16,
        )
        private val P256_A = BigInteger(
            "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16,
        )
        private val P256_B = BigInteger(
            "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16,
        )
        private val P256_GX = BigInteger(
            "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16,
        )
        private val P256_GY = BigInteger(
            "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16,
        )
        private val P256_ORDER = BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16,
        )
    }
}
