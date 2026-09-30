/*
 * SensitivePatternDetector.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: unchanged
 */

package com.example.humantypingime;

import java.util.regex.Pattern;

public class SensitivePatternDetector {

    // 13-19 digits, optional spaces/dashes — Luhn-checked separately to cut false positives
    private static final Pattern CARD_CANDIDATE =
            Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    // 4-8 digit numeric code, not part of a longer number
    private static final Pattern OTP =
            Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)");

    // Common API key shapes
    private static final Pattern API_KEY = Pattern.compile(
            "(?i)\\b(?:sk-[a-z0-9]{20,}|" +                       // OpenAI-style
            "AKIA[0-9A-Z]{16}|" +                                 // AWS access key
            "ghp_[a-zA-Z0-9]{36}|" +                              // GitHub PAT
            "xox[baprs]-[a-zA-Z0-9-]{10,})\\b");

    // JWT
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

    // Long alphanumeric string mixed with symbols — password-ish
    private static final Pattern PASSWORDISH = Pattern.compile(
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{12,128}$");

    public enum Kind { CARD, OTP, API_KEY, JWT, PASSWORD, NONE }

    public static Kind classify(String text) {
        if (text == null) return Kind.NONE;
        String t = text.trim();
        if (t.isEmpty()) return Kind.NONE;

        if (JWT.matcher(t).find()) return Kind.JWT;
        if (API_KEY.matcher(t).find()) return Kind.API_KEY;

        // Card — regex shape + Luhn
        if (CARD_CANDIDATE.matcher(t).matches()) {
            String digits = t.replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && luhn(digits)) {
                return Kind.CARD;
            }
        }

        // OTP — pure 4-8 digit numeric string
        if (OTP.matcher(t).matches()) return Kind.OTP;

        // Password-ish single-token string
        if (!t.contains(" ") && t.length() >= 12 && PASSWORDISH.matcher(t).matches()) {
            return Kind.PASSWORD;
        }

        return Kind.NONE;
    }

    public static boolean isSensitive(String text) {
        return classify(text) != Kind.NONE;
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
//（注：内容由AI生成）
