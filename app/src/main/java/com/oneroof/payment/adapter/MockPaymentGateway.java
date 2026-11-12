package com.oneroof.payment.adapter;

import java.util.List;

import com.oneroof.payment.PaymentGatewayPort;
import org.springframework.stereotype.Component;

/** Simulated bank gateway: accepts every bulk transfer and sends nothing anywhere. */
@Component
class MockPaymentGateway implements PaymentGatewayPort {

	@Override
	public BulkReceipt sendBulk(List<PaymentOrder> orders) {
		List<String> references = orders.stream().map((order) -> "MOCK-GW-%06d".formatted(order.instructionId())).toList();
		return new BulkReceipt("MOCK-GW-BULK-%06d".formatted(orders.get(0).instructionId()), references);
	}

	@Override
	public boolean isSimulated() {
		return true;
	}
}
