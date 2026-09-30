package com.muvusoft.agentfarm.net

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * One P-256 key per pairing, kept in the Android Keystore: the private half never leaves it.
 * The farm stores the public half at pairing and verifies every hello's signature against it.
 */
object DeviceKeys {
    private const val STORE = "AndroidKeyStore"

    /** A new alias for a pairing with `farmId`; unique so a re-pairing never overwrites a working key. */
    fun newAlias(farmId: String, nowMs: Long): String = "af.device.$farmId.${nowMs.toString(36)}"

    /** Creates the key under `alias`; returns the SPKI DER public key, base64 (the contract's publicKey). */
    fun create(alias: String): String {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE)
        gen.initialize(spec)
        return b64(gen.generateKeyPair().public.encoded)
    }

    fun exists(alias: String): Boolean = store().containsAlias(alias)

    /** SHA256withECDSA over the UTF-8 text, DER-encoded, base64: what a hello's `signature` carries. */
    fun sign(alias: String, text: String): String {
        val key = store().getKey(alias, null) as? PrivateKey ?: error("no device key $alias")
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(key)
        sig.update(text.toByteArray(Charsets.UTF_8))
        return b64(sig.sign())
    }

    fun delete(alias: String) {
        val ks = store()
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    private fun store(): KeyStore = KeyStore.getInstance(STORE).apply { load(null) }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
