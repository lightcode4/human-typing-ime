/*
 * TypingTelemetry.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — getIntervals() now returns a defensive copy of the
 *             internal mutable list instead of exposing it; synchronize all
 *             mutators/accessors so the list and lastKeyTime are safely visible
 *             across the typing loop and the proof-attaching path. Callers
 *             keep working; behavior preserved.
 */

package com.example.humantypingime;

import java.util.ArrayList;
import java.util.List;

public class TypingTelemetry {

    private final List<Long> interKeyIntervals = new ArrayList<>();
    private long lastKeyTime = 0L;

    /** Call this every time a character is committed through the IME. */
    public synchronized void recordKeystroke() {
        long now = System.nanoTime();
        if (lastKeyTime > 0) {
            long deltaMs = (now - lastKeyTime) / 1_000_000L;
            // Ignore long pauses (thinking, walking away) — they're not typing signal
            if (deltaMs > 0 && deltaMs < 2000) {
                interKeyIntervals.add(deltaMs);
            }
        }
        lastKeyTime = now;
    }

    public synchronized void reset() {
        interKeyIntervals.clear();
        lastKeyTime = 0L;
    }

    public synchronized List<Long> getIntervals() {
        return new ArrayList<>(interKeyIntervals);
    }

    public synchronized boolean hasEnoughSamples() {
        return interKeyIntervals.size() >= 20;
    }
}
//（注：内容由AI生成）
