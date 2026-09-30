/*
 * ProofEmbedder.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: unchanged
 */

package com.example.humantypingime;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class ProofEmbedder {

    // Zero-width characters used to encode bits
    private static final char ZW_ZERO = '​';  // zero-width space
    private static final char ZW_ONE  = '‌';  // zero-width non-joiner

    private static final String TAG_START = "⁠"; // word joiner — marks beginning of tag
    private static final String TAG_END   = "⁠"; // and end

    /**
     * Payload format: base64(sigAlg:signature:fingerprint)
     *   sigAlg  = "ed25519" or "ecdsa-p256"
     *   signature = base64 raw signature bytes
     *   fingerprint = base64 of the fingerprint's serialized form
     */
    public static String buildTag(String sigAlg, byte[] signature, byte[] fingerprint) {
        String sigB64 = Base64.encodeToString(signature, Base64.NO_WRAP);
        String fpB64 = Base64.encodeToString(fingerprint, Base64.NO_WRAP);
        String payload = sigAlg + ":" + sigB64 + ":" + fpB64;
        return encode(payload);
    }

    /** Append the tag to the end of the text. */
    public static String embed(String text, String sigAlg, byte[] signature, byte[] fingerprint) {
        String tag = buildTag(sigAlg, signature, fingerprint);
        return text + TAG_START + tag + TAG_END;
    }

    /** Extract the payload if present, or null. */
    public static String extractPayload(String text) {
        if (text == null) return null;
        int start = text.indexOf(TAG_START);
        if (start < 0) return null;
        int end = text.indexOf(TAG_END, start + TAG_START.length());
        if (end < 0) return null;
        String encoded = text.substring(start + TAG_START.length(), end);
        return decode(encoded);
    }

    /** Strip the tag from the text (for display purposes). */
    public static String stripTag(String text) {
        if (text == null) return null;
        int start = text.indexOf(TAG_START);
        if (start < 0) return text;
        int end = text.indexOf(TAG_END, start + TAG_START.length());
        if (end < 0) return text;
        return text.substring(0, start) + text.substring(end + TAG_END.length());
    }

    private static String encode(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 8);
        for (byte b : bytes) {
            for (int i = 7; i >= 0; i--) {
                sb.append(((b >> i) & 1) == 1 ? ZW_ONE : ZW_ZERO);
            }
        }
        return sb.toString();
    }

    private static String decode(String s) {
        int bitCount = s.length();
        if (bitCount % 8 != 0) return null;
        byte[] out = new byte[bitCount / 8];
        for (int i = 0; i < out.length; i++) {
            int b = 0;
            for (int j = 0; j < 8; j++) {
                char c = s.charAt(i * 8 + j);
                b = (b << 1) | (c == ZW_ONE ? 1 : 0);
            }
            out[i] = (byte) b;
        }
        return new String(out, StandardCharsets.UTF_8);
    }
}
//（注：内容由AI生成）
