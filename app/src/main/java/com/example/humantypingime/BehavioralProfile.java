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
