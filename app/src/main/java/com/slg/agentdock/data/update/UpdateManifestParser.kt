package com.slg.agentdock.data.update

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class UpdateManifest(
    val schemaVersion: Int,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val channel: UpdateChannel,
    val publishedAt: Date,
    val issuedAt: Date,
    val expiresAt: Date,
    val minimumSupportedVersionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val supportedAbis: List<String>,
    val apk: ApkMetadata,
    val changelog: Changelog,
)

internal enum class UpdateChannel { STABLE, PREVIEW }

internal data class ApkMetadata(
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

internal data class Changelog(
    val added: List<String>,
    val improved: List<String>,
    val fixed: List<String>,
    val security: List<String>,
    val compatibility: List<String>,
)

internal object UpdateManifestParser {
    private const val MAX_PAYLOAD_CHARS = 64 * 1024
    private const val PACKAGE_NAME = "com.slg.agentdock"
    private const val MAX_APK_SIZE_BYTES = 512L * 1024 * 1024
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    private val REQUIRED_FIELDS = setOf(
        "schemaVersion", "packageName", "versionName", "versionCode", "channel",
        "publishedAt", "issuedAt", "expiresAt", "minimumSupportedVersionCode",
        "minSdk", "targetSdk", "supportedAbis", "apk", "changelog",
    )
    private val APK_FIELDS = setOf("url", "sizeBytes", "sha256")
    private val CHANGELOG_FIELDS = setOf("Added", "Improved", "Fixed", "Security", "Compatibility")

    fun parse(payloadBytes: ByteArray): UpdateManifest {
        require(payloadBytes.isNotEmpty()) { "Manifest payload is empty" }
        require(payloadBytes.size <= 64 * 1024) { "Manifest payload exceeds the size limit" }
        val payload = decodeUtf8(payloadBytes)
        require(payload.length <= MAX_PAYLOAD_CHARS) { "Manifest payload exceeds the size limit" }
        val root = StrictJsonParser.parse(payload, MAX_PAYLOAD_CHARS)
        val fields = (root as? JsonValue.Object)?.fields
            ?: throw IllegalArgumentException("Manifest payload must be a JSON object")
        require(fields.keys == REQUIRED_FIELDS) { "Manifest payload fields are invalid" }

        val publishedAt = fields.requiredDate("publishedAt")
        val issuedAt = fields.requiredDate("issuedAt")
        val expiresAt = fields.requiredDate("expiresAt")
        require(expiresAt.after(issuedAt)) { "expiresAt must be after issuedAt" }

        return UpdateManifest(
            schemaVersion = fields.requiredInt("schemaVersion").also { require(it == 1) { "Unsupported payload schema" } },
            packageName = fields.requiredString("packageName").also { require(it == PACKAGE_NAME) { "Unexpected package name" } },
            versionName = fields.requiredString("versionName").also { require(it.isNotBlank()) { "versionName is empty" } },
            versionCode = fields.requiredPositiveLong("versionCode"),
            channel = fields.requiredString("channel").toChannel(),
            publishedAt = publishedAt,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            minimumSupportedVersionCode = fields.requiredPositiveLong("minimumSupportedVersionCode"),
            minSdk = fields.requiredPositiveInt("minSdk"),
            targetSdk = fields.requiredPositiveInt("targetSdk"),
            supportedAbis = fields.requiredStringArray("supportedAbis"),
            apk = fields.requiredObject("apk").toApkMetadata(),
            changelog = fields.requiredObject("changelog").toChangelog(),
        )
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        throw IllegalArgumentException("Manifest payload is not valid UTF-8")
    }

    private fun Map<String, JsonValue>.requiredString(name: String): String =
        (get(name) as? JsonValue.StringValue)?.value
            ?: throw IllegalArgumentException("$name must be a string")

    private fun Map<String, JsonValue>.requiredInt(name: String): Int {
        val number = get(name) as? JsonValue.NumberValue
            ?: throw IllegalArgumentException("$name must be an integer")
        val value = number.value.toLongOrNull()
            ?: throw IllegalArgumentException("$name must be an integer")
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "$name is out of range" }
        return value.toInt()
    }

    private fun Map<String, JsonValue>.requiredPositiveInt(name: String): Int =
        requiredInt(name).also { require(it > 0) { "$name must be positive" } }

    private fun Map<String, JsonValue>.requiredPositiveLong(name: String): Long {
        val number = get(name) as? JsonValue.NumberValue
            ?: throw IllegalArgumentException("$name must be an integer")
        val value = number.value.toLongOrNull()
            ?: throw IllegalArgumentException("$name must be an integer")
        require(value > 0) { "$name must be positive" }
        return value
    }

    private fun Map<String, JsonValue>.requiredDate(name: String): Date =
        parseRfc3339(requiredString(name), name)

    private fun Map<String, JsonValue>.requiredStringArray(name: String): List<String> {
        val values = (get(name) as? JsonValue.Array)?.values
            ?: throw IllegalArgumentException("$name must be an array")
        require(values.isNotEmpty()) { "$name must not be empty" }
        return values.map {
            (it as? JsonValue.StringValue)?.value
                ?: throw IllegalArgumentException("$name must contain strings")
        }.also { require(it.all(String::isNotBlank)) { "$name contains an empty ABI" } }
    }

    private fun Map<String, JsonValue>.requiredObject(name: String): Map<String, JsonValue> =
        (get(name) as? JsonValue.Object)?.fields
            ?: throw IllegalArgumentException("$name must be an object")

    private fun Map<String, JsonValue>.toApkMetadata(): ApkMetadata {
        require(keys == APK_FIELDS) { "APK fields are invalid" }
        val url = requiredString("url")
        val uri = try { URI(url) } catch (_: Exception) { throw IllegalArgumentException("APK URL is invalid") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "APK URL host is invalid" }
        val size = requiredPositiveLong("sizeBytes")
        require(size <= MAX_APK_SIZE_BYTES) { "APK is too large" }
        val sha256 = requiredString("sha256")
        require(SHA256_PATTERN.matches(sha256)) { "APK SHA-256 is invalid" }
        return ApkMetadata(url, size, sha256)
    }

    private fun Map<String, JsonValue>.toChangelog(): Changelog {
        require(keys == CHANGELOG_FIELDS) { "Changelog fields are invalid" }
        fun items(name: String): List<String> {
            val values = (get(name) as? JsonValue.Array)?.values
                ?: throw IllegalArgumentException("Changelog $name must be an array")
            return values.map {
                (it as? JsonValue.StringValue)?.value
                    ?: throw IllegalArgumentException("Changelog $name must contain strings")
            }
        }
        return Changelog(items("Added"), items("Improved"), items("Fixed"), items("Security"), items("Compatibility"))
    }

    private fun String.toChannel(): UpdateChannel = when (this) {
        "stable" -> UpdateChannel.STABLE
        "preview" -> UpdateChannel.PREVIEW
        else -> throw IllegalArgumentException("Unsupported update channel")
    }

    private fun parseRfc3339(value: String, name: String): Date {
        require(value.length <= 64) { "$name is too long" }
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        for (format in formats) {
            val parser = SimpleDateFormat(format, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val parsed = parser.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed
        }
        throw IllegalArgumentException("$name is not a valid RFC 3339 timestamp")
    }
}
