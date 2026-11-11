package com.oneroof.payment;

/**
 * An instruction was stored and is ready to be handed to the bank. Handing it over happens after
 * the storing transaction commits, so a failure at the bank never loses the instruction.
 */
record PaymentInstructionCreated(long instructionId) {
}
