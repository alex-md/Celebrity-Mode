package com.celebritymode.generation;

/** Wording plus its unexpanded sentence structure, for repetition checks across speakers. */
public final class FanUtterance {
    public final String text;
    public final String structure;

    public FanUtterance(String text, String structure) {
        this.text = text;
        this.structure = structure;
    }
}
