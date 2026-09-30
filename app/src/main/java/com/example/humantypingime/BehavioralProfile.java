/*
 * BehavioralProfile.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: unchanged
 */

package com.example.humantypingime;

/**
 * A local, aggregate typing-rhythm profile. It deliberately contains no text
 * and no individual keystroke timestamps.
 */
public class BehavioralProfile {
    public double mean;
    public double stddev;
    public double median;
    public double p10;
    public double p90;
    public int samples;
    public long enrolledAt;

    public boolean isValid() {
        return samples >= 40 && mean > 0 && stddev > 0;
    }
}
//（注：内容由AI生成）
