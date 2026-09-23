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

    @Test
    fun testDynamicUserAgentUsesRealChromeVersion() {
        val customVersion = "134.0.6998.39"
        val mobile = WebViewBrowserEngine.buildMobileUserAgent(customVersion)
        val desktop = WebViewBrowserEngine.buildDesktopUserAgent(customVersion)
        assertTrue(mobile.contains("Chrome/134.0.6998.39"))
        assertTrue(desktop.contains("Chrome/134.0.6998.39"))
        assertTrue(mobile.contains("Mobile Safari"))
        assertTrue(desktop.contains("Safari"))
        assertFalse(desktop.contains("Mobile Safari"))
        assertFalse(mobile.contains("ChromeMobileAI"))
        assertFalse(desktop.contains("ChromeMobileAI"))
    }

    @Test
    fun testDetectChromeVersionFallback() {
        val fallbackVersion = WebViewBrowserEngine.detectChromeVersion(null, null)
        assertEquals(WebViewBrowserEngine.DEFAULT_CHROME_VERSION, fallbackVersion)
    }
}
