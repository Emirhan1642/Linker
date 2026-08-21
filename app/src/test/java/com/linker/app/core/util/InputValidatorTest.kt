package com.linker.app.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun testIsAllowedDomain_exactAndSubdomainMatching() {
        val allowed = listOf("spotify.com", "linker.app")

        // Valid domains
        assertTrue(InputValidator.isAllowedDomain("https://spotify.com/track/123", allowed))
        assertTrue(InputValidator.isAllowedDomain("https://api.spotify.com/v1/tracks", allowed))
        assertTrue(InputValidator.isAllowedDomain("https://linker.app/download", allowed))
        assertTrue(InputValidator.isAllowedDomain("https://sub.linker.app/profile", allowed))

        // Attack payloads / suffix spoofing
        assertFalse(InputValidator.isAllowedDomain("https://evil-spotify.com/login", allowed))
        assertFalse(InputValidator.isAllowedDomain("https://notspotify.com", allowed))
        assertFalse(InputValidator.isAllowedDomain("https://fake-linker.app", allowed))
        assertFalse(InputValidator.isAllowedDomain("https://linker.attacker.com", allowed))
    }
}
