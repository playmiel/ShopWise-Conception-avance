package com.shopwise.app.sales.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Ligne demandee par le client : un produit et une quantite.
 * Le prix n'est volontairement pas accepte : il est lu dans le catalogue cote serveur.
 */
public record SaleLineRequest(
		@NotNull @Positive Long productId,
		@NotNull @Positive @Max(10_000) Integer quantity) {
}