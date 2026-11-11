package com.oneroof.payment.adapter;

import com.oneroof.payment.PaymentGatewayPort;
import org.springframework.stereotype.Component;

/** Simulated bank gateway: accepts every order and sends nothing anywhere. */
@Component
class MockPaymentGateway implements PaymentGatewayPort {

	@Override
	public String send(PaymentOrder order) {
		return "MOCK-GW-%06d".formatted(order.instructionId());
	}

	@Override
	public boolean isSimulated() {
		return true;
	}
}
