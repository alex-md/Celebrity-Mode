package com.celebritymode.fan;

public enum ArrivalPace {
    GRADUAL,
    QUICK,
    INSTANT;

    public int dueTick(int startTick, int ordinal) {
        int delay = CrowdArrival.dueTick(0, ordinal);
        return startTick + (this == INSTANT ? 0 : this == QUICK ? Math.max(1, delay / 4) : delay);
    }
}
