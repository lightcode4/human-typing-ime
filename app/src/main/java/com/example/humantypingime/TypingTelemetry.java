package com.example.humantypingime;

import java.util.ArrayList;
import java.util.List;

public class TypingTelemetry {

    private final List<Long> interKeyIntervals = new ArrayList<>();
    private long lastKeyTime = 0L;

    /** Call this every time a character is committed through the IME. */
    public void recordKeystroke() {
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

    public void reset() {
        interKeyIntervals.clear();
        lastKeyTime = 0L;
    }

    public List<Long> getIntervals() {
        return interKeyIntervals;
    }

    public boolean hasEnoughSamples() {
        return interKeyIntervals.size() >= 20;
    }
}
