package com.restaurant.workforce.invitation.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InvitationTokenCipher {

    private static final int NONCE_SIZE = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public InvitationTokenCipher(@Value("${invitation.token-encryption-secret}") String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("Invitation token encryption secret must contain at least 16 characters");
        }
        this.key = new SecretKeySpec(sha256(secret), "AES");
    }

    public String encrypt(String token) {
        byte[] nonce = new byte[NONCE_SIZE];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt invitation token", exception);
        }
    }

    public String decrypt(String encryptedToken) {
        byte[] value = Base64.getUrlDecoder().decode(encryptedToken);
        if (value.length <= NONCE_SIZE) {
            throw new IllegalStateException("Invalid encrypted invitation token");
        }
        byte[] nonce = java.util.Arrays.copyOfRange(value, 0, NONCE_SIZE);
        byte[] encrypted = java.util.Arrays.copyOfRange(value, NONCE_SIZE, value.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt invitation token", exception);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
