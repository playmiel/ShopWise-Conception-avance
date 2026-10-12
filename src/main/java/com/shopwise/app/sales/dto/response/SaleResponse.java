package com.shopwise.app.sales.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Detail d'une vente avec ses lignes (US 3) et son auteur (US 5). */
public record SaleResponse(
		Long id,
		LocalDateTime saleDate,
		String createdBy,
		BigDecimal totalAmount,
		List<SaleLineResponse> lines) {
}