package com.shopwise.app.recommendation.algorithm;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.shopwise.app.recommendation.data.TrainingData;

/**
 * Similarite de contenu : vecteurs TF-IDF sur le nom et la description, compares par cosinus.
 * Ne depend pas des ventes : couvre le demarrage a froid (produit jamais vendu).
 */
@Component
public class ContentSimilarityStrategy implements RecommendationStrategy {

	public static final String NAME = "content";

	private static final Set<String> STOP_WORDS = Set.of("les", "des", "une", "pour", "avec", "sans", "aux", "the",
			"and", "for", "with", "latest");

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String reason() {
		return "SIMILAR_PRODUCT";
	}

	/** Minuscules, sans accents, mots de 3 lettres ou plus hors mots vides. */
	static List<String> tokenize(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
		return Arrays.stream(normalized.split("[^a-z0-9]+"))
				.filter(token -> token.length() >= 3 && !STOP_WORDS.contains(token))
				.toList();
	}

	@Override
	public Scorer train(TrainingData data) {
		Map<Long, Map<String, Integer>> termCounts = new HashMap<>();
		Map<String, Integer> documentFrequency = new HashMap<>();
		for (TrainingData.ProductFeatures product : data.products()) {
			Map<String, Integer> counts = new HashMap<>();
			tokenize(product.name() + " " + (product.description() == null ? "" : product.description()))
					.forEach(token -> counts.merge(token, 1, Integer::sum));
			termCounts.put(product.id(), counts);
			counts.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
		}
		int documents = data.products().size();
		Map<Long, Map<String, Double>> vectors = new HashMap<>();
		termCounts.forEach((productId, counts) -> {
			Map<String, Double> vector = new HashMap<>();
			counts.forEach((term, tf) -> {
				double idf = Math.log((documents + 1.0) / (documentFrequency.get(term) + 1.0)) + 1.0;
				vector.put(term, tf * idf);
			});
			double norm = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
			if (norm > 0) {
				vector.replaceAll((term, value) -> value / norm);
			}
			vectors.put(productId, Map.copyOf(vector));
		});
		return new TfIdfScorer(Map.copyOf(vectors), documentFrequency.size());
	}

	/** Vecteurs TF-IDF normalises par produit. */
	public record TfIdfScorer(Map<Long, Map<String, Double>> vectors, int vocabularySize) implements Scorer {

		public double similarity(Long a, Long b) {
			Map<String, Double> va = vectors.get(a);
			Map<String, Double> vb = vectors.get(b);
			if (va == null || vb == null || va.isEmpty() || vb.isEmpty()) {
				return 0.0;
			}
			Map<String, Double> small = va.size() <= vb.size() ? va : vb;
			Map<String, Double> large = small == va ? vb : va;
			double dot = 0;
			for (Map.Entry<String, Double> entry : small.entrySet()) {
				dot += entry.getValue() * large.getOrDefault(entry.getKey(), 0.0);
			}
			return Math.max(0, Math.min(1, dot));
		}

		@Override
		public double score(Collection<Long> seeds, Long candidate) {
			return seeds.isEmpty() ? 0.0
					: seeds.stream().mapToDouble(seed -> similarity(seed, candidate)).average().orElse(0);
		}

		@Override
		public Map<String, Object> diagnostics() {
			return Map.of("vocabularySize", vocabularySize, "products", vectors.size());
		}
	}
}