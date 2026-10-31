package com.slg.agentdock.domain.validation

import java.net.URI

/** Returns a normalized HTTP(S) base URL, or null when the value is unsafe/invalid. */
fun normalizeServerAddress(value: String): String? {
    val candidate = value.trim().trimEnd('/')
    if (candidate.isEmpty()) return ""

    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("http", "https")) return null
    if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null

    return uri.normalize().toASCIIString().trimEnd('/')
}
