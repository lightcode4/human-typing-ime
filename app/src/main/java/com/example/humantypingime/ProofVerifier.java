/*
 * ProofVerifier.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — explicit null guard on enrolledPubKeyB64 so a missing
 *             enrollment key returns a structured Result.fail instead of
 *             reaching Base64.decode with null input.
 */

package com.example.humantypingime;

import android.util.Base64;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public class ProofVerifier {

    public static class Result {
        public boolean valid;
        public String algorithm;
        public byte[] fingerprint;
        public String error;

        public static Result fail(String reason) {
            Result r = new Result();
            r.valid = false;
            r.error = reason;
            return r;
        }
    }

    /**
     * @param text             the received message with embedded tag
     * @param enrolledPubKeyB64 base64 of the sender's enrolled X.509 public key
     */
    public static Result verify(String text, String enrolledPubKeyB64) {
        if (enrolledPubKeyB64 == null) return Result.fail("missing enrolled public key");

        String payload = ProofEmbedder.extractPayload(text);
        if (payload == null) return Result.fail("no proof tag found");

        String[] parts = payload.split(":", 3);
        if (parts.length != 3) return Result.fail("malformed payload");

        String alg = parts[0];
        byte[] signature;
        byte[] fingerprint;
        try {
            signature = Base64.decode(parts[1], Base64.NO_WRAP);
            fingerprint = Base64.decode(parts[2], Base64.NO_WRAP);
        } catch (Exception e) {
            return Result.fail("base64 decode failed");
        }

        try {
            byte[] pubKeyBytes = Base64.decode(enrolledPubKeyB64, Base64.NO_WRAP);
            PublicKey pub = KeyFactory.getInstance(
                    alg.equals("ed25519") ? "Ed25519" : "EC")
                    .generatePublic(new X509EncodedKeySpec(pubKeyBytes));

            boolean ok = SignatureCrypto.verify(fingerprint, signature, pub);
            Result r = new Result();
            r.valid = ok;
            r.algorithm = alg;
            r.fingerprint = fingerprint;
            if (!ok) r.error = "signature mismatch";
            return r;
        } catch (Exception e) {
            return Result.fail("verify exception: " + e.getMessage());
        }
    }
}
//（注：内容由AI生成）
