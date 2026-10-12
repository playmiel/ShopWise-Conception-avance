package com.shopwise.app.sales.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Informations principales d'une vente pour la liste (US 2) : sans les lignes, mais avec
 * leur nombre et la quantite totale d'articles. Construit directement par une requete
 * d'agregation, pour eviter de charger toutes les lignes de toutes les ventes (N+1).
 */
public record SaleSummaryResponse(
		Long id,
		LocalDateTime saleDate,
		String createdBy,
		BigDecimal totalAmount,
		Long lineCount,
		Long itemCount) {
}