package com.example.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;

import java.nio.charset.StandardCharsets;

public class EncryptionTests {

    @Test
    public void testBCrypt() {
        // BCrypt.gensalt() has 10 log_rounds as default (reasonable, tolerable, performance-wise)
        System.out.println(BCrypt.hashpw("hello", BCrypt.gensalt()));
        Assertions.assertTrue(BCrypt.checkpw("hello", "$2a$12$qevbPfTUlQuWEONbtCGB5elNcsV3LvQdihhGAXUfwORAdKYZ6zkGK"));
    }

    @Test
    public void testBCryptPasswordEncoder() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        System.out.println(encoder.encode("hello world"));
        Assertions.assertTrue(encoder.matches("hello world", "$2a$12$zv35Nvg5UNK9FT.jMjtIGu/BEaA1ZCNgJAZVEJDcE29/ppgAtoaa."));
    }

    @Test
    public void testKeyGenerator() {
        StringKeyGenerator generator = KeyGenerators.string();
        String key = generator.generateKey();
        System.out.println(key);
        // 8 random bytes, hex-encoded - usable as the salt for password-based encryptors
        Assertions.assertTrue(key.matches("[0-9a-f]{16}"));
    }

    @Test
    public void testEncryptor() {
        // AES-256/GCM with a PBKDF2-derived key; the salt is hex-encoded
        BytesEncryptor encryptor = AesGcmBytesEncryptor.withPassword("password", "f25e5ba1a7d42dc1").build();
        String plainText = "hello world";
        String encryptedText = new String(Hex.encode(encryptor.encrypt(plainText.getBytes(StandardCharsets.UTF_8))));
        System.out.println(encryptedText);
        Assertions.assertEquals(plainText, new String(encryptor.decrypt(Hex.decode(encryptedText)), StandardCharsets.UTF_8));
        // A random IV per call: the same plaintext never produces the same ciphertext
        Assertions.assertNotEquals(encryptedText, new String(Hex.encode(encryptor.encrypt(plainText.getBytes(StandardCharsets.UTF_8)))));
    }

}
