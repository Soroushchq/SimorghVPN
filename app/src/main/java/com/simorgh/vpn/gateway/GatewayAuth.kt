package com.simorgh.vpn.gateway

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Credentials for the sharing gateway (nova pattern): a short username and
 * password generated on this phone, shown in the app and on the portal page
 * (portal page only to clients that connect THROUGH the sharing interface).
 */
object GatewayAuth {

    private val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray()
    private val rng = SecureRandom()

    /** Short human-typable token (no confusing 0/O/1/I/l). */
    fun generateToken(length: Int = 8): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(ALPHABET[rng.nextInt(ALPHABET.size)]) }
        return sb.toString()
    }

    /** Fresh pair for a first-time enable. */
    fun newCredentials(): Pair<String, String> = generateToken(8) to generateToken(10)

    /** Constant-time comparison (via SHA-256 digests) of provided credentials. */
    fun check(expectedUser: String, expectedPass: String, user: String, pass: String): Boolean {
        if (expectedUser.isBlank() || expectedPass.isBlank()) return false
        val a = sha256("$expectedUser:$expectedPass")
        val b = sha256("$user:$pass")
        return MessageDigest.isEqual(a, b)
    }

    private fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
}
