package com.oneroof.payment;

import java.util.List;

/**
 * Instructions were stored and are ready to be handed to the bank. Handing them over happens after
 * the storing transaction commits, so a failure at the bank never loses an instruction.
 */
record PaymentBatchCreated(List<Long> instructionIds) {
}
