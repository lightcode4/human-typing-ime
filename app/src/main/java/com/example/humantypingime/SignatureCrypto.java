package com.example.humantypingime;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class SignatureCrypto {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "proof_of_human_signing_v1";

    private static boolean useEd25519() {
        return Build.VERSION.SDK_INT >= 33;
    }

    private static String algorithm() {
        return useEd25519() ? "Ed25519" : "SHA256withECDSA";
    }

    public static KeyPair getOrCreateKeyPair() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);

        if (ks.containsAlias(KEY_ALIAS)) {
            KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
            if (entry instanceof KeyStore.PrivateKeyEntry) {
                PrivateKey priv = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
                PublicKey pub = ((KeyStore.PrivateKeyEntry) entry).getCertificate().getPublicKey();
                return new KeyPair(pub, priv);
            }
        }

        String algo = useEd25519() ? "Ed25519"
                                   : KeyProperties.KEY_ALGORITHM_EC;
        KeyPairGenerator kg = KeyPairGenerator.getInstance(algo, KEYSTORE);

        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY);

        if (!useEd25519()) {
            b.setDigests(KeyProperties.DIGEST_SHA256);
        }

        kg.initialize(b.build());
        return kg.generateKeyPair();
    }

    public static byte[] sign(byte[] data) throws Exception {
        KeyPair kp = getOrCreateKeyPair();
        Signature sig = Signature.getInstance(algorithm());
        sig.initSign(kp.getPrivate());
        sig.update(data);
        return sig.sign();
    }

    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance(algorithm());
            sig.initVerify(publicKey);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** Export public key as base64 for enrollment / verification. */
    public static String exportPublicKeyBase64() throws Exception {
        KeyPair kp = getOrCreateKeyPair();
        byte[] encoded = kp.getPublic().getEncoded();
        return android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP);
    }
}
