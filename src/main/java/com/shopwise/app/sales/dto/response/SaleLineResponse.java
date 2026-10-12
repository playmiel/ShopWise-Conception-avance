package com.shopwise.app.sales.dto.response;

import java.math.BigDecimal;

public record SaleLineResponse(
		Long productId,
		String productName,
		int quantity,
		BigDecimal unitPrice,
		BigDecimal lineTotal) {
}