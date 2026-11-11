/**
 * Receives business events (settlement batches, master data corrections, external replies) and turns each into exactly one command, rejecting duplicates.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Intake")
package com.oneroof.intake;
