/*
 * TranscriptPostProcessor.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — bound the repeated-word collapse fixed-point loop
 *             (j) with a maximum-iteration cap so a pathological transcript can
 *             never spin; normal inputs still converge in 1–2 passes.
 */

package com.example.humantypingime;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranscriptPostProcessor {

    // Common English speech fillers. Keep this conservative — you don't
    // want to drop words that carry intent (e.g. "well" at the start of a clause).
    private static final Set<String> FILLERS = new HashSet<>(Arrays.asList(
            "um", "uh", "uhh", "umm", "erm", "er",
            "like", "basically", "actually", "literally",
            "sort of", "kind of", "you know", "i mean"
    ));

    // Multi-word fillers need regex; single-word can use token lookups.
    private static final Pattern MULTI_WORD_FILLERS = Pattern.compile(
            "\\b(sort of|kind of|you know|i mean)\\b,?\\s*",
            Pattern.CASE_INSENSITIVE
    );

    // Matches duplicate adjacent words: "the the", "I I", "and and".
    private static final Pattern REPEATED_WORDS = Pattern.compile(
            "\\b([A-Za-z]+)\\s+\\1\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Safety cap for the fixed-point collapse loop; well-formed transcripts
    // converge far below this.
    private static final int MAX_COLLAPSE_ITERATIONS = 16;

    public static String process(String raw, List<Long> pauseMarks) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String s = raw.trim();
        s = removeFillers(s);
        if (s.trim().isEmpty()) return "";
        s = collapseRepeats(s);
        s = insertPunctuationFromPauses(s, pauseMarks);
        s = capitalizeSentences(s);

        return s.trim();
    }

    private static String removeFillers(String s) {
        // First pass: multi-word phrases
        s = MULTI_WORD_FILLERS.matcher(s).replaceAll("");

        // Second pass: single-word fillers
        String[] words = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.US);
            if (FILLERS.contains(clean)) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w);
        }
        return out.toString();
    }

    private static String collapseRepeats(String s) {
        // Run until fixed point so "the the the" collapses to "the"
        String prev;
        int iterations = 0;
        do {
            prev = s;
            Matcher m = REPEATED_WORDS.matcher(s);
            s = m.replaceAll("$1");
            if (++iterations >= MAX_COLLAPSE_ITERATIONS) break;
        } while (!s.equals(prev));
        return s;
    }

    /**
     * Pauses detected during speech (recorded by VoiceInputManager via onRmsChanged)
     * are matched heuristically against word boundaries. If a pause > ~700ms happened
     * near a word transition, append a comma or period depending on pause length.
     *
     * In practice, SpeechRecognizer does not return per-word timestamps without
     * using the cloud-based Cloud Speech API, so this uses the pauseMarks count
     * proportionally across the word count. It's a heuristic, but it works
     * surprisingly well for natural cadence.
     */
    private static String insertPunctuationFromPauses(String s, List<Long> pauseMarks) {
        if (pauseMarks == null || pauseMarks.isEmpty()) {
            // No pause data: just ensure a trailing period
            return endsWithPunct(s) ? s : s + ".";
        }

        String[] words = s.split("\\s+");
        if (words.length < 3) {
            return endsWithPunct(s) ? s : s + ".";
        }

        // Map pause marks into word positions proportionally
        // e.g. 3 pauses across 12 words -> pause roughly after word 3, 6, 9
        int step = Math.max(2, words.length / (pauseMarks.size() + 1));
        StringBuilder out = new StringBuilder();

        int pauseIdx = 0;
        for (int i = 0; i < words.length; i++) {
            out.append(words[i]);
            boolean isLast = (i == words.length - 1);

            if (isLast) {
                if (!endsWithPunct(out.toString())) out.append('.');
            } else if ((i + 1) % step == 0 && pauseIdx < pauseMarks.size()) {
                long duration = pauseMarks.get(pauseIdx++);
                // Long pause -> period (new sentence); medium pause -> comma
                if (duration > 1200) {
                    out.append('.');
                } else if (duration > 600) {
                    out.append(',');
                }
                out.append(' ');
            } else {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private static String capitalizeSentences(String s) {
        // Capitalize first letter and any letter following ". " or "? " or "! "
        char[] chars = s.toCharArray();
        boolean capNext = true;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (capNext && Character.isLetter(c)) {
                chars[i] = Character.toUpperCase(c);
                capNext = false;
            } else if (c == '.' || c == '?' || c == '!') {
                capNext = true;
            }
        }
        return new String(chars);
    }

    private static boolean endsWithPunct(String s) {
        if (s == null || s.isEmpty()) return false;
        char last = s.charAt(s.length() - 1);
        return last == '.' || last == '?' || last == '!' || last == ',';
    }
}
//（注：内容由AI生成）
