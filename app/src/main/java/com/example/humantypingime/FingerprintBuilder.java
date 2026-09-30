/*
 * FingerprintBuilder.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: unchanged
 */

package com.example.humantypingime;

import java.util.List;

public class FingerprintBuilder {

    public static class Fingerprint {
        public double mean;
        public double stddev;
        public double median;
        public double p10;
        public double p90;
        public int samples;

        public byte[] serialize() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(java.util.Locale.US,
                    "v1|m=%.3f|s=%.3f|md=%.3f|p10=%.3f|p90=%.3f|n=%d",
                    mean, stddev, median, p10, p90, samples));
            return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public static Fingerprint build(List<Long> intervals) {
        Fingerprint fp = new Fingerprint();
        if (intervals == null || intervals.isEmpty()) return fp;

        int n = intervals.size();
        long[] arr = new long[n];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            arr[i] = intervals.get(i);
            sum += arr[i];
        }
        fp.samples = n;
        fp.mean = (double) sum / n;

        double sq = 0;
        for (long v : arr) {
            double d = v - fp.mean;
            sq += d * d;
        }
        fp.stddev = Math.sqrt(sq / n);

        java.util.Arrays.sort(arr);
        fp.median = arr[n / 2];
        fp.p10 = arr[(int) Math.floor(n * 0.10)];
        fp.p90 = arr[(int) Math.floor(n * 0.90)];
        return fp;
    }
}
//（注：内容由AI生成）
