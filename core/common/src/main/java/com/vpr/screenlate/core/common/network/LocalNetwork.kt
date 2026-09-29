package com.vpr.screenlate.core.common.network

/**
 * Tells addresses on the local network apart, for the local network permission of newer Android versions.
 * Loopback addresses are not on the local network.
 */
object LocalNetwork {
    private val HOST = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://(?:[^/?#@]*@)?(\[[^]]*]|[^/?#:]*)""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal")

    /** Whether [url] (a URL or a URL template) points to a device on the local network. */
    fun isLocalUrl(url: String): Boolean = HOST.find(url.trim())?.groupValues?.get(1)?.let(::isLocalHost) == true

    /** Whether [host] is a private or link-local address, or a name that only resolves on the local network. */
    fun isLocalHost(host: String): Boolean {
        val name = host.trim().removePrefix("[").removeSuffix("]").trimEnd('.').lowercase()
        if (name.isEmpty() || name == "localhost") return false
        (ipv4(name) ?: mappedIpv4(name))?.let { (a, b) ->
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (':' in name) return name.startsWith("fc") || name.startsWith("fd") || name.startsWith("fe8") ||
            name.startsWith("fe9") || name.startsWith("fea") || name.startsWith("feb")
        return '.' !in name || LOCAL_SUFFIXES.any { name.endsWith(it) }
    }

    /** The first two octets of an IPv4 literal. */
    private fun ipv4(name: String): Pair<Int, Int>? {
        val parts = name.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 && part.all(Char::isDigit) } ?: return null }
        return octets[0] to octets[1]
    }

    /** The first two octets of an IPv4-mapped IPv6 address: `::ffff:192.168.1.20` or `::ffff:c0a8:114`. */
    private fun mappedIpv4(name: String): Pair<Int, Int>? {
        val mapped = name.removePrefix("0:0:0:0:0:ffff:").removePrefix("::ffff:").takeIf { it != name } ?: return null
        ipv4(mapped)?.let { return it }
        val groups = mapped.split(':').map { group -> group.takeIf { it.length in 1..4 }?.toIntOrNull(16) ?: return null }
        if (groups.size != 2) return null
        return (groups[0] shr 8) to (groups[0] and 0xFF)
    }
}
