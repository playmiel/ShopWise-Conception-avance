package com.shopwise.app.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param type      TOP_SELLERS, SIMILAR_PRODUCTS ou BASKET_COMPLETION
 * @param basedOn   produits de depart
 * @param modelTrainedAt date d'entrainement du modele utilise
 */
public record RecommendationResponse(String type, List<Long> basedOn, LocalDateTime modelTrainedAt,
		List<RecommendedProduct> recommendations) {
}