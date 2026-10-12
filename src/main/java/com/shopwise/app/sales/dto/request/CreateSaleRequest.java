package com.shopwise.app.sales.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de POST /api/sales : une vente contient au moins une ligne (US 1). */
public record CreateSaleRequest(
		@NotEmpty @Size(max = 100) List<@NotNull @Valid SaleLineRequest> lines) {
}