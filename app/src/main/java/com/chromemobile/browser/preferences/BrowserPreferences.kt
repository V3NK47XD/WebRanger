package com.chromemobile.browser.preferences

import android.content.Context
import android.content.SharedPreferences
import com.chromemobile.browser.agent.SearchEngine
import org.json.JSONArray
import org.json.JSONObject

enum class CookiePolicy(val displayName: String, val description: String) {
    BLOCK_THIRD_PARTY("Block third-party cookies", "Sites might not work if third-party cookies are blocked (Default Chrome behavior)."),
    ALLOW_ALL("Allow all cookies", "Sites work normally, but your browsing can be tracked across sites."),
    BLOCK_ALL("Block all cookies", "Not recommended. Many websites may break completely.")
}

enum class SafeBrowsingLevel(val displayName: String, val description: String) {
    STANDARD("Standard protection", "Proactively protects against dangerous sites, downloads, and extensions."),
    ENHANCED("Enhanced protection", "Faster, proactive protection with advanced AI threat analysis."),
    OFF("No protection", "Not recommended. Turns off dangerous site alerts.")
}

data class Bookmark(val title: String, val url: String)

class BrowserPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Zoom Factor (Default 80%)
    var zoomFactor: Int
        get() = prefs.getInt(KEY_ZOOM_FACTOR, DEFAULT_ZOOM_FACTOR)
        set(value) = prefs.edit().putInt(KEY_ZOOM_FACTOR, value).apply()

    // Password sharing with AI / MCP tools
    var sharePasswordsWithLlm: Boolean
        get() = prefs.getBoolean(KEY_SHARE_PASSWORDS_WITH_LLM, false)
        set(value) = prefs.edit().putBoolean(KEY_SHARE_PASSWORDS_WITH_LLM, value).apply()

    // Background MCP Server enabled (for Termux / omp / Claude Code)
    var mcpServerEnabled: Boolean
        get() = prefs.getBoolean(KEY_MCP_SERVER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MCP_SERVER_ENABLED, value).apply()

    // Background MCP Server Port (default 8765)
    var mcpServerPort: Int
        get() = prefs.getInt(KEY_MCP_SERVER_PORT, 8765)
        set(value) = prefs.edit().putInt(KEY_MCP_SERVER_PORT, value).apply()

    // Background Keep-Alive (Foreground service with WakeLock)
    var backgroundKeepAlive: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_KEEP_ALIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_KEEP_ALIVE, value).apply()

    // Save Passwords prompt
    var savePasswordsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAVE_PASSWORDS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_PASSWORDS_ENABLED, value).apply()

    // Auto Sign-in
    var autoSignInEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SIGN_IN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SIGN_IN_ENABLED, value).apply()

    // Search Engine
    var searchEngine: SearchEngine
        get() {
            val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.name) ?: SearchEngine.GOOGLE.name
            return try {
                SearchEngine.valueOf(name)
            } catch (e: Exception) {
                SearchEngine.GOOGLE
            }
        }
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINE, value.name).apply()

    // Cookie Policy
    var cookiePolicy: CookiePolicy
        get() {
            val name = prefs.getString(KEY_COOKIE_POLICY, CookiePolicy.BLOCK_THIRD_PARTY.name) ?: CookiePolicy.BLOCK_THIRD_PARTY.name
            return try {
                CookiePolicy.valueOf(name)
            } catch (e: Exception) {
                CookiePolicy.BLOCK_THIRD_PARTY
            }
        }
        set(value) = prefs.edit().putString(KEY_COOKIE_POLICY, value.name).apply()

    // Safe Browsing
    var safeBrowsingLevel: SafeBrowsingLevel
        get() {
            val name = prefs.getString(KEY_SAFE_BROWSING, SafeBrowsingLevel.STANDARD.name) ?: SafeBrowsingLevel.STANDARD.name
            return try {
                SafeBrowsingLevel.valueOf(name)
            } catch (e: Exception) {
                SafeBrowsingLevel.STANDARD
            }
        }
        set(value) = prefs.edit().putString(KEY_SAFE_BROWSING, value.name).apply()

    // JavaScript Enabled
    var javascriptEnabled: Boolean
        get() = prefs.getBoolean(KEY_JAVASCRIPT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_JAVASCRIPT_ENABLED, value).apply()

    // DOM / Local Storage Enabled
    var domStorageEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOM_STORAGE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DOM_STORAGE_ENABLED, value).apply()

    // Pop-ups & Redirects
    var popupsEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUPS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_POPUPS_ENABLED, value).apply()

    // Do Not Track
    var doNotTrack: Boolean
        get() = prefs.getBoolean(KEY_DO_NOT_TRACK, true)
        set(value) = prefs.edit().putBoolean(KEY_DO_NOT_TRACK, value).apply()

    // HTTPS First Mode
    var httpsFirstMode: Boolean
        get() = prefs.getBoolean(KEY_HTTPS_FIRST_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_HTTPS_FIRST_MODE, value).apply()

    // Desktop Site Mode
    var desktopSiteMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_SITE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_SITE_MODE, value).apply()

    // Force Dark Mode for Web Contents
    var forceDarkMode: Boolean
        get() = prefs.getBoolean(KEY_FORCE_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_DARK_MODE, value).apply()

    // Load Images Automatically
    var loadsImagesAutomatically: Boolean
        get() = prefs.getBoolean(KEY_LOAD_IMAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_LOAD_IMAGES, value).apply()

    // Bookmarks — persisted as a JSON array of {title, url} objects
    var bookmarks: List<Bookmark>
        get() {
            val json = prefs.getString(KEY_BOOKMARKS, null) ?: return DEFAULT_BOOKMARKS
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    Bookmark(title = obj.getString("title"), url = obj.getString("url"))
                }
            } catch (e: Exception) {
                DEFAULT_BOOKMARKS
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { bm ->
                arr.put(JSONObject().apply {
                    put("title", bm.title)
                    put("url", bm.url)
                })
            }
            prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
        }

    companion object {
        const val DEFAULT_ZOOM_FACTOR = 80
        private const val PREFS_NAME = "chrome_mobile_browser_settings"
        private const val KEY_ZOOM_FACTOR = "browser_zoom_factor"
        private const val KEY_SHARE_PASSWORDS_WITH_LLM = "share_passwords_with_llm"
        private const val KEY_SAVE_PASSWORDS_ENABLED = "save_passwords_enabled"
        private const val KEY_AUTO_SIGN_IN_ENABLED = "auto_sign_in_enabled"
        private const val KEY_SEARCH_ENGINE = "browser_search_engine"
        private const val KEY_COOKIE_POLICY = "browser_cookie_policy"
        private const val KEY_SAFE_BROWSING = "browser_safe_browsing"
        private const val KEY_JAVASCRIPT_ENABLED = "browser_js_enabled"
        private const val KEY_DOM_STORAGE_ENABLED = "browser_dom_storage_enabled"
        private const val KEY_POPUPS_ENABLED = "browser_popups_enabled"
        private const val KEY_DO_NOT_TRACK = "browser_do_not_track"
        private const val KEY_HTTPS_FIRST_MODE = "browser_https_first_mode"
        private const val KEY_DESKTOP_SITE_MODE = "browser_desktop_site_mode"
        private const val KEY_FORCE_DARK_MODE = "browser_force_dark_mode"
        private const val KEY_LOAD_IMAGES = "browser_load_images"
        private const val KEY_BOOKMARKS = "browser_bookmarks"
        private const val KEY_MCP_SERVER_ENABLED = "mcp_server_enabled"
        private const val KEY_MCP_SERVER_PORT = "mcp_server_port"
        private const val KEY_BACKGROUND_KEEP_ALIVE = "background_keep_alive"

        val DEFAULT_BOOKMARKS = listOf(
            Bookmark("Google", "https://google.com"),
            Bookmark("GitHub", "https://github.com"),
            Bookmark("YouTube", "https://youtube.com"),
            Bookmark("Wikipedia", "https://en.wikipedia.org"),
        )
    }
}
