package com.simorgh.vpn.core.warp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simorgh.vpn.util.LogBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Identity Vault (msn-guard/Aether pattern): WARP device identities — the
 * private key above all — live only inside an AES-256/GCM-encrypted blob,
 * sealed by a non-exportable AndroidKeyStore key. The blob is a plain file
 * so both the UI process (writes) and :daemon (reads) can open it.
 */
object WarpIdentityVault {

    private const val ALIAS = "simorgh_warp_vault"
    private const val FILE = "warp_vault.bin"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val MAX_IDENTITIES = 3

    @Serializable
    private data class VaultData(
        val identities: List<WarpIdentity> = emptyList(),
        val activeId: String = ""
    )

    // ------------------------------------------------------------------ api

    /** All identities + which one is active (snapshot for the UI). */
    suspend fun snapshot(context: Context): Pair<List<WarpIdentity>, WarpIdentity?> =
        withContext(Dispatchers.IO) {
            val data = read(context)
            val active = data.identities.firstOrNull { it.id == data.activeId }
                ?: data.identities.firstOrNull()
            data.identities to active
        }

    /** The identity the daemon should dial with, or null when none exists. */
    suspend fun active(context: Context): WarpIdentity? = snapshot(context).second

    /**
     * Registers a brand-new WARP device and makes it the active identity.
     * Keeps at most [MAX_IDENTITIES] identities (oldest drop off).
     */
    suspend fun register(context: Context, socksPort: Int): Result<WarpIdentity> {
        val identity = WarpApi.registerNew(socksPort).getOrElse { return Result.failure(it) }
        withContext(Dispatchers.IO) {
            val data = read(context)
            val kept = (listOf(identity) + data.identities.filter { it.id != identity.id })
                .take(MAX_IDENTITIES)
            write(context, VaultData(identities = kept, activeId = identity.id))
        }
        LogBuffer.i("Warp", "identity minted: ${identity.shortId} (${identity.v4})")
        return Result.success(identity)
    }

    suspend fun setActive(context: Context, id: String) = withContext(Dispatchers.IO) {
        val data = read(context)
        if (data.identities.any { it.id == id }) write(context, data.copy(activeId = id))
    }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        val data = read(context)
        val remaining = data.identities.filter { it.id != id }
        val activeId = when {
            data.activeId == id -> remaining.firstOrNull()?.id ?: ""
            else -> data.activeId
        }
        write(context, VaultData(identities = remaining, activeId = activeId))
    }

    // ------------------------------------------------------------------ crypto file

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE)

    private fun read(context: Context): VaultData {
        val f = file(context)
        if (!f.exists()) return VaultData()
        return runCatching {
            val blob = f.readBytes()
            require(blob.size > IV_LEN) { "vault blob too short" }
            val iv = blob.copyOfRange(0, IV_LEN)
            val ct = blob.copyOfRange(IV_LEN, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(ct)
            Json.decodeFromString<VaultData>(plain.toString(Charsets.UTF_8))
        }.getOrElse {
            // unreadable vault (key rotated away / corrupted) → start clean
            LogBuffer.w("Warp", "vault unreadable — resetting (${it.message})")
            runCatching { f.delete() }
            VaultData()
        }
    }

    private fun write(context: Context, data: VaultData) {
        val f = file(context)
        // default randomized-encryption mode: the keystore mints a fresh IV
        // per seal and exposes it through cipher.iv — stored alongside.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv.copyOf()
        val ct = cipher.doFinal(Json.encodeToString(data).toByteArray(Charsets.UTF_8))
        // atomic tmp+rename: daemon may be reading concurrently
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeBytes(iv + ct)
        if (!tmp.renameTo(f)) {
            f.delete()
            check(tmp.renameTo(f)) { "cannot persist warp vault" }
        }
    }

    /** Non-exportable AES-256 key, minted on first use, hardware-backed when available. */
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Debug helper (never logs key material). */
    fun describe(identities: List<WarpIdentity>): String =
        identities.joinToString(", ") { "${it.shortId}=${it.v4}${if (it.v6.isNotBlank()) "/${it.v6}" else ""}" }

    /** Unused-but-handy for future backup tooling: base64 of the raw blob. */
    fun rawBlobBase64(context: Context): String? = runCatching {
        val f = file(context)
        if (f.exists()) Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) else null
    }.getOrNull()
}
