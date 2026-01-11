package core;

/**
 * A simple, immutable data record to hold information about a single chemical reaction.
 * Records automatically provide a constructor, getters (e.g., name()), equals(), hashCode(), and toString().
 */
public record Reaction(String name, String equation, String type, String description) {
}
