package com.oneroof.intake;

/**
 * The answer to a submitted event.
 *
 * @param eventId the id that was submitted
 * @param accepted {@code false} when the same event id was received before and nothing was done
 */
public record Receipt(String eventId, boolean accepted) {
}
