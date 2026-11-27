package com.example.mesh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class IrohKeyStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "iroh-identity"))

    fun loadOrCreate(): ByteArray {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (file.baseFile.exists()) {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: error("The iroh identity encryption key is missing")
            val encrypted = file.readFully()
            check(encrypted.size == IV_BYTES + KEY_BYTES + TAG_BITS / 8) { "Invalid encrypted iroh identity" }
            return Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, encrypted, 0, IV_BYTES))
                doFinal(encrypted, IV_BYTES, encrypted.size - IV_BYTES)
            }
        }
        val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
            }.generateKey()
        val identity = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        check(cipher.iv.size == IV_BYTES)
        val output = file.startWrite()
        try {
            output.write(cipher.iv)
            output.write(cipher.doFinal(identity))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            identity.fill(0)
            throw error
        }
        return identity
    }

    private companion object {
        const val KEY_ALIAS = "homelist-iroh-identity"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
