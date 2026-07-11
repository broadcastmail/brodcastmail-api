package com.broadcastemail.api.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilTest {
    @Test
    void encryptDecryptRoundTrip() {
        //Given
        String plainText = "test-api-key";
        String masterKey = "01234567890123456789012345678901";

        // When
        String encrypted = SecurityUtil.encrypt(plainText, masterKey);
        String decrypted = SecurityUtil.decrypt(encrypted, masterKey);

        // Then
        assertEquals(plainText, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertextEachTime() {
        // Given
        String plaintext = "same-input";
        String masterKey = "01234567890123456789012345678901";

        // When
        String first = SecurityUtil.encrypt(plaintext, masterKey);
        String second = SecurityUtil.encrypt(plaintext, masterKey);

        // Then
        assertNotEquals(first, second); // different IV each time
    }

    @Test
    void sha256IsDeterministic() {
        // Given
        String input = "bm_live_abc123";

        // When
        String first = SecurityUtil.sha256(input);
        String second = SecurityUtil.sha256(input);

        // Then
        assertEquals(first, second);
    }

    @Test
    void generateApiKeyHasCorrectPrefix() {
        // When
        String key = SecurityUtil.generateApiKey();

        // Then
        assertTrue(key.startsWith("bm_live_"));
    }
}