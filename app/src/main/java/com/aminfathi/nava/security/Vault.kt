package com.aminfathi.nava.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Vault(private val context: Context) {
    private val preferences = context.getSharedPreferences("sealed", Context.MODE_PRIVATE)
    private val alias = "nava.aes.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        check(preferences.all.isEmpty()) { "Key unavailable; encrypted data must not be reset silently" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun put(name: String, bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()); updateAAD(name.toByteArray()) }
        val sealed = cipher.iv + cipher.doFinal(bytes)
        check(preferences.edit().putString(name,Base64.encodeToString(sealed,Base64.NO_WRAP)).commit())
    }
    @Synchronized fun get(name: String): ByteArray? {
        val encoded = preferences.getString(name,null) ?: return null
        val bytes = Base64.decode(encoded,Base64.NO_WRAP)
        require(bytes.size >= 28)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            updateAAD(name.toByteArray()); doFinal(bytes.copyOfRange(12,bytes.size))
        }
    }
    fun remove(name: String) { check(preferences.edit().remove(name).commit()) }
    @Synchronized fun sealTo(file: java.io.File, aad: String, bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()); updateAAD(aad.toByteArray()) }
        file.parentFile?.mkdirs()
        file.writeBytes(cipher.iv + cipher.doFinal(bytes))
    }
    @Synchronized fun openFrom(file: java.io.File, aad: String): ByteArray {
        val bytes = file.readBytes()
        require(bytes.size >= 28)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            updateAAD(aad.toByteArray()); doFinal(bytes.copyOfRange(12,bytes.size))
        }
    }
    fun databaseKey(): ByteArray = get("database") ?: run {
        check(!context.getDatabasePath("clinical.db").exists()) { "Database key missing" }
        ByteArray(32).also { SecureRandom().nextBytes(it); put("database",it) }
    }
    fun apiKey(): String = get("api")?.toString(Charsets.UTF_8).orEmpty()
    fun saveApiKey(value: String) { require(value.length in 10..1000 && !value.contains('\n')); put("api",value.trim().toByteArray()) }
}
