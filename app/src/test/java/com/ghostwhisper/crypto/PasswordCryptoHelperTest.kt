package com.ghostwhisper.crypto

import org.junit.Assert.*
import org.junit.Test

class PasswordCryptoHelperTest {

    @Test
    fun testEncryptionAndDecryption() {
        val originalText = "Hello World! Secret Message 123"
        val password = "SuperSecretPassword!"

        val encrypted = PasswordCryptoHelper.encrypt(originalText, password)
        assertNotEquals(originalText, encrypted)

        val decrypted = PasswordCryptoHelper.decrypt(encrypted, password)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testDecryptionWithWrongPasswordFails() {
        val originalText = "Top Secret"
        val password = "CorrectPassword"
        val wrongPassword = "WrongPassword"

        val encrypted = PasswordCryptoHelper.encrypt(originalText, password)

        try {
            PasswordCryptoHelper.decrypt(encrypted, wrongPassword)
            fail("Should hav thrown an exception")
        } catch (e: Exception) {
            // Expected
        }
    }
}
