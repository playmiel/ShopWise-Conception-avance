package com.shopwise.app.recommendation.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.shopwise.app.recommendation.algorithm.Scorer;

/**
 * Instantane immuable du modele entraine. Il est remplace d'un bloc a chaque reentrainement :
 * les requetes en cours continuent sur l'ancien modele, sans verrou ni etat incoherent.
 */
record RecommendationModel(
		LocalDateTime trainedAt,
		long trainingMillis,
		int salesCount,
		List<Long> productIds,
		Map<String, Scorer> scorers,
		Map<String, Double> weights,
		Map<String, String> reasons) {
}