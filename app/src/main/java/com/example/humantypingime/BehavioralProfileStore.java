/*
 * BehavioralProfileStore.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: unchanged
 */

package com.example.humantypingime;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores only aggregate timing statistics in this app's private preferences. */
public class BehavioralProfileStore {
    private static final String PREFS = "behavioral_profile";
    private static final String MEAN = "mean";
    private static final String STDDEV = "stddev";
    private static final String MEDIAN = "median";
    private static final String P10 = "p10";
    private static final String P90 = "p90";
    private static final String SAMPLES = "samples";
    private static final String ENROLLED_AT = "enrolled_at";

    private final SharedPreferences prefs;

    public BehavioralProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public BehavioralProfile load() {
        BehavioralProfile profile = new BehavioralProfile();
        profile.mean = Double.longBitsToDouble(prefs.getLong(MEAN, 0L));
        profile.stddev = Double.longBitsToDouble(prefs.getLong(STDDEV, 0L));
        profile.median = Double.longBitsToDouble(prefs.getLong(MEDIAN, 0L));
        profile.p10 = Double.longBitsToDouble(prefs.getLong(P10, 0L));
        profile.p90 = Double.longBitsToDouble(prefs.getLong(P90, 0L));
        profile.samples = prefs.getInt(SAMPLES, 0);
        profile.enrolledAt = prefs.getLong(ENROLLED_AT, 0L);
        return profile;
    }

    public void save(BehavioralProfile profile) {
        prefs.edit()
                .putLong(MEAN, Double.doubleToLongBits(profile.mean))
                .putLong(STDDEV, Double.doubleToLongBits(profile.stddev))
                .putLong(MEDIAN, Double.doubleToLongBits(profile.median))
                .putLong(P10, Double.doubleToLongBits(profile.p10))
                .putLong(P90, Double.doubleToLongBits(profile.p90))
                .putInt(SAMPLES, profile.samples)
                .putLong(ENROLLED_AT, System.currentTimeMillis())
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
//（注：内容由AI生成）
