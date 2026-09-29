package com.nathzramirez.thesisflow

import com.nathzramirez.thesisflow.domain.validation.InviteCodeFormat
import java.net.URI

/** Builds and reads invite links of the form `https://<host>/join/<code>`. */
object InviteLinks {

    fun build(host: String, code: String): String = "https://$host/join/$code"

    /** Returns the normalized code, or null if [url] isn't a valid invite link for [host]. */
    fun parseCode(url: String?, host: String): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || !uri.host.equals(host, ignoreCase = true)) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size != 2 || segments[0] != "join") return null

        return InviteCodeFormat.normalize(segments[1]).takeIf(InviteCodeFormat::isValid)
    }
}
