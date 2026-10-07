package com.nathzramirez.thesisflow.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameTest {

    @Test
    fun `titles are skipped for greetings and initials`() {
        assertEquals("Liza", PersonName.firstName("Prof. Liza Reyes"))
        assertEquals("LR", PersonName.initials("Prof. Liza Reyes"))
        assertEquals("RC", PersonName.initials("engr ramon cruz"))
        assertEquals("Ana", PersonName.firstName("Dr. Ma'am Ana Santos"))
    }

    @Test
    fun `initials use the first and last name`() {
        assertEquals("JC", PersonName.initials("Juan Dela Cruz"))
        assertEquals("CM", PersonName.initials("  Carla   Mendoza "))
        assertEquals("D", PersonName.initials("Dan"))
    }

    @Test
    fun `odd names still give something sensible`() {
        assertEquals("Prof.", PersonName.firstName("Prof."))
        assertEquals("?", PersonName.initials(""))
        assertEquals("", PersonName.firstName("   "))
        assertEquals("JS", PersonName.initials("(Jo) Smith"))
    }
}
