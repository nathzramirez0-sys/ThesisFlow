package com.nathzramirez.thesisflow.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeFormatTest {

    @Test
    fun `normalize uppercases and strips spaces and dashes`() {
        assertEquals("K7QM2P", InviteCodeFormat.normalize(" k7q-m 2p "))
    }

    @Test
    fun `codes with look-alike characters are rejected`() {
        assertTrue(InviteCodeFormat.isValid("K7QM2P"))
        assertFalse(InviteCodeFormat.isValid("K7QM0P")) // zero
        assertFalse(InviteCodeFormat.isValid("K7QMIP")) // capital I
        assertFalse(InviteCodeFormat.isValid("K7QM2")) // too short
    }
}
