package com.shopwise.app.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;

/** Produit recommande, avec son score et les raisons de la recommandation (explicabilite). */
public record RecommendedProduct(Long productId, String name, BigDecimal price, double score, List<String> reasons) {
}