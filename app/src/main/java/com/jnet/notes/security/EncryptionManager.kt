
package com.jnet.notes.security

import android.util.Log

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
private const val TAG = "JNetNotes"

object EncryptionManager {
    private const val AES_KEY_SIZE = 256
    private const val IV_SIZE = 12 // GCM standard
    private const val SALT_SIZE = 16
    private const val ITERATIONS = 10000

    // Hash password for authentication (stores hash, not password)
    fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        return salt
    }

    // Derive a key from the password to encrypt/decrypt notes
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plaintext: String, password: String, salt: ByteArray): String {
        try {
            if (plaintext.isBlank()) {
                Log.w(TAG, "E008: encrypt called with empty plaintext")
                return ""
            }
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(128, iv)

            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Combine IV and Ciphertext for storage: [IV(12b)][Ciphertext]
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

            val result = Base64.getEncoder().encodeToString(combined)
            Log.d(TAG, "Encrypt: ${plaintext.length} chars -> ${result.length} chars base64")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "E008: Encryption failed", e)
            throw Exception("E008: Encryption failed - ${e.message}")
        }
    }

    fun decrypt(encryptedBase64: String, password: String, salt: ByteArray): String {
        try {
            if (encryptedBase64.isBlank()) {
                throw IllegalArgumentException("Empty ciphertext")
            }
            val combined = Base64.getDecoder().decode(encryptedBase64)
            if (combined.size < 13) {
                throw IllegalArgumentException("Data too short: ${combined.size} bytes")
            }
            val key = deriveKey(password, salt)

            val iv = ByteArray(IV_SIZE)
            System.arraycopy(combined, 0, iv, 0, IV_SIZE)

            val ciphertext = ByteArray(combined.size - IV_SIZE)
            System.arraycopy(combined, IV_SIZE, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val result = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            Log.d(TAG, "Decrypt: ${combined.size} bytes -> ${result.length} chars")
            return result
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "E009: BAD_DECRYPT - wrong password or corrupted data", e)
            throw Exception("E009: BAD_DECRYPT - Wrong password or corrupted data")
        } catch (e: Exception) {
            Log.e(TAG, "E009: Decryption failed", e)
            throw Exception("E009: ${e.message}")
        }
    }
}
