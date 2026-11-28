package com.simorgh.vpn.core

import com.simorgh.vpn.core.anticensor.TlsFragmentProfile
import com.simorgh.vpn.core.mae.MaeRules
import com.simorgh.vpn.data.Profile
import com.simorgh.vpn.data.RoutingMode
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.data.TunnelMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the full Xray JSON config from a profile + app settings.
 * Structure mirrors the battle-tested v2rayNG/Derafsh template
 * (socks inbound + policy level 8 stats + Iran-aware DNS/routing).
 */
object XrayConfigBuilder {

    fun build(
        profile: Profile,
        s: SimorghSettings,
        logPath: String,
        maeRoutes: List<MaeRules.ServiceRoute> = emptyList()
    ): String {
        val inbounds = mutableListOf<JsonObject>()
        inbounds.add(socksInbound(s))
        if (s.tunnelMode == TunnelMode.XRAY_TUN) inbounds.add(tunInbound(s))

        val outbounds = mutableListOf<JsonObject>()
        outbounds.add(proxyOutbound(profile, s))
        outbounds.add(directOutbound())
        outbounds.add(dnsOutbound())
        outbounds.add(blockOutbound())

        return buildJsonObject {
            put("stats", buildJsonObject {})
            putJsonObject("log") {
                put("loglevel", "warning")
                put("error", logPath)
            }
            putJsonObject("policy") {
                putJsonObject("levels") {
                    putJsonObject("8") {
                        put("handshake", 4)
                        put("connIdle", 300)
                        put("uplinkOnly", 1)
                        put("downlinkOnly", 1)
                    }
                }
                putJsonObject("system") {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            put("inbounds", JsonArray(inbounds))
            put("outbounds", JsonArray(outbounds))
            putJsonObject("routing") {
                put("domainStrategy", "AsIs")
                put("rules", JsonArray(routingRules(s, maeRoutes)))
            }
            put("dns", dnsSection(s))
        }.toString()
    }

    /** Minimal config for latency measurement (no inbounds, no routing). */
    fun buildTestConfig(profile: Profile, s: SimorghSettings): String = buildJsonObject {
        putJsonObject("log") { put("loglevel", "warning") }
        putJsonArray("outbounds") {
            add(proxyOutbound(profile, s))
            add(directOutbound())
        }
    }.toString()

    // ------------------------------------------------------------------ inbounds

    internal fun sniffing() = buildJsonObject {
        put("enabled", true)
        put("destOverride", JsonArray(listOf("http", "tls", "quic").map { JsonPrimitive(it) }))
    }

    internal fun socksInbound(s: SimorghSettings) = buildJsonObject {
        put("tag", "socks")
        put("listen", "127.0.0.1")
        put("port", s.socksPort)
        put("protocol", "socks")
        putJsonObject("settings") {
            put("auth", "noauth")
            put("udp", true)
            put("userLevel", 8)
        }
        put("sniffing", sniffing())
    }

    internal fun tunInbound(s: SimorghSettings) = buildJsonObject {
        put("tag", "tun")
        put("protocol", "tun")
        putJsonObject("settings") {
            put("name", "xray0")
            put("MTU", s.mtu)
            put("userLevel", 8)
        }
        put("sniffing", sniffing())
    }

    // ------------------------------------------------------------------ outbounds

    private fun proxyOutbound(p: Profile, s: SimorghSettings): JsonObject = buildJsonObject {
        put("tag", "proxy")
        put("mux", buildJsonObject { put("enabled", false) })
        when (p.protocol) {
            "vless" -> {
                put("protocol", "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(buildJsonObject {
                            put("address", p.host)
                            put("port", p.port)
                            putJsonArray("users") {
                                add(buildJsonObject {
                                    put("id", p.secret)
                                    put("encryption", p.encryption.ifBlank { "none" })
                                    if (p.flow.isNotBlank()) put("flow", p.flow)
                                    put("level", 8)
                                })
                            }
                        })
                    }
                }
            }
            "vmess" -> {
                put("protocol", "vmess")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        add(buildJsonObject {
                            put("address", p.host)
                            put("port", p.port)
                            putJsonArray("users") {
                                add(buildJsonObject {
                                    put("id", p.secret)
                                    put("security", p.encryption.ifBlank { "auto" })
                                    put("alterId", 0)
                                    put("level", 8)
                                })
                            }
                        })
                    }
                }
            }
            "trojan" -> {
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.host)
                            put("port", p.port)
                            put("password", p.secret)
                            put("level", 8)
                        })
                    }
                }
            }
            "shadowsocks" -> {
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", p.host)
                            put("port", p.port)
                            put("method", p.encryption)
                            put("password", p.secret)
                            put("level", 8)
                        })
                    }
                }
            }
            else -> put("protocol", "freedom")
        }
        put("streamSettings", streamSettings(p, s))
    }

    private fun streamSettings(p: Profile, s: SimorghSettings) = buildJsonObject {
        val net = p.network.ifBlank { "tcp" }
        put("network", net)
        val security = when (p.security) {
            "reality" -> "reality"
            "tls" -> "tls"
            else -> "none"
        }
        put("security", security)

        // v0.3 anti-censor: per-attempt uTLS override (FingerprintRotator)
        val fp = s.fingerprintOverride.trim().ifBlank { p.fingerprint }

        if (security == "tls") {
            putJsonObject("tlsSettings") {
                put("serverName", p.sni.ifBlank { p.host })
                put("allowInsecure", p.allowInsecure || s.allowInsecure)
                if (fp.isNotBlank()) put("fingerprint", fp)
                if (p.alpn.isNotBlank()) {
                    put("alpn", JsonArray(p.alpn.split(',').map { JsonPrimitive(it.trim()) }))
                }
            }
        }
        if (security == "reality") {
            putJsonObject("realitySettings") {
                put("serverName", p.sni.ifBlank { p.host })
                put("fingerprint", fp.ifBlank { "chrome" })
                if (p.publicKey.isNotBlank()) put("publicKey", p.publicKey)
                if (p.shortId.isNotBlank()) put("shortId", p.shortId)
                put("spiderX", "/")
            }
        }

        // v0.3 anti-censor: TLS ClientHello fragmentation (msn-guard pattern)
        // via the core's native sockopt.fragment — applies to every TLS/REALITY
        // handshake dialed by this outbound.
        if (s.tlsFragment && security != "none") {
            val profile = TlsFragmentProfile.of(s.tlsFragmentProfile)
            putJsonObject("sockopt") {
                putJsonObject("fragment") {
                    put("packets", "tlshello")
                    put("length", profile.lengthSpec)
                    put("interval", profile.intervalSpec)
                }
            }
        }

        when (net) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", p.path.ifBlank { "/" })
                if (p.hostHeader.isNotBlank()) {
                    putJsonObject("headers") { put("Host", p.hostHeader) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", p.serviceName.ifBlank { p.path })
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                put("path", p.path.ifBlank { "/" })
                if (p.hostHeader.isNotBlank()) put("host", p.hostHeader)
            }
            "xhttp" -> putJsonObject("xhttpSettings") {
                put("path", p.path.ifBlank { "/" })
                if (p.hostHeader.isNotBlank()) put("host", p.hostHeader)
            }
            "tcp" -> if (p.hostHeader.isNotBlank()) {
                putJsonObject("tcpSettings") {
                    putJsonObject("header") {
                        put("type", "http")
                        putJsonObject("request") {
                            put("path", JsonArray(listOf(JsonPrimitive(p.path.ifBlank { "/" }))))
                            putJsonObject("headers") {
                                put("Host", JsonArray(listOf(JsonPrimitive(p.hostHeader))))
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun directOutbound() = buildJsonObject {
        put("protocol", "freedom")
        put("tag", "direct")
        putJsonObject("streamSettings") {
            putJsonObject("sockopt") { put("domainStrategy", "UseIP") }
        }
    }

    internal fun dnsOutbound() = buildJsonObject {
        put("protocol", "dns")
        put("tag", "dns-out")
    }

    internal fun blockOutbound() = buildJsonObject {
        put("protocol", "blackhole")
        put("tag", "block")
    }

    // ------------------------------------------------------------------ routing + dns

    private fun rule(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "field")
            block()
        }

    internal fun routingRules(
        s: SimorghSettings,
        maeRoutes: List<MaeRules.ServiceRoute> = emptyList()
    ): List<JsonObject> = buildList {
        // DNS server traffic: Iranian domains direct, the rest through proxy
        add(rule {
            put("inboundTag", JsonArray(listOf(JsonPrimitive("dns-ir"))))
            put("outboundTag", "direct")
        })
        add(rule {
            put("inboundTag", JsonArray(listOf(JsonPrimitive("dns-remote"))))
            put("outboundTag", "proxy")
        })
        // Hijack app DNS (port 53) into the Xray DNS module
        add(rule {
            put("port", "53")
            put("outboundTag", "dns-out")
        })
        // v0.4 MAE: per-service adaptive decisions beat the generic geo lists
        // (mlmvpn RouteProvider order — most specific rule first)
        if (s.maeEnabled) {
            maeRoutes.forEach { r ->
                add(rule {
                    put("domain", JsonArray(r.domains.map { JsonPrimitive(it) }))
                    put("outboundTag", r.outboundTag)
                })
            }
        }
        if (s.routingMode == RoutingMode.IR_BYPASS) {
            add(rule {
                put("ip", JsonArray(listOf(JsonPrimitive("geoip:private"), JsonPrimitive("geoip:ir"))))
                put("outboundTag", "direct")
            })
            add(rule {
                put("domain", JsonArray(listOf(JsonPrimitive("geosite:category-ir"), JsonPrimitive("domain:ir"))))
                put("outboundTag", "direct")
            })
        } else {
            add(rule {
                put("ip", JsonArray(listOf(JsonPrimitive("geoip:private"))))
                put("outboundTag", "direct")
            })
        }
    }

    internal fun dnsSection(s: SimorghSettings) = buildJsonObject {
        putJsonObject("hosts") {
            put("dns.google", JsonArray(listOf(JsonPrimitive("8.8.8.8"), JsonPrimitive("8.8.4.4"))))
            put("dns.quad9.net", JsonArray(listOf(JsonPrimitive("9.9.9.9"), JsonPrimitive("149.112.112.112"))))
            put("cloudflare-dns.com", JsonArray(listOf(JsonPrimitive("1.1.1.1"), JsonPrimitive("1.0.0.1"))))
            put("one.one.one.one", JsonArray(listOf(JsonPrimitive("1.1.1.1"), JsonPrimitive("1.0.0.1"))))
        }
        put("servers", JsonArray(buildList {
            if (s.routingMode == RoutingMode.IR_BYPASS) {
                add(buildJsonObject {
                    put("address", s.domesticDns)
                    put("domains", JsonArray(listOf(JsonPrimitive("geosite:category-ir"), JsonPrimitive("domain:ir"))))
                    put("expectIPs", JsonArray(listOf(JsonPrimitive("geoip:ir"))))
                    put("skipFallback", true)
                    put("tag", "dns-ir")
                })
            }
            add(buildJsonObject {
                put("address", s.remoteDns)
                put("tag", "dns-remote")
            })
        }))
        put("tag", "dns-in")
        put("queryStrategy", "UseIPv4")
    }
}
