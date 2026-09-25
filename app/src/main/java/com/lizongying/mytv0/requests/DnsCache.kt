package com.lizongying.mytv0.requests

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DnsCache : Dns {
    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAt: Long
    )

    private val dnsCache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMillis = 10 * 60 * 1000L // 10分钟 TTL，避免 CDN/直播源 IP 变更后死锁

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isEmpty()) {
            return Dns.SYSTEM.lookup(hostname)
        }

        val now = System.currentTimeMillis()
        val entry = dnsCache[hostname]
        if (entry != null && now < entry.expiresAt) {
            return entry.addresses
        }

        val ipv4Addresses = mutableListOf<InetAddress>()
        val ipv6Addresses = mutableListOf<InetAddress>()

        try {
            for (address in InetAddress.getAllByName(hostname)) {
                if (address is Inet4Address) {
                    ipv4Addresses.add(address)
                } else if (address is Inet6Address) {
                    ipv6Addresses.add(address)
                }
            }
        } catch (e: Exception) {
            // 如果解析失败但有过期缓存，降级使用旧缓存
            if (entry != null) {
                return entry.addresses
            }
            throw e
        }

        // IPv4 优先排在前面，提升大部分普通家庭网络连接的成功率与响应速度
        val addressesNew = ipv4Addresses + ipv6Addresses

        if (addressesNew.isNotEmpty()) {
            dnsCache[hostname] = CacheEntry(addressesNew, now + ttlMillis)
        }

        return addressesNew
    }
}