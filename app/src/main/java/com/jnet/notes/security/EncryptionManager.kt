package com.jnet.notes.security

import java.security.SecureRandom
import java.security.spec.PKCS5Spec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
        
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedBase64: String, password: String, salt: ByteArray): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        val key = deriveKey(password, salt)
        
        val iv = ByteArray(IV_SIZE)
        System.arraycopy(combined, 0, iv, 0, IV_SIZE)
        
        val ciphertext = ByteArray(combined.size - IV_SIZE)
        System.arraycopy(combined, IV_SIZE, ciphertext, 0, ciphertext.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
