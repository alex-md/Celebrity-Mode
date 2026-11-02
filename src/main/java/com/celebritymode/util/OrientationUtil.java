package com.celebritymode.util;

public final class OrientationUtil {
    private OrientationUtil() {}

    /** Jagex: south=0, west=512, north=1024, east=1536. */
    public static int facing(int dx, int dy) {
        return ((int) Math.round(Math.atan2(-dx, -dy) * 1024 / Math.PI)) & 2047;
    }

    public static int delta(int current, int target) {
        return ((target - current + 1024) & 2047) - 1024;
    }

    public static int approach(int current, int target) {
        int delta = delta(current, target);
        // One 20 ms client cycle; rendering the same cycle twice must not turn twice.
        return (current + Math.max(-32, Math.min(32, delta))) & 2047;
    }
}
