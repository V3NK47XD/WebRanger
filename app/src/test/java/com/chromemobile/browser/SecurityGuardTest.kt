package com.chromemobile.browser

import com.chromemobile.browser.agent.ElementRect
import com.chromemobile.browser.agent.SecurityGuard
import com.chromemobile.browser.agent.SnapshotElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityGuardTest {

    @Test
    fun testPasswordElementRequiresConfirmation() {
        val passwordElement = SnapshotElement(
            id = 1,
            tag = "input",
            type = "password",
            name = "User Password",
            rect = ElementRect(x = 10, y = 20, width = 100, height = 30)
        )

        val result = SecurityGuard.checkElementInteractionSafety(
            action = "type",
            element = passwordElement,
            typedText = "secret123"
        )

        assertFalse(result.isSafe)
        assertTrue(result.requiresUserConfirmation)
        assertTrue(result.promptMessage?.contains("password", ignoreCase = true) == true)
    }

    @Test
    fun testCreditCardRedaction() {
        val rawSnapshot = "Found credit card: 4111222233334444 on form."
        val sanitized = SecurityGuard.sanitizeSnapshotText(rawSnapshot)

        assertEquals("Found credit card: [REDACTED_CREDIT_CARD] on form.", sanitized)
    }

    @Test
    fun testStandardSearchInputIsSafe() {
        val searchElement = SnapshotElement(
            id = 2,
            tag = "input",
            type = "text",
            name = "Search Query",
            placeholder = "Search Wikipedia",
            rect = ElementRect(x = 10, y = 20, width = 200, height = 35)
        )

        val result = SecurityGuard.checkElementInteractionSafety(
            action = "type",
            element = searchElement,
            typedText = "Chromium mobile browser"
        )

        assertTrue(result.isSafe)
        assertFalse(result.requiresUserConfirmation)
    }
}
