package com.nathzramirez.thesisflow.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `email must be present and well formed`() {
        assertEquals(ValidationError.REQUIRED, Validators.email("  "))
        assertEquals(ValidationError.INVALID_EMAIL, Validators.email("juan@"))
        assertEquals(ValidationError.INVALID_EMAIL, Validators.email("juan delacruz@up.edu.ph"))
        assertNull(Validators.email(" juan@up.edu.ph "))
    }

    @Test
    fun `new password needs eight characters with a letter and a digit`() {
        assertEquals(ValidationError.REQUIRED, Validators.newPassword(""))
        assertEquals(ValidationError.PASSWORD_TOO_SHORT, Validators.newPassword("abc123"))
        assertEquals(ValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT, Validators.newPassword("abcdefgh"))
        assertEquals(ValidationError.PASSWORD_NEEDS_LETTER_AND_DIGIT, Validators.newPassword("12345678"))
        assertNull(Validators.newPassword("thesis2026"))
    }

    @Test
    fun `confirmation must match the password`() {
        assertEquals(ValidationError.REQUIRED, Validators.confirmPassword("thesis2026", ""))
        assertEquals(
            ValidationError.PASSWORDS_DO_NOT_MATCH,
            Validators.confirmPassword("thesis2026", "thesis2025"),
        )
        assertNull(Validators.confirmPassword("thesis2026", "thesis2026"))
    }

    @Test
    fun `text fields are trimmed before checking length`() {
        assertEquals(ValidationError.TOO_SHORT, Validators.displayName(" J "))
        assertEquals(
            ValidationError.TOO_LONG,
            Validators.groupName("x".repeat(Validators.GROUP_NAME_MAX + 1)),
        )
        assertNull(Validators.groupName("  Group 7  "))
    }

    @Test
    fun `thesis title is optional but capped`() {
        assertNull(Validators.thesisTitle(""))
        assertEquals(
            ValidationError.TOO_LONG,
            Validators.thesisTitle("x".repeat(Validators.THESIS_TITLE_MAX + 1)),
        )
    }

    @Test
    fun `fieldErrorsOf keeps only the failing fields`() {
        val errors = fieldErrorsOf(
            Field.EMAIL to null,
            Field.PASSWORD to ValidationError.REQUIRED,
        )
        assertEquals(mapOf(Field.PASSWORD to ValidationError.REQUIRED), errors)
    }
}
