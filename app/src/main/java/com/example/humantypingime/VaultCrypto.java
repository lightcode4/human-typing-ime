package com.example.humantypingime;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class VaultCrypto {

    private final String keyAlias;

    /** Default: the vault master key. */
    public VaultCrypto() {
        this("vault_master_key_v1");
    }

    public VaultCrypto(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);

        if (ks.containsAlias(keyAlias)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(keyAlias, null)).getSecretKey();
        }

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);

        // NOTE: the key intentionally does NOT require user authentication —
        // the IME must be able to encrypt/decrypt in the background (vault
        // auto-capture and dedupe run without user presence).
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256);

        kg.init(b.build());
        return kg.generateKey();
    }

    /** Encrypt plaintext. Returns iv||ciphertext as a single byte array. */
    public byte[] encrypt(String plaintext) throws Exception {
        SecretKey key = getOrCreateKey();
        Cipher c = Cipher.getInstance(TRANSFORM);
        c.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plaintext.getBytes("UTF-8"));

        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return out;
    }

    /** Decrypt iv||ciphertext. */
    public String decrypt(byte[] blob) throws Exception {
        if (blob == null || blob.length < IV_BYTES + 16) return null;

        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(blob, 0, iv, 0, IV_BYTES);

        byte[] ct = new byte[blob.length - IV_BYTES];
        System.arraycopy(blob, IV_BYTES, ct, 0, ct.length);

        SecretKey key = getOrCreateKey();
        Cipher c = Cipher.getInstance(TRANSFORM);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(c.doFinal(ct), "UTF-8");
    }
}
