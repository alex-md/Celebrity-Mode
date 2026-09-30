package com.celebritymode.fan;

/**
 * Word-of-mouth fan arrivals in OSRS game ticks (600ms). A spotter alerts two friends, then
 * branching waves build the full 30-fan crowd over roughly 45–50 seconds.
 */
public final class CrowdArrival {
    public static final int MAX_FANS = 30;
    private static final int INITIAL_SPOT_TICKS = 4;
    private static final int TICKS_PER_GENERATION = 16;
    private static final int MAX_JITTER_TICKS = 2;

    private CrowdArrival() {}

    public static int dueTick(int startTick, int ordinal) {
        if (ordinal < 0 || ordinal >= MAX_FANS) {
            throw new IllegalArgumentException(
                    "Fan ordinal must be between 0 and " + (MAX_FANS - 1));
        }
        if (ordinal == 0) return startTick + INITIAL_SPOT_TICKS;

        // floor(log2(ordinal + 1)): generations contain 1, 2, 4, 8, then 15 fans.
        int generation = 31 - Integer.numberOfLeadingZeros(ordinal + 1);
        int genStart = (1 << generation) - 1;
        int genSize = 1 << generation;
        double progressInGen = (double) (ordinal - genStart) / genSize;
        int waveOffset = (int) Math.round(progressInGen * (TICKS_PER_GENERATION - 2));
        int baseDelay = INITIAL_SPOT_TICKS + generation * TICKS_PER_GENERATION + waveOffset;
        int jitter = pseudoJitter(ordinal, MAX_JITTER_TICKS);
        return startTick + Math.max(INITIAL_SPOT_TICKS + 1, baseDelay + jitter);
    }

    /** Stable pathing/reaction variance without consuming the crowd's shared random state. */
    private static int pseudoJitter(int seed, int range) {
        int hash = (seed * 0x45d9f3b) ^ (seed >> 8);
        hash = (hash * 0x45d9f3b) ^ (hash >> 16);
        // Widen before abs so Integer.MIN_VALUE cannot produce out-of-range jitter.
        return (int) (Math.abs((long) hash) % (range * 2 + 1)) - range;
    }
}
