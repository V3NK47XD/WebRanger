package com.chromemobile.browser

import com.chromemobile.browser.password.SavedCredential
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedCredentialTest {

    @Test
    fun testDomainNormalization() {
        assertEquals("google.com", SavedCredential.normalizeDomain("https://www.google.com/search?q=test"))
        assertEquals("wikipedia.org", SavedCredential.normalizeDomain("http://wikipedia.org/wiki/Main_Page"))
        assertEquals("github.com", SavedCredential.normalizeDomain("https://github.com:443/login"))
        assertEquals("accounts.google.com", SavedCredential.normalizeDomain("https://accounts.google.com/signin"))
        assertEquals("example.org", SavedCredential.normalizeDomain("example.org"))
    }

    @Test
    fun testCredentialSerialization() {
        val cred = SavedCredential(
            domain = "github.com",
            originUrl = "https://github.com/login",
            username = "dev_user",
            password = "secure_pass_123",
            title = "GitHub Account"
        )

        val json = Json { ignoreUnknownKeys = true }
        val serialized = json.encodeToString(cred)
        val deserialized = json.decodeFromString<SavedCredential>(serialized)

        assertEquals(cred.domain, deserialized.domain)
        assertEquals(cred.username, deserialized.username)
        assertEquals(cred.password, deserialized.password)
        assertEquals(cred.title, deserialized.title)
    }

    @Test
    fun testNormalizedDomainMethod() {
        val cred = SavedCredential(
            domain = "https://www.wikipedia.org/login",
            username = "wiki_user",
            password = "password"
        )
        assertEquals("wikipedia.org", cred.normalizedDomain())
    }
}
