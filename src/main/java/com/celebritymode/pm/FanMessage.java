package com.celebritymode.pm;

/** Plain text only. Native chat escaping is performed at the dispatch boundary. */
public final class FanMessage {
    public enum Archetype { HYPE, FRIEND, REQUEST, SKEPTIC }

    private final String username;
    private final String text;
    private final Archetype archetype;
    private final long createdNanos;

    public FanMessage(String username, String text, Archetype archetype, long createdNanos) {
        this.username = username;
        this.text = text;
        this.archetype = archetype;
        this.createdNanos = createdNanos;
    }

    public String getUsername() { return username; }
    public String getText() { return text; }
    public Archetype getArchetype() { return archetype; }
    public long getCreatedNanos() { return createdNanos; }
}
