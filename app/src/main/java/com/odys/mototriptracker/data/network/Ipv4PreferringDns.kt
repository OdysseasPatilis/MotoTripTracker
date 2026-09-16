package com.odys.mototriptracker.data.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/** Prefer IPv4 so emulators / broken IPv6 routes don't hang on AAAA records. */
object Ipv4PreferringDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        val ipv4 = all.filterIsInstance<Inet4Address>()
        return if (ipv4.isNotEmpty()) ipv4 else all
    }
}
