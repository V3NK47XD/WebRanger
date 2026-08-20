package com.chromemobile.browser.password

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SavedCredential(
    val id: String = UUID.randomUUID().toString(),
    val domain: String,
    val originUrl: String = "",
    val username: String,
    val password: String,
    val title: String = "",
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Normalized domain for matching (e.g. "github.com")
     */
    fun normalizedDomain(): String {
        return normalizeDomain(domain.ifEmpty { originUrl })
    }

    companion object {
        fun normalizeDomain(raw: String): String {
            if (raw.isBlank()) return ""
            var clean = raw.trim().lowercase()
            if (clean.startsWith("http://")) clean = clean.removePrefix("http://")
            if (clean.startsWith("https://")) clean = clean.removePrefix("https://")
            clean = clean.substringBefore("/").substringBefore(":")
            if (clean.startsWith("www.")) clean = clean.removePrefix("www.")
            return clean
        }
    }
}
