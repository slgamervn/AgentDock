package com.slg.agentdock.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestParserTest {
    @Test
    fun parsesValidStableManifest() {
        val manifest = UpdateManifestParser.parse(validPayload().toByteArray())

        assertEquals(UpdateChannel.STABLE, manifest.channel)
        assertEquals(42L, manifest.versionCode)
        assertEquals(
            "https://github.com/slgamervn/AgentDock/releases/download/v1.0.0/agentdock.apk",
            manifest.apk.url,
        )
        assertEquals(1, manifest.changelog.added.size)
    }

    @Test
    fun rejectsUnknownField() {
        assertRejected(validPayload().replace("\"changelog\":", "\"unknown\":true,\"changelog\":"))
    }

    @Test
    fun rejectsDuplicateField() {
        assertRejected(validPayload().replace("\"versionCode\":42", "\"versionCode\":42,\"versionCode\":43"))
    }

    @Test
    fun rejectsWrongPackage() {
        assertRejected(validPayload().replace("com.slg.agentdock", "com.example.other"))
    }

    @Test
    fun rejectsUnsupportedChannel() {
        assertRejected(validPayload().replace("\"stable\"", "\"beta\""))
    }

    @Test
    fun rejectsExpiredOrdering() {
        assertRejected(validPayload().replace("2026-12-01T00:00:00Z", "2026-10-01T00:00:00Z"))
    }

    @Test
    fun rejectsPublishedAfterIssued() {
        assertRejected(
            validPayload().replace(
                "\"publishedAt\":\"2026-11-01T00:00:00Z\"",
                "\"publishedAt\":\"2026-11-02T00:00:00Z\"",
            ),
        )
    }

    @Test
    fun rejectsUnsupportedReleaseHostAndUrlConfusion() {
        assertRejected(validPayload().replace("https://github.com/", "https://attacker.example/"))
        assertRejected(
            validPayload().replace(
                "https://github.com/",
                "https://github.com@attacker.example/",
            ),
        )
        assertRejected(
            validPayload().replace(
                "https://github.com/",
                "https://github.com:8443/",
            ),
        )
    }

    @Test
    fun rejectsNonHttpsApkUrl() {
        assertRejected(validPayload().replace("https://github.com", "http://github.com"))
    }

    @Test
    fun rejectsWrongReleasePath() {
        assertRejected(
            validPayload().replace(
                "/slgamervn/AgentDock/releases/download/",
                "/someone/else/releases/download/",
            ),
        )
    }

    @Test
    fun rejectsMinimumSupportedVersionAboveReleaseVersion() {
        assertRejected(
            validPayload().replace(
                "\"minimumSupportedVersionCode\":1",
                "\"minimumSupportedVersionCode\":43",
            ),
        )
    }

    @Test
    fun rejectsVersionCodeAboveAndroidLimit() {
        assertRejected(validPayload().replace("\"versionCode\":42", "\"versionCode\":2100000001"))
    }

    @Test
    fun rejectsTargetSdkBelowMinimumSdk() {
        assertRejected(validPayload().replace("\"targetSdk\":36", "\"targetSdk\":22"))
    }

    @Test
    fun rejectsDuplicateSupportedAbis() {
        assertRejected(
            validPayload().replace(
                "\"x86_64\"]",
                "\"arm64-v8a\"]",
            ),
        )
    }

    @Test
    fun rejectsInvalidSha256() {
        assertRejected(validPayload().replace("a".repeat(64), "b".repeat(63)))
    }

    @Test
    fun rejectsTrailingJsonContent() {
        assertRejected(validPayload() + "{}")
    }

    private fun assertRejected(payload: String) {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifestParser.parse(payload.toByteArray())
        }
    }

    private fun validPayload(): String = """
        {
          "schemaVersion":1,
          "packageName":"com.slg.agentdock",
          "versionName":"1.0.0",
          "versionCode":42,
          "channel":"stable",
          "publishedAt":"2026-11-01T00:00:00Z",
          "issuedAt":"2026-11-01T00:00:00Z",
          "expiresAt":"2026-12-01T00:00:00Z",
          "minimumSupportedVersionCode":1,
          "minSdk":23,
          "targetSdk":36,
          "supportedAbis":["arm64-v8a","x86_64"],
          "apk":{
            "url":"https://github.com/slgamervn/AgentDock/releases/download/v1.0.0/agentdock.apk",
            "sizeBytes":123456,
            "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          },
          "changelog":{
            "Added":["Update Center"],
            "Improved":[],
            "Fixed":[],
            "Security":[],
            "Compatibility":[]
          }
        }
    """.trimIndent()
}