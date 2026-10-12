package com.shopwise.app.recommendation.algorithm;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.shopwise.app.recommendation.config.RecommendationProperties;
import com.shopwise.app.recommendation.data.TrainingData;

/**
 * Meilleures ventes avec decroissance temporelle : chaque article vendu compte pour
 * quantite x 0.5^(anciennete / demi-vie). Les tendances recentes l'emportent sur l'historique ancien.
 * Score normalise par le meilleur produit (1.0 = meilleure vente).
 */
@Component
public class PopularityStrategy implements RecommendationStrategy {

	public static final String NAME = "popularity";

	private final Duration halfLife;

	public PopularityStrategy(RecommendationProperties properties) {
		this.halfLife = properties.popularity().halfLife();
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String reason() {
		return "BEST_SELLER";
	}

	@Override
	public Scorer train(TrainingData data) {
		double halfLifeDays = halfLife.toSeconds() / 86_400.0;
		Map<Long, Double> raw = new HashMap<>();
		for (TrainingData.Basket basket : data.baskets()) {
			double ageDays = Math.max(0, Duration.between(basket.date(), data.referenceDate()).toSeconds() / 86_400.0);
			double weight = Math.pow(0.5, ageDays / halfLifeDays);
			basket.quantities().forEach((productId, quantity) -> raw.merge(productId, quantity * weight, Double::sum));
		}
		double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
		Map<Long, Double> normalized = new HashMap<>();
		if (max > 0) {
			raw.forEach((id, value) -> normalized.put(id, value / max));
		}
		return new PopularityScorer(Map.copyOf(normalized), halfLifeDays);
	}

	/** Scores de popularite, independants des produits de depart. */
	public record PopularityScorer(Map<Long, Double> scores, double halfLifeDays) implements Scorer {

		@Override
		public double score(Collection<Long> seeds, Long candidate) {
			return scores.getOrDefault(candidate, 0.0);
		}

		@Override
		public Map<String, Object> diagnostics() {
			return Map.of("halfLifeDays", halfLifeDays, "productsWithSales", scores.size());
		}
	}
}