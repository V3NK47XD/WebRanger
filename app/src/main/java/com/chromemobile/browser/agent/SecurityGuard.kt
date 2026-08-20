package com.chromemobile.browser.agent

import java.util.regex.Pattern

object SecurityGuard {

    private val PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|auth_token|token|api_key|pin)")
    private val CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12})\\b")
    private val CVV_PATTERN = Pattern.compile("(?i)(cvv|cvc|security_code)")

    data class SafetyCheckResult(
        val isSafe: Boolean,
        val reason: String? = null,
        val requiresUserConfirmation: Boolean = false,
        val promptMessage: String? = null
    )

    /**
     * Check if typing or interacting with an element requires safety confirmation
     */
    fun checkElementInteractionSafety(
        action: String,
        element: SnapshotElement?,
        typedText: String?,
        allowPasswordAccess: Boolean = false
    ): SafetyCheckResult {
        if (element == null) {
            return SafetyCheckResult(isSafe = true)
        }

        // Check if element is a credit card / payment input
        if (CVV_PATTERN.matcher(element.name).find() ||
            CVV_PATTERN.matcher(element.placeholder).find() ||
            (typedText != null && CREDIT_CARD_PATTERN.matcher(typedText).find())
        ) {
            return SafetyCheckResult(
                isSafe = false,
                requiresUserConfirmation = true,
                promptMessage = "The AI Agent is attempting to fill payment or credit card information. Allow?"
            )
        }

        // Check if element is password
        if (element.type.equals("password", ignoreCase = true) ||
            PASSWORD_PATTERN.matcher(element.name).find() ||
            PASSWORD_PATTERN.matcher(element.placeholder).find()
        ) {
            if (allowPasswordAccess) {
                // User has explicitly enabled AI password tool access in Settings
                return SafetyCheckResult(isSafe = true)
            }
            return SafetyCheckResult(
                isSafe = false,
                requiresUserConfirmation = true,
                promptMessage = "The AI Agent is attempting to enter credentials into a password field. Allow?"
            )
        }

        return SafetyCheckResult(isSafe = true)
    }

    /**
     * Sanitize sensitive values from DOM snapshot text before sending to LLM context
     */
    fun sanitizeSnapshotText(rawText: String): String {
        var sanitized = rawText
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("[REDACTED_CREDIT_CARD]")
        return sanitized
    }
}
