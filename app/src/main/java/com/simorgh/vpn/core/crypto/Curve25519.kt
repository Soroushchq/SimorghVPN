package com.simorgh.vpn.core.crypto

import java.security.SecureRandom

/**
 * X25519 scalar multiplication (RFC 7748) — the minimal piece the WARP
 * Identity Vault needs to mint device keypairs locally.
 *
 * Field arithmetic follows the public-domain TweetNaCl structure
 * (16-limb GF(2^255-19), int64 limbs, carry-fold at 2^255) with the
 * Montgomery ladder spelled out exactly as in RFC 7748 §5.
 */
object Curve25519 {

    private const val BITS = 254 // ladder loop: 254..0
    private const val A24 = 121665L

    private class Gf {
        val v = LongArray(16)
        operator fun get(i: Int) = v[i]
        operator fun set(i: Int, x: Long) { v[i] = x }
        fun copy(): Gf = Gf().also { System.arraycopy(v, 0, it.v, 0, 16) }
    }

    // -------------------------------------------------------------- field ops

    private fun add(o: Gf, a: Gf, b: Gf) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: Gf, a: Gf, b: Gf) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: Gf, a: Gf, b: Gf) {
        val t = LongArray(31)
        for (i in 0 until 16) {
            val ai = a[i]
            for (j in 0 until 16) t[i + j] += ai * b[j]
        }
        for (i in 0 until 15) t[i] += 38L * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        carry(o)
        carry(o)
    }

    private fun sqr(o: Gf, a: Gf) = mul(o, a, a)

    private fun carry(o: Gf) {
        for (i in 0 until 16) {
            o[i] += (1L shl 16)
            val c = o[i] shr 16
            o[if (i < 15) i + 1 else 0] += c - 1L + 37L * (c - 1L) * (if (i == 15) 1 else 0)
            o[i] -= c shl 16
        }
    }

    /** Constant-time conditional swap: [b] == 1 → swap p and q. */
    private fun cswap(p: Gf, q: Gf, b: Int) {
        val mask = (b - 1L).inv() // b=1 → 0 (no swap); b=0 → all ones (swap)
        for (i in 0 until 16) {
            val t = mask and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun unpack(o: Gf, n: ByteArray) {
        for (i in 0 until 16) {
            o[i] = (n[2 * i].toLong() and 0xff) + ((n[2 * i + 1].toLong() and 0xff) shl 8)
        }
        o[15] = o[15] and 0x7fff
    }

    private fun pack(n: ByteArray, gf: Gf) {
        val t = gf.copy()
        carry(t); carry(t); carry(t)
        val m = Gf()
        for (j in 0 until 2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1L)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1L)
            val b = ((m[15] shr 16) and 1L).toInt()
            m[14] = m[14] and 0xffff
            cswap(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            n[2 * i] = (t[i] and 0xff).toByte()
            n[2 * i + 1] = ((t[i] shr 8) and 0xff).toByte()
        }
    }

    private fun powPm2(o: Gf, i: Gf) {
        val c = i.copy()
        for (a in 253 downTo 0) {
            sqr(c, c)
            if (a != 2 && a != 4) mul(c, c, i)
        }
        System.arraycopy(c.v, 0, o.v, 0, 16)
    }

    // -------------------------------------------------------------- ladder

    /** X25519(k, u) — both exactly 32 bytes, little-endian. */
    fun scalarMult(k: ByteArray, u: ByteArray): ByteArray {
        require(k.size == 32 && u.size == 32) { "x25519 inputs must be 32 bytes" }
        val kk = k.copyOf()
        // clamp the scalar (RFC 7748 §5)
        kk[0] = (kk[0].toInt() and 248).toByte()
        kk[31] = ((kk[31].toInt() and 127) or 64).toByte()

        val a24gf = Gf().also { it[0] = A24 }
        val x1 = Gf().also { unpack(it, u) }
        val x2 = Gf().also { it[0] = 1 }
        val z2 = Gf()
        val x3 = x1.copy()
        val z3 = Gf().also { it[0] = 1 }

        val a = Gf(); val b = Gf(); val c = Gf(); val d = Gf()
        val e = Gf(); val f = Gf(); val da = Gf(); val cb = Gf()
        val aa = Gf(); val bb = Gf(); val tmp = Gf()

        var swap = 0
        for (t in BITS downTo 0) {
            val kt = (kk[t ushr 3].toInt() shr (t and 7)) and 1
            swap = swap xor kt
            cswap(x2, x3, swap)
            cswap(z2, z3, swap)
            swap = kt

            add(a, x2, z2)          // A = x2 + z2
            sqr(aa, a)              // AA = A^2
            sub(b, x2, z2)          // B = x2 - z2
            sqr(bb, b)              // BB = B^2
            sub(e, aa, bb)          // E = AA - BB
            add(c, x3, z3)          // C = x3 + z3
            sub(d, x3, z3)          // D = x3 - z3
            mul(da, d, a)           // DA = D * A
            mul(cb, c, b)           // CB = C * B
            add(tmp, da, cb)
            sqr(x3, tmp)            // x3 = (DA + CB)^2
            sub(tmp, da, cb)
            sqr(tmp, tmp)
            mul(z3, x1, tmp)        // z3 = x1 * (DA - CB)^2
            mul(x2, aa, bb)         // x2 = AA * BB
            mul(f, e, a24gf)        // f = a24 * E
            add(tmp, aa, f)         // AA + a24*E
            mul(z2, e, tmp)         // z2 = E * (AA + a24*E)
        }
        cswap(x2, x3, swap)
        cswap(z2, z3, swap)

        powPm2(z2, z2)
        mul(x2, x2, z2)
        val out = ByteArray(32)
        pack(out, x2)
        return out
    }

    private val basePoint: ByteArray = ByteArray(32).also { it[0] = 9 }

    /** Public key for a 32-byte private key (clamping happens per scalarmult). */
    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, basePoint)

    /** Fresh random WireGuard-style private key (clamping happens per scalarmult). */
    fun generatePrivateKey(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(32).also { random.nextBytes(it) }
}
