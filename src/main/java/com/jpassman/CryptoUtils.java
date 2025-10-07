package com.jpassman;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

public class CryptoUtils {
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    public static final int SALT_LENGTH_BYTES = 16;
    public static final int IV_LENGTH_BYTES = 12;
    public static final int TAG_LENGTH_BIT = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static SecretKey deriveKey(char[] masterPassword, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        KeySpec spec = new PBEKeySpec(masterPassword, salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ENCRYPTION_ALGORITHM);
    }

    public static byte[] encrypt(byte[] plaintext, SecretKey key) throws Exception {
        byte[] iv = generateRandomBytes(IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Combine IV and ciphertext: [IV (12 bytes)][Ciphertext]
        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);
        return encrypted;
    }

    public static byte[] decrypt(byte[] encryptedData, SecretKey key) throws Exception {
        if (encryptedData.length < IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Encrypted data is too short.");
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        System.arraycopy(encryptedData, 0, iv, 0, iv.length);
        
        int ciphertextLength = encryptedData.length - IV_LENGTH_BYTES;
        byte[] ciphertext = new byte[ciphertextLength];
        System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertextLength);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        return cipher.doFinal(ciphertext);
    }
}
