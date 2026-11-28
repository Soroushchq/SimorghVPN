package com.simorgh.vpn.core.warp

import com.simorgh.vpn.core.XrayConfigBuilder
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
 * Xray config for the WARP engine: the same battle-tested chassis as
 * [XrayConfigBuilder] (socks inbound + stats + Iran-aware DNS/routing) but
 * the proxy outbound is Xray's native `wireguard` protocol carrying the
 * Identity Vault's device — a free, serverless Cloudflare tunnel.
 */
object WarpConfigBuilder {

    fun build(identity: WarpIdentity, s: SimorghSettings, logPath: String): String {
        val ip = s.warpIpOverride.ifBlank { WarpEndpoints.anycastIps().first() }
        val port = if (s.warpPortOverride in 1..65535) s.warpPortOverride
        else if (s.warpPort in 1..65535) s.warpPort else WarpEndpoints.DEFAULT_PORT

        val inbounds = mutableListOf<JsonObject>()
        inbounds.add(XrayConfigBuilder.socksInbound(s))
        if (s.tunnelMode == TunnelMode.XRAY_TUN) inbounds.add(XrayConfigBuilder.tunInbound(s))

        val outbounds = mutableListOf<JsonObject>()
        outbounds.add(wireguardOutbound(identity, ip, port))
        outbounds.add(XrayConfigBuilder.directOutbound())
        outbounds.add(XrayConfigBuilder.dnsOutbound())
        outbounds.add(XrayConfigBuilder.blockOutbound())

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
                put("rules", JsonArray(routingRules(s)))
            }
            put("dns", XrayConfigBuilder.dnsSection(s))
        }.toString()
    }

    // ------------------------------------------------------------------ wireguard outbound

    private fun wireguardOutbound(identity: WarpIdentity, ip: String, port: Int): JsonObject =
        buildJsonObject {
            put("tag", "proxy") // stats + routing rules all speak "proxy"
            put("protocol", "wireguard")
            putJsonObject("settings") {
                put("secretKey", identity.privateKey)
                putJsonArray("address") {
                    add(JsonPrimitive("${identity.v4}/32"))
                    if (identity.v6.isNotBlank()) add(JsonPrimitive("${identity.v6}/128"))
                }
                putJsonArray("peers") {
                    add(buildJsonObject {
                        put("publicKey", WarpEndpoints.PEER_PUBLIC_KEY)
                        put("endpoint", "$ip:$port")
                        put("keepAlive", 10)
                    })
                }
                if (identity.reserved.isNotEmpty()) {
                    put("reserved", JsonArray(identity.reserved.map { JsonPrimitive(it) }))
                }
                put("mtu", WarpEndpoints.WG_MTU)
            }
        }

    // ------------------------------------------------------------------ routing

    private fun rule(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "field")
            block()
        }

    /**
     * XrayConfigBuilder's rule set + WARP-specific additions:
     *  - Cloudflare client API / engage hostnames never enter the tunnel
     *    (they ARE the tunnel; also keeps re-registration working while up)
     */
    private fun routingRules(s: SimorghSettings): List<JsonObject> = buildList {
        add(rule {
            put("domain", JsonArray(listOf(
                JsonPrimitive("domain:cloudflareclient.com"),
                JsonPrimitive("domain:engage.cloudflareclient.com"),
                JsonPrimitive("domain:api.cloudflare.com")
            )))
            put("outboundTag", "direct")
        })
        addAll(XrayConfigBuilder.routingRules(s))
    }
}
