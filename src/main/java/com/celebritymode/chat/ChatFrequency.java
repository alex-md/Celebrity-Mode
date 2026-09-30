package com.celebritymode.chat;

import java.util.Random;

public enum ChatFrequency {
    OFF(0, 0),
    LOW(5, 10),
    NORMAL(1, 2),
    SPAM(1, 1);
    public final int minimumTicks, maximumTicks;

    ChatFrequency(int min, int max) {
        minimumTicks = min;
        maximumTicks = max;
    }

    public int delay(Random random) {
        return minimumTicks + random.nextInt(maximumTicks - minimumTicks + 1);
    }
}
