package com.slg.agentdock.data.update

/**
 * Base64URL codec abstraction so the verifier can be unit-tested on the JVM
 * while production uses Android's API-8-compatible Base64 implementation.
 */
interface Base64UrlCodec {
    fun encode(bytes: ByteArray): String

    fun decode(value: String): ByteArray
}

/**
 * RFC 4648 URL-safe Base64 without padding or line wrapping.
 *
 * android.util.Base64 is available on the app's minSdk 23; java.util.Base64
 * would require API 26 unless core-library desugaring were enabled.
 */
object AndroidBase64UrlCodec : Base64UrlCodec {
    private const val FLAGS =
        android.util.Base64.URL_SAFE or
            android.util.Base64.NO_PADDING or
            android.util.Base64.NO_WRAP

    override fun encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, FLAGS)

    override fun decode(value: String): ByteArray =
        android.util.Base64.decode(value, FLAGS)
}
