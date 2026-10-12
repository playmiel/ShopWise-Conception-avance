package com.shopwise.app.sales.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Detail d'une vente avec ses lignes (US 3). */
public record SaleResponse(
		Long id,
		LocalDateTime saleDate,
		BigDecimal totalAmount,
		List<SaleLineResponse> lines) {
}