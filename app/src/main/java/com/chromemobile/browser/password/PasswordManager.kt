package com.chromemobile.browser.password

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class PasswordManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = ReentrantReadWriteLock()
    private val credentials = mutableListOf<SavedCredential>()

    init {
        loadCredentials()
        if (credentials.isEmpty()) {
            seedDefaultDemoCredentials()
        }
    }

    private fun loadCredentials() {
        lock.write {
            credentials.clear()
            val raw = prefs.getString(KEY_CREDENTIALS_JSON, null)
            if (!raw.isNullOrBlank()) {
                try {
                    val parsed = json.decodeFromString<List<SavedCredential>>(raw)
                    credentials.addAll(parsed)
                } catch (e: Exception) {
                    // Fallback on corrupt JSON
                }
            }
        }
    }

    private fun persistCredentials() {
        lock.read {
            try {
                val raw = json.encodeToString(credentials.toList())
                prefs.edit().putString(KEY_CREDENTIALS_JSON, raw).apply()
            } catch (e: Exception) {
                // Ignore serialization errors
            }
        }
    }

    fun getAllCredentials(): List<SavedCredential> {
        return lock.read {
            credentials.sortedByDescending { it.lastUsed }
        }
    }

    fun getCredentialsForDomain(domainOrUrl: String): List<SavedCredential> {
        val targetDomain = SavedCredential.normalizeDomain(domainOrUrl)
        if (targetDomain.isBlank()) return emptyList()

        return lock.read {
            credentials.filter { cred ->
                val credDomain = cred.normalizedDomain()
                credDomain == targetDomain ||
                        credDomain.endsWith(".$targetDomain") ||
                        targetDomain.endsWith(".$credDomain")
            }.sortedByDescending { it.lastUsed }
        }
    }

    fun getCredentialById(id: String): SavedCredential? {
        return lock.read {
            credentials.firstOrNull { it.id == id }
        }
    }

    fun saveCredential(credential: SavedCredential): SavedCredential {
        val normalized = credential.copy(
            domain = SavedCredential.normalizeDomain(credential.domain.ifEmpty { credential.originUrl }),
            lastUsed = System.currentTimeMillis()
        )

        lock.write {
            val existingIndex = credentials.indexOfFirst {
                it.id == normalized.id || (it.normalizedDomain() == normalized.normalizedDomain() && it.username.equals(normalized.username, ignoreCase = true))
            }

            if (existingIndex >= 0) {
                credentials[existingIndex] = normalized
            } else {
                credentials.add(0, normalized)
            }
            persistCredentials()
        }
        return normalized
    }

    fun deleteCredential(id: String): Boolean {
        return lock.write {
            val removed = credentials.removeAll { it.id == id }
            if (removed) {
                persistCredentials()
            }
            removed
        }
    }

    fun clearAll() {
        lock.write {
            credentials.clear()
            prefs.edit().remove(KEY_CREDENTIALS_JSON).apply()
        }
    }

    fun markUsed(id: String) {
        lock.write {
            val index = credentials.indexOfFirst { it.id == id }
            if (index >= 0) {
                val updated = credentials[index].copy(lastUsed = System.currentTimeMillis())
                credentials[index] = updated
                persistCredentials()
            }
        }
    }

    private fun seedDefaultDemoCredentials() {
        val demoAccounts = listOf(
            SavedCredential(
                domain = "wikipedia.org",
                originUrl = "https://en.wikipedia.org/w/index.php?title=Special:UserLogin",
                username = "wiki_researcher",
                password = "WikiPassword2026!",
                title = "Wikipedia"
            ),
            SavedCredential(
                domain = "github.com",
                originUrl = "https://github.com/login",
                username = "mobile_dev@chromemobile.ai",
                password = "GhPassToken#9876",
                title = "GitHub"
            ),
            SavedCredential(
                domain = "google.com",
                originUrl = "https://accounts.google.com/signin",
                username = "user.chrome@gmail.com",
                password = "GoogleSecure#2026",
                title = "Google Account"
            )
        )
        lock.write {
            credentials.addAll(demoAccounts)
            persistCredentials()
        }
    }

    companion object {
        private const val PREFS_NAME = "chrome_mobile_passwords"
        private const val KEY_CREDENTIALS_JSON = "saved_credentials_json"
    }
}
