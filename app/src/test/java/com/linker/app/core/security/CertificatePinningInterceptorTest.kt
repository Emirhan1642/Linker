package com.linker.app.core.security

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CertificatePinningInterceptorTest {

    @Before
    fun setUp() {
        CertificatePinningInterceptor.disableTestMode()
    }

    @After
    fun tearDown() {
        CertificatePinningInterceptor.disableTestMode()
    }

    @Test
    fun testCertificatePins_allPinsHaveValidSha256Format() {
        val allPins = listOf(
            CertificatePins.GTS_ROOT_R1,
            CertificatePins.GTS_ROOT_R2,
            CertificatePins.GTS_ROOT_R4,
            CertificatePins.GTS_WE1,
            CertificatePins.GLOBALSIGN_ROOT_R2,
            CertificatePins.SUPABASE_ROOT_CA,
            CertificatePins.SUPABASE_BACKUP_CA,
            CertificatePins.SUPABASE_EDGE_CERT,
            CertificatePins.CLOUDINARY_ROOT_CA,
            CertificatePins.CLOUDINARY_BACKUP_CA
        )

        for (pin in allPins) {
            assertTrue("Pin '$pin' must start with 'sha256/'", pin.startsWith("sha256/"))
            val hashPart = pin.removePrefix("sha256/")
            assertTrue("Pin hash part must not be empty", hashPart.isNotEmpty())
            // Base64 SHA-256 is 44 characters (32 bytes base64 encoded with padding '=')
            assertTrue("Pin hash part should be 44 chars long, got ${hashPart.length}", hashPart.length == 44)
        }
    }

    @Test
    fun testCreateCertificatePinner_returnsValidPinnerByDefault() {
        val pinner = CertificatePinningInterceptor.createCertificatePinner()
        assertNotNull("CertificatePinner should be created by default", pinner)
    }

    @Test
    fun testEnableTestMode_disablesPinner() {
        CertificatePinningInterceptor.enableTestMode()
        val pinner = CertificatePinningInterceptor.createCertificatePinner()
        assertNull("In test mode, createCertificatePinner should return null", pinner)

        // Disable test mode restores pinner creation
        CertificatePinningInterceptor.disableTestMode()
        val restoredPinner = CertificatePinningInterceptor.createCertificatePinner()
        assertNotNull("After disabling test mode, pinner should be created again", restoredPinner)
    }
}
