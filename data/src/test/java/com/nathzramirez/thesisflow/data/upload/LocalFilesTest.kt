package com.nathzramirez.thesisflow.data.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFilesTest {

    @Test
    fun `ordinary names are kept`() {
        assertEquals("Chapter 1 - Introduction v2.pdf", LocalFiles.sanitizeName("Chapter 1 - Introduction v2.pdf"))
    }

    @Test
    fun `path separators and reserved characters are replaced`() {
        assertEquals("a_b_c_.pdf", LocalFiles.sanitizeName("a/b\\c?.pdf"))
    }

    @Test
    fun `hidden-file dots and empty names are handled`() {
        assertEquals("draft.pdf", LocalFiles.sanitizeName("..draft.pdf"))
        assertEquals("file", LocalFiles.sanitizeName("   "))
    }

    @Test
    fun `long names are shortened but keep their extension`() {
        val name = LocalFiles.sanitizeName("x".repeat(150) + ".docx")
        assertEquals(100, name.length)
        assertTrue(name.endsWith(".docx"))
    }
}
