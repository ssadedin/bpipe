package bpipe

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security

import org.bouncycastle.util.io.pem.*

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * HPKE-based secret exchange using BouncyCastle library.
 * Uses sensible defaults: mode_base, X25519, HKDF-SHA256, AES-128-GCM
 */
class HPKESecretExchange {
    
    // HPKE Configuration - sensible defaults
    private static final int HPKE_MODE_BASE = 0x00
    private static final String KEM_ALGORITHM = "X25519"
    private static final String KDF_ALGORITHM = "HKDF-SHA256"
    private static final String AEAD_ALGORITHM = "AES-128-GCM"
    private static final int GCM_TAG_LENGTH = 128
    private static final int GCM_IV_LENGTH = 12
    
    private final PublicKey receiverPublicKey
    private final PrivateKey receiverPrivateKey
    private final SecureRandom secureRandom
    
    static {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider())
        }
    }
    
    /**
     * Constructor for encryption only
     * @param receiverPublicKey The public key of the intended receiver (X25519 public key)
     */
    HPKESecretExchange(PublicKey receiverPublicKey) {
        if (receiverPublicKey == null) {
            throw new IllegalArgumentException("Receiver public key cannot be null")
        }
        this.receiverPublicKey = receiverPublicKey
        this.receiverPrivateKey = null
        this.secureRandom = new SecureRandom()
    }
    
    /**
     * Constructor for encryption and decryption
     * @param receiverPublicKey The public key of the intended receiver (X25519 public key)
     * @param receiverPrivateKey The private key of the receiver for decryption
     */
    HPKESecretExchange(PublicKey receiverPublicKey, PrivateKey receiverPrivateKey) {
        if (receiverPublicKey == null) {
            throw new IllegalArgumentException("Receiver public key cannot be null")
        }
        this.receiverPublicKey = receiverPublicKey
        this.receiverPrivateKey = receiverPrivateKey
        this.secureRandom = new SecureRandom()
    }
    
    /**
     * Constructor for decryption only
     * @param receiverPrivateKey The private key of the receiver for decryption
     */
    HPKESecretExchange(PrivateKey receiverPrivateKey) {
        if (receiverPrivateKey == null) {
            throw new IllegalArgumentException("Receiver private key cannot be null")
        }
        this.receiverPublicKey = null
        this.receiverPrivateKey = receiverPrivateKey
        this.secureRandom = new SecureRandom()
    }
    
    /**
     * Encapsulates a secret for the receiver using HPKE mode_base
     * @param secret The secret bytes to encrypt
     * @return Map containing base64-encoded ephemeralPublicKey and encryptedSecret
     */
    Map<String, String> encapsulate(byte[] secret) {
        if (receiverPublicKey == null) {
            throw new IllegalStateException("Receiver public key is required for encryption")
        }
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("Secret cannot be null or empty")
        }
        
        try {
            // Generate ephemeral key pair
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(KEM_ALGORITHM, "BC")
            keyPairGen.initialize(255, secureRandom) // X25519 uses 255-bit keys
            KeyPair ephemeralKeyPair = keyPairGen.generateKeyPair()
            
            // Perform ECDH key agreement
            byte[] sharedSecret = performKeyAgreement(
                ephemeralKeyPair.getPrivate(),
                receiverPublicKey
            )
            
            // Derive encryption key using HKDF
            byte[] encryptionKey = deriveKey(sharedSecret, 16) // 128-bit key for AES-128
            
            // Encrypt the secret using AES-GCM
            byte[] iv = new byte[GCM_IV_LENGTH]
            secureRandom.nextBytes(iv)
            
            byte[] encryptedSecret = encryptAESGCM(secret, encryptionKey, iv)
            
            // Combine IV and ciphertext
            byte[] combined = new byte[iv.length + encryptedSecret.length]
            System.arraycopy(iv, 0, combined, 0, iv.length)
            System.arraycopy(encryptedSecret, 0, combined, iv.length, encryptedSecret.length)
            
            // Get ephemeral public key bytes
            byte[] ephemeralPublicKeyBytes = ephemeralKeyPair.getPublic().getEncoded()
            
            // Return base64-encoded values
            return [
                ephemeralPublicKey: Base64.getEncoder().encodeToString(ephemeralPublicKeyBytes),
                encryptedSecret: Base64.getEncoder().encodeToString(combined)
            ]
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to encapsulate secret: ${e.message}", e)
        }
    }
    
    /**
     * Decapsulates an encrypted secret using the receiver's private key
     * @param encapsulatedData Map containing base64-encoded ephemeralPublicKey and encryptedSecret
     * @return The decrypted secret as a byte array
     */
    byte[] decapsulate(Map<String, String> encapsulatedData) {
        if (receiverPrivateKey == null) {
            throw new IllegalStateException("Receiver private key is required for decryption")
        }
        if (encapsulatedData == null) {
            throw new IllegalArgumentException("Encapsulated data cannot be null")
        }
        if (!encapsulatedData.containsKey("ephemeralPublicKey") ||
            !encapsulatedData.containsKey("encryptedSecret")) {
            throw new IllegalArgumentException("Encapsulated data must contain 'ephemeralPublicKey' and 'encryptedSecret'")
        }
        
        try {
            // Decode base64 values
            byte[] ephemeralPublicKeyBytes = Base64.getDecoder().decode(encapsulatedData.ephemeralPublicKey)
            byte[] combined = Base64.getDecoder().decode(encapsulatedData.encryptedSecret)
            
            // Extract IV and ciphertext
            if (combined.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted data is too short")
            }
            byte[] iv = new byte[GCM_IV_LENGTH]
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH]
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length)
            
            // Reconstruct ephemeral public key
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance(KEM_ALGORITHM, "BC")
            java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(ephemeralPublicKeyBytes)
            PublicKey ephemeralPublicKey = keyFactory.generatePublic(keySpec)
            
            // Perform ECDH key agreement with ephemeral public key and receiver's private key
            byte[] sharedSecret = performKeyAgreement(receiverPrivateKey, ephemeralPublicKey)
            
            // Derive decryption key using same HKDF process
            byte[] decryptionKey = deriveKey(sharedSecret, 16)
            
            // Decrypt the secret using AES-GCM
            byte[] decryptedSecret = decryptAESGCM(ciphertext, decryptionKey, iv)
            
            return decryptedSecret
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to decapsulate secret: ${e.message}", e)
        }
    }
    
    /**
     * Performs X25519 ECDH key agreement
     */
    private byte[] performKeyAgreement(java.security.PrivateKey privateKey, PublicKey publicKey) {
        try {
            javax.crypto.KeyAgreement keyAgreement = javax.crypto.KeyAgreement.getInstance("X25519", "BC")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)
            return keyAgreement.generateSecret()
        } catch (Exception e) {
            throw new RuntimeException("Key agreement failed: ${e.message}", e)
        }
    }
    
    /**
     * Derives encryption key using HKDF-SHA256
     */
    private byte[] deriveKey(byte[] sharedSecret, int keyLength) {
        try {
            // HPKE key schedule - simplified version
            // In full HPKE, this would include suite_id, mode, and proper context
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256")
            byte[] hashed = digest.digest(sharedSecret)
            
            // Extract phase (HKDF-Extract with empty salt)
            javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256", "BC")
            SecretKeySpec keySpec = new SecretKeySpec(new byte[32], "HmacSHA256")
            hmac.init(keySpec)
            byte[] prk = hmac.doFinal(hashed)
            
            // Expand phase (HKDF-Expand)
            hmac = javax.crypto.Mac.getInstance("HmacSHA256", "BC")
            keySpec = new SecretKeySpec(prk, "HmacSHA256")
            hmac.init(keySpec)
            hmac.update([0x01] as byte[]) // Counter = 1
            byte[] okm = hmac.doFinal()
            
            // Return requested key length
            byte[] key = new byte[keyLength]
            System.arraycopy(okm, 0, key, 0, keyLength)
            return key
            
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed: ${e.message}", e)
        }
    }
    
    /**
     * Encrypts data using AES-128-GCM
     */
    private byte[] encryptAESGCM(byte[] plaintext, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES")
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            return cipher.doFinal(plaintext)
            
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed: ${e.message}", e)
        }
    }
    
    /**
     * Decrypts data using AES-128-GCM
     */
    private byte[] decryptAESGCM(byte[] ciphertext, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES")
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            return cipher.doFinal(ciphertext)
            
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: ${e.message}", e)
        }
    }
    
    /**
     * Reads an X25519 public key from a PEM file
     * @param filename Path to the PEM file containing the public key
     * @return The loaded PublicKey
     */
    static PublicKey readPublicKeyFromPEM(String filename) {
        new File(filename).withReader { reader ->
            def pemReader = new PemReader(reader)
            def pemObject = pemReader.readPemObject()
            def keyFactory = java.security.KeyFactory.getInstance(KEM_ALGORITHM, "BC")
            def keySpec = new java.security.spec.X509EncodedKeySpec(pemObject.getContent())
            return keyFactory.generatePublic(keySpec)
        }
    }

    /**
     * Reads an X25519 private key from a PEM file
     * @param filename Path to the PEM file containing the private key
     * @return The loaded PrivateKey
     */
    static PrivateKey readPrivateKeyFromPEM(String filename) {
        new File(filename).withReader { reader ->
            def pemReader = new PemReader(reader)
            def pemObject = pemReader.readPemObject()
            def keyFactory = java.security.KeyFactory.getInstance(KEM_ALGORITHM, "BC")
            def keySpec = new java.security.spec.PKCS8EncodedKeySpec(pemObject.getContent())
            return keyFactory.generatePrivate(keySpec)
        }
    }

    /**
     * Writes an X25519 public key to a PEM file
     * @param key The PublicKey to write
     * @param filename Path where the PEM file should be written
     */
    static void writePublicKeyToPEM(PublicKey key, String filename) {
        new File(filename).withWriter { writer ->
            def pemWriter = new PemWriter(writer)
            def pemObject = new PemObject("PUBLIC KEY", key.getEncoded())
            pemWriter.writeObject(pemObject)
            pemWriter.flush()
        }
    }

    /**
     * Writes an X25519 private key to a PEM file
     * @param key The PrivateKey to write
     * @param filename Path where the PEM file should be written
     */
    static void writePrivateKeyToPEM(PrivateKey key, String filename) {
        new File(filename).withWriter { writer ->
            def pemWriter = new PemWriter(writer)
            def pemObject = new PemObject("PRIVATE KEY", key.getEncoded())
            pemWriter.writeObject(pemObject)
            pemWriter.flush()
        }
    }
}
