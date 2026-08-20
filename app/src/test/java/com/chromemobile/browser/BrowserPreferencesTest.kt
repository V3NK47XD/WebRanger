package com.chromemobile.browser

import com.chromemobile.browser.agent.LlmProvider
import com.chromemobile.browser.agent.SearchEngine
import com.chromemobile.browser.engine.WebViewBrowserEngine
import com.chromemobile.browser.preferences.BrowserPreferences
import com.chromemobile.browser.preferences.CookiePolicy
import com.chromemobile.browser.preferences.SafeBrowsingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPreferencesTest {

    @Test
    fun testDefaultZoomFactorIs80Percent() {
        assertEquals(80, BrowserPreferences.DEFAULT_ZOOM_FACTOR)
    }

    @Test
    fun testCookiePolicyValues() {
        assertEquals("Block third-party cookies", CookiePolicy.BLOCK_THIRD_PARTY.displayName)
        assertEquals("Allow all cookies", CookiePolicy.ALLOW_ALL.displayName)
        assertEquals("Block all cookies", CookiePolicy.BLOCK_ALL.displayName)
    }

    @Test
    fun testSafeBrowsingLevels() {
        assertEquals("Standard protection", SafeBrowsingLevel.STANDARD.displayName)
        assertEquals("Enhanced protection", SafeBrowsingLevel.ENHANCED.displayName)
        assertEquals("No protection", SafeBrowsingLevel.OFF.displayName)
    }

    @Test
    fun testSearchEngineUrls() {
        assertEquals("https://www.google.com/search?q=", SearchEngine.GOOGLE.searchUrl)
        assertEquals("https://duckduckgo.com/?q=", SearchEngine.DUCKDUCKGO.searchUrl)
        assertEquals("https://www.bing.com/search?q=", SearchEngine.BING.searchUrl)
        assertEquals("https://search.brave.com/search?q=", SearchEngine.BRAVE.searchUrl)
    }

    @Test
    fun testChrome131UserAgent() {
        assertTrue(WebViewBrowserEngine.MOBILE_USER_AGENT.contains("Chrome/131.0.0.0"))
        assertTrue(WebViewBrowserEngine.DESKTOP_USER_AGENT.contains("Chrome/131.0.0.0"))
    }
}
