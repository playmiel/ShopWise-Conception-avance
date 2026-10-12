package com.shopwise.app.recommendation.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Proprietes shopwise.recommendation.* : poids du modele hybride et hyperparametres. */
@ConfigurationProperties(prefix = "shopwise.recommendation")
public record RecommendationProperties(Map<String, Double> weights, Item2Vec item2vec, Popularity popularity) {

	public RecommendationProperties {
		weights = weights == null ? Map.of() : Map.copyOf(weights);
		item2vec = item2vec == null ? new Item2Vec(24, 40, 0.05, 5, 42L) : item2vec;
		popularity = popularity == null ? new Popularity(Duration.ofDays(30)) : popularity;
	}

	/** Hyperparametres du reseau Item2Vec. */
	public record Item2Vec(int dimensions, int epochs, double learningRate, int negativeSamples, long seed) {
	}

	/** Demi-vie de la decroissance temporelle des ventes. */
	public record Popularity(Duration halfLife) {
	}
}