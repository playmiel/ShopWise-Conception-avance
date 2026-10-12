package com.shopwise.app.recommendation.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Etat du modele : date, volume de donnees, poids et indicateurs d'entrainement par strategie. */
public record ModelInfoResponse(LocalDateTime trainedAt, long trainingMillis, int salesCount, int productCount,
		Map<String, Double> weights, Map<String, Map<String, Object>> strategies) {
}