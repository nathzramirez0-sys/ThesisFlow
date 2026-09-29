package com.nathzramirez.thesisflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinksTest {

    private val host = "thesisflow.web.app"

    @Test
    fun `a built link parses back to its code`() {
        val link = InviteLinks.build(host, "K7QM2P")
        assertEquals("https://thesisflow.web.app/join/K7QM2P", link)
        assertEquals("K7QM2P", InviteLinks.parseCode(link, host))
    }

    @Test
    fun `lowercase codes and trailing slashes are accepted`() {
        assertEquals("K7QM2P", InviteLinks.parseCode("https://thesisflow.web.app/join/k7qm2p/", host))
    }

    @Test
    fun `links for other hosts, paths or schemes are ignored`() {
        assertNull(InviteLinks.parseCode("https://evil.example/join/K7QM2P", host))
        assertNull(InviteLinks.parseCode("http://thesisflow.web.app/join/K7QM2P", host))
        assertNull(InviteLinks.parseCode("https://thesisflow.web.app/groups/K7QM2P", host))
        assertNull(InviteLinks.parseCode("https://thesisflow.web.app/join/K7QM2P/extra", host))
    }

    @Test
    fun `malformed codes and empty input are ignored`() {
        assertNull(InviteLinks.parseCode("https://thesisflow.web.app/join/K7QM0P", host))
        assertNull(InviteLinks.parseCode(null, host))
        assertNull(InviteLinks.parseCode("not a url", host))
    }
}
