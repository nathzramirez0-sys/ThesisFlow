package com.nathzramirez.thesisflow.domain.validation

import com.nathzramirez.thesisflow.domain.model.LocalFileInfo
import com.nathzramirez.thesisflow.domain.result.DomainError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadRulesTest {

    private fun file(mimeType: String, sizeBytes: Long = 1_000) = LocalFileInfo("draft", mimeType, sizeBytes)

    @Test
    fun `pdf, docx and images are accepted`() {
        assertNull(UploadRules.check(file(UploadRules.PDF)))
        assertNull(UploadRules.check(file(UploadRules.DOCX)))
        assertNull(UploadRules.check(file("image/jpeg")))
    }

    @Test
    fun `other types are rejected`() {
        assertEquals(DomainError.UnsupportedFileType, UploadRules.check(file("application/msword")))
        assertEquals(DomainError.UnsupportedFileType, UploadRules.check(file("video/mp4")))
    }

    @Test
    fun `size must be between one byte and the limit`() {
        assertNull(UploadRules.check(file(UploadRules.PDF, UploadRules.MAX_BYTES)))
        assertEquals(DomainError.FileTooLarge, UploadRules.check(file(UploadRules.PDF, UploadRules.MAX_BYTES + 1)))
        assertEquals(DomainError.FileUnreadable, UploadRules.check(file(UploadRules.PDF, 0)))
    }
}
