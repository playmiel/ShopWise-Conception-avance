package com.shopwise.app.recommendation.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.shopwise.app.recommendation.config.RecommendationProperties;

/**
 * Item2Vec : reseau de neurones peu profond (architecture skip-gram de word2vec) applique aux paniers.
 *
 * Chaque produit est represente par un vecteur (embedding) de dimension d. Pour chaque paire
 * (produit cible, produit du meme panier), le reseau apprend a predire que les deux produits
 * apparaissent ensemble (sortie sigmoide = 1), et que k produits tires au hasard n'y apparaissent
 * pas (echantillonnage negatif, sortie = 0). La perte d'entropie croisee est minimisee par
 * descente de gradient stochastique avec un taux d'apprentissage decroissant lineairement.
 *
 * Apres entrainement, deux produits souvent achetes ensemble (ou dans des contextes similaires)
 * ont des vecteurs proches : leur similarite cosinus mesure la complementarite.
 * L'entrainement est deterministe (graine fixe) pour etre reproductible et testable.
 */
public final class Item2VecModel implements Scorer {

	private final Map<Long, double[]> embeddings;
	private final List<Double> lossHistory;
	private final int dimensions;
	private final int trainingPairsPerEpoch;

	private Item2VecModel(Map<Long, double[]> embeddings, List<Double> lossHistory, int dimensions,
			int trainingPairsPerEpoch) {
		this.embeddings = embeddings;
		this.lossHistory = lossHistory;
		this.dimensions = dimensions;
		this.trainingPairsPerEpoch = trainingPairsPerEpoch;
	}

	public static Item2VecModel train(List<List<Long>> baskets, RecommendationProperties.Item2Vec params) {
		Map<Long, Integer> frequency = new LinkedHashMap<>();
		baskets.forEach(basket -> basket.forEach(id -> frequency.merge(id, 1, Integer::sum)));
		List<Long> vocabulary = new ArrayList<>(frequency.keySet());
		Collections.sort(vocabulary);
		int size = vocabulary.size();
		int d = params.dimensions();
		if (size < 2 || baskets.isEmpty()) {
			return new Item2VecModel(Map.of(), List.of(), d, 0);
		}
		Map<Long, Integer> index = new HashMap<>();
		for (int i = 0; i < size; i++) {
			index.put(vocabulary.get(i), i);
		}

		Random random = new Random(params.seed());
		double[][] input = new double[size][d];   // embeddings appris (couche d'entree)
		double[][] output = new double[size][d];  // poids de la couche de sortie
		for (double[] row : input) {
			for (int j = 0; j < d; j++) {
				row[j] = (random.nextDouble() - 0.5) / d;
			}
		}

		// Distribution de tirage des negatifs : frequence^0.75 (lisse la domination des produits tres vendus)
		double[] cumulative = new double[size];
		double total = 0;
		for (int i = 0; i < size; i++) {
			total += Math.pow(frequency.get(vocabulary.get(i)), 0.75);
			cumulative[i] = total;
		}

		List<int[]> order = new ArrayList<>(baskets.size());
		for (List<Long> basket : baskets) {
			order.add(basket.stream().mapToInt(index::get).toArray());
		}

		long totalSteps = (long) params.epochs() * order.size();
		long step = 0;
		double[] gradient = new double[d];
		List<Double> losses = new ArrayList<>(params.epochs());
		int pairsPerEpoch = 0;
		for (int epoch = 0; epoch < params.epochs(); epoch++) {
			Collections.shuffle(order, random);
			double epochLoss = 0;
			int pairs = 0;
			for (int[] basket : order) {
				double learningRate = Math.max(params.learningRate() * 1e-4,
						params.learningRate() * (1.0 - (double) step++ / totalSteps));
				for (int target : basket) {
					for (int context : basket) {
						if (target == context) {
							continue;
						}
						Arrays.fill(gradient, 0);
						epochLoss += update(input[target], output[context], 1, learningRate, gradient);
						for (int k = 0; k < params.negativeSamples(); k++) {
							int negative = sample(cumulative, total, random);
							if (negative != context && negative != target) {
								epochLoss += update(input[target], output[negative], 0, learningRate, gradient);
							}
						}
						for (int j = 0; j < d; j++) {
							input[target][j] += gradient[j];
						}
						pairs++;
					}
				}
			}
			losses.add(epochLoss / Math.max(1, pairs));
			pairsPerEpoch = pairs;
		}

		Map<Long, double[]> normalized = new HashMap<>();
		for (int i = 0; i < size; i++) {
			double norm = Math.sqrt(Arrays.stream(input[i]).map(v -> v * v).sum());
			if (norm > 0) {
				double[] vector = new double[d];
				for (int j = 0; j < d; j++) {
					vector[j] = input[i][j] / norm;
				}
				normalized.put(vocabulary.get(i), vector);
			}
		}
		return new Item2VecModel(Map.copyOf(normalized), List.copyOf(losses), d, pairsPerEpoch);
	}

	/** Une etape de descente de gradient sur une paire (sortie sigmoide, perte d'entropie croisee). */
	private static double update(double[] target, double[] context, int label, double learningRate, double[] gradient) {
		double dot = 0;
		for (int j = 0; j < target.length; j++) {
			dot += target[j] * context[j];
		}
		double prediction = 1.0 / (1.0 + Math.exp(-dot));
		double g = learningRate * (label - prediction);
		for (int j = 0; j < target.length; j++) {
			gradient[j] += g * context[j];
			context[j] += g * target[j];
		}
		double probability = label == 1 ? prediction : 1.0 - prediction;
		return -Math.log(Math.max(probability, 1e-10));
	}

	private static int sample(double[] cumulative, double total, Random random) {
		int position = Arrays.binarySearch(cumulative, random.nextDouble() * total);
		if (position < 0) {
			position = -position - 1;
		}
		return Math.min(position, cumulative.length - 1);
	}

	/** Similarite cosinus des embeddings, dans [-1, 1] ; 0 si un produit est inconnu du modele. */
	public double similarity(Long a, Long b) {
		double[] va = embeddings.get(a);
		double[] vb = embeddings.get(b);
		if (va == null || vb == null) {
			return 0.0;
		}
		double dot = 0;
		for (int j = 0; j < va.length; j++) {
			dot += va[j] * vb[j];
		}
		return dot;
	}

	@Override
	public double score(Collection<Long> seeds, Long candidate) {
		return seeds.isEmpty() ? 0.0
				: seeds.stream().mapToDouble(seed -> Math.max(0, similarity(seed, candidate))).average().orElse(0);
	}

	public List<Double> lossHistory() {
		return lossHistory;
	}

	public boolean knows(Long productId) {
		return embeddings.containsKey(productId);
	}

	@Override
	public Map<String, Object> diagnostics() {
		Map<String, Object> diagnostics = new LinkedHashMap<>();
		diagnostics.put("dimensions", dimensions);
		diagnostics.put("epochs", lossHistory.size());
		diagnostics.put("vocabularySize", embeddings.size());
		diagnostics.put("trainingPairsPerEpoch", trainingPairsPerEpoch);
		if (!lossHistory.isEmpty()) {
			diagnostics.put("initialLoss", round(lossHistory.get(0)));
			diagnostics.put("finalLoss", round(lossHistory.get(lossHistory.size() - 1)));
		}
		return diagnostics;
	}

	private static double round(double value) {
		return Math.round(value * 10_000.0) / 10_000.0;
	}
}