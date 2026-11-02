package com.slg.agentdock.data.update

/** Small strict JSON parser used for untrusted update metadata. */
internal sealed interface JsonValue {
    data class Object(val fields: LinkedHashMap<String, JsonValue>) : JsonValue
    data class Array(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val value: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal object StrictJsonParser {
    fun parse(input: String, maxChars: Int): JsonValue {
        require(maxChars >= 0) { "JSON size limit must not be negative" }
        require(input.length <= maxChars) { "JSON exceeds the size limit" }
        val parser = Parser(input)
        val value = parser.parseValue()
        parser.skipWhitespace()
        require(parser.atEnd()) { "Trailing JSON content" }
        return value
    }

    private class Parser(private val input: String) {
        private var index = 0

        fun parseValue(): JsonValue {
            skipWhitespace()
            require(!atEnd()) { "Unexpected end of JSON" }
            return when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.StringValue(parseString())
                't' -> parseLiteral("true", JsonValue.BooleanValue(true))
                'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
                'n' -> parseLiteral("null", JsonValue.NullValue)
                '-', in '0'..'9' -> JsonValue.NumberValue(parseNumber())
                else -> invalid("Unexpected JSON token")
            }
        }

        fun skipWhitespace() {
            // RFC 8259 permits only SP, HTAB, LF and CR outside JSON values.
            while (!atEnd() && input[index] in JSON_WHITESPACE) index++
        }

        fun atEnd(): Boolean = index == input.length

        private fun parseObject(): JsonValue.Object {
            index++
            val fields = linkedMapOf<String, JsonValue>()
            skipWhitespace()
            if (consume('}')) return JsonValue.Object(fields)
            while (true) {
                skipWhitespace()
                require(!atEnd() && input[index] == '"') { "Object key must be a string" }
                val key = parseString()
                val value = parseMemberValue()
                require(!fields.containsKey(key)) { "Duplicate JSON key: $key" }
                fields[key] = value
                skipWhitespace()
                if (consume('}')) return JsonValue.Object(fields)
                require(consume(',')) { "Expected comma between object fields" }
            }
        }

        private fun parseMemberValue(): JsonValue {
            skipWhitespace()
            require(consume(':')) { "Expected colon after object key" }
            return parseValue()
        }

        private fun parseArray(): JsonValue.Array {
            index++
            val values = mutableListOf<JsonValue>()
            skipWhitespace()
            if (consume(']')) return JsonValue.Array(values)
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (consume(']')) return JsonValue.Array(values)
                require(consume(',')) { "Expected comma between array values" }
            }
        }

        private fun parseString(): String {
            require(consume('"')) { "Expected string" }
            val result = StringBuilder()
            while (!atEnd()) {
                when (val character = input[index++]) {
                    '"' -> {
                        val value = result.toString()
                        validateSurrogatePairs(value)
                        return value
                    }
                    '\\' -> {
                        require(!atEnd()) { "Incomplete escape sequence" }
                        when (val escaped = input[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> result.append(parseUnicodeEscape())
                            else -> invalid("Invalid JSON escape")
                        }
                    }
                    in '\u0000'..'\u001f' -> invalid("Unescaped control character in string")
                    else -> result.append(character)
                }
            }
            invalid("Unterminated JSON string")
        }

        private fun validateSurrogatePairs(value: String) {
            var offset = 0
            while (offset < value.length) {
                val character = value[offset]
                when {
                    Character.isHighSurrogate(character) -> {
                        require(
                            offset + 1 < value.length &&
                                Character.isLowSurrogate(value[offset + 1]),
                        ) { "Unpaired high surrogate in JSON string" }
                        offset += 2
                    }
                    Character.isLowSurrogate(character) ->
                        invalid("Unpaired low surrogate in JSON string")
                    else -> offset++
                }
            }
        }

        private fun parseUnicodeEscape(): Char {
            require(index + 4 <= input.length) { "Incomplete unicode escape" }
            val hex = input.substring(index, index + 4)
            require(hex.all { it in "0123456789abcdefABCDEF" }) { "Invalid unicode escape" }
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun parseNumber(): String {
            val start = index
            if (consume('-')) require(!atEnd()) { "Incomplete JSON number" }
            if (consume('0')) {
                require(atEnd() || input[index] !in '0'..'9') { "Leading zero in JSON number" }
            } else {
                require(!atEnd() && input[index] in '1'..'9') { "Invalid JSON number" }
                while (!atEnd() && input[index] in '0'..'9') index++
            }
            if (consume('.')) {
                require(!atEnd() && input[index] in '0'..'9') { "Invalid JSON fraction" }
                while (!atEnd() && input[index] in '0'..'9') index++
            }
            if (!atEnd() && input[index] in "eE") {
                index++
                if (!atEnd() && input[index] in "+-") index++
                require(!atEnd() && input[index] in '0'..'9') { "Invalid JSON exponent" }
                while (!atEnd() && input[index] in '0'..'9') index++
            }
            return input.substring(start, index)
        }

        private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
            require(input.regionMatches(index, literal, 0, literal.length)) { "Invalid JSON literal" }
            index += literal.length
            return value
        }

        private fun consume(expected: Char): Boolean {
            if (!atEnd() && input[index] == expected) {
                index++
                return true
            }
            return false
        }

        private fun invalid(message: String): Nothing =
            throw IllegalArgumentException(message)
    }

    private val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')
}