package com.celebritymode.pm;

/** Target averages for a Poisson arrival process, before backpressure drops. */
public enum TrafficIntensity {
    RELAXED(20, "Relaxed · ~20/min"),
    STREAMER(80, "Streamer · ~80/min"),
    GLOBAL_CELEBRITY(180, "Global celebrity · ~180/min"),
    PEAK_WORLD_RECORD(360, "Peak world record · ~360/min");

    private final int messagesPerMinute;
    private final String label;

    TrafficIntensity(int messagesPerMinute, String label) {
        this.messagesPerMinute = messagesPerMinute;
        this.label = label;
    }

    public int getMessagesPerMinute() {
        return messagesPerMinute;
    }

    @Override
    public String toString() {
        return label;
    }
}
