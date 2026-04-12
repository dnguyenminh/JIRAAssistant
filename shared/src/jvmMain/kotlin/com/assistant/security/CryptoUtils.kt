package com.assistant.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption utilities for encrypting sensitive data at rest.
 * Uses 12-byte random IV and 128-bit authentication tag.
 * Output format: Base64(IV + ciphertext + tag).
 */
object CryptoUtils {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    /**
     * Encrypt plaintext using AES-256-GCM.
     * @param plaintext the text to encrypt
     * @param key the encryption key (will be padded/truncated to 32 bytes for AES-256)
     * @return Base64-encoded string containing IV + ciphertext + auth tag
     */
    fun encryptAES256GCM(plaintext: String, key: String): String {
        val keyBytes = deriveKeyBytes(key)
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine IV + ciphertext (which includes the GCM auth tag)
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypt AES-256-GCM ciphertext.
     * @param ciphertext Base64-encoded string containing IV + ciphertext + auth tag
     * @param key the encryption key (same key used for encryption)
     * @return the decrypted plaintext
     * @throws javax.crypto.AEADBadTagException if the auth tag verification fails
     */
    fun decryptAES256GCM(ciphertext: String, key: String): String {
        val keyBytes = deriveKeyBytes(key)
        val secretKey = SecretKeySpec(keyBytes, ALGORITHM)

        val combined = Base64.getDecoder().decode(ciphertext)

        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val plainBytes = cipher.doFinal(encrypted)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Derive a 32-byte key from the input string.
     * Pads with zeros if shorter, truncates if longer.
     */
    private fun deriveKeyBytes(key: String): ByteArray {
        val raw = key.toByteArray(Charsets.UTF_8)
        val result = ByteArray(32) // 256 bits for AES-256
        System.arraycopy(raw, 0, result, 0, minOf(raw.size, 32))
        return result
    }
}
