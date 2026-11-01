package com.slg.agentdock.data.update

internal object ManifestEnvelopeParser {
    private const val MAX_ENVELOPE_CHARS = 128 * 1024
    private val REQUIRED_FIELDS = setOf("schemaVersion", "keyId", "algorithm", "payload", "signature")

    fun parse(json: String): SignedManifestEnvelope {
        val root = StrictJsonParser.parse(json, MAX_ENVELOPE_CHARS)
        val objectValue = root as? JsonValue.Object ?: error("Manifest envelope must be a JSON object")
        require(objectValue.fields.keys == REQUIRED_FIELDS) { "Manifest envelope fields are invalid" }

        return SignedManifestEnvelope(
            schemaVersion = objectValue.fields.requiredNumber("schemaVersion").toIntExact("schemaVersion"),
            keyId = objectValue.fields.requiredString("keyId"),
            algorithm = objectValue.fields.requiredString("algorithm"),
            payload = objectValue.fields.requiredString("payload"),
            signature = objectValue.fields.requiredString("signature"),
        )
    }

    private fun Map<String, JsonValue>.requiredString(name: String): String =
        get(name).let { value ->
            require(value is JsonValue.StringValue) { "$name must be a string" }
            value.value
        }

    private fun Map<String, JsonValue>.requiredNumber(name: String): JsonValue.NumberValue =
        get(name).let { value ->
            require(value is JsonValue.NumberValue) { "$name must be an integer" }
            require(value.value.toLongOrNull() != null) { "$name must be an integer" }
            value
        }

    private fun JsonValue.NumberValue.toIntExact(name: String): Int {
        val value = value.toLongOrNull() ?: error("$name must be an integer")
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "$name is out of range" }
        return value.toInt()
    }
}
