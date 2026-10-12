package com.shopwise.app.recommendation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.service.ProductService;
import com.shopwise.app.recommendation.algorithm.PopularityStrategy;
import com.shopwise.app.recommendation.algorithm.RecommendationStrategy;
import com.shopwise.app.recommendation.algorithm.Scorer;
import com.shopwise.app.recommendation.config.RecommendationProperties;
import com.shopwise.app.recommendation.data.TrainingData;
import com.shopwise.app.recommendation.data.TrainingDataProvider;
import com.shopwise.app.recommendation.dto.ModelInfoResponse;
import com.shopwise.app.recommendation.dto.RecommendationResponse;
import com.shopwise.app.recommendation.dto.RecommendedProduct;
import com.shopwise.app.shared.exception.ApiException;
import com.shopwise.app.shared.exception.ErrorCode;

/**
 * Moteur de recommandation hybride (US 7).
 *
 * Entraine toutes les strategies declarees, puis combine leurs scores :
 * score(c) = somme sur les strategies s de poids(s) x score_s(produits de depart, c).
 * Une strategie qui apporte au moins 25 % du score d'un produit donne sa raison a la recommandation.
 * Les produits renvoyes sont relus dans le catalogue courant (nom et prix a jour, produits supprimes exclus).
 */
@Service
public class RecommendationService {

	private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
	private static final double REASON_SHARE = 0.25;

	private final TrainingDataProvider dataProvider;
	private final List<RecommendationStrategy> strategies;
	private final RecommendationProperties properties;
	private final ProductService productService;
	private final AtomicReference<RecommendationModel> model = new AtomicReference<>();

	public RecommendationService(TrainingDataProvider dataProvider, List<RecommendationStrategy> strategies,
			RecommendationProperties properties, ProductService productService) {
		this.dataProvider = dataProvider;
		this.strategies = List.copyOf(strategies);
		this.properties = properties;
		this.productService = productService;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void trainOnStartup() {
		retrain();
	}

	@Scheduled(cron = "${shopwise.recommendation.retrain-cron:0 0 * * * *}")
	public void scheduledRetrain() {
		retrain();
	}

	/** Reentraine toutes les strategies sur les donnees courantes et publie le nouveau modele. */
	public synchronized ModelInfoResponse retrain() {
		long start = System.nanoTime();
		TrainingData data = dataProvider.load();
		Map<String, Scorer> scorers = new LinkedHashMap<>();
		Map<String, String> reasons = new LinkedHashMap<>();
		for (RecommendationStrategy strategy : strategies) {
			scorers.put(strategy.name(), strategy.train(data));
			reasons.put(strategy.name(), strategy.reason());
		}
		long millis = (System.nanoTime() - start) / 1_000_000;
		List<Long> productIds = data.products().stream().map(TrainingData.ProductFeatures::id).sorted().toList();
		RecommendationModel trained = new RecommendationModel(data.referenceDate(), millis, data.baskets().size(),
				productIds, Map.copyOf(scorers), normalizedWeights(), Map.copyOf(reasons));
		model.set(trained);
		log.info("Recommendation model trained in {} ms on {} sales and {} products", millis, trained.salesCount(),
				productIds.size());
		return info(trained);
	}

	public ModelInfoResponse modelInfo() {
		return info(current());
	}

	/** Meilleures ventes recentes (popularite avec decroissance temporelle). */
	public RecommendationResponse topSellers(int limit) {
		RecommendationModel m = current();
		Scorer popularity = m.scorers().get(PopularityStrategy.NAME);
		Map<Long, Double> scores = new LinkedHashMap<>();
		if (popularity != null) {
			for (Long id : m.productIds()) {
				double score = popularity.score(List.of(), id);
				if (score > 0) {
					scores.put(id, score);
				}
			}
		}
		Map<Long, List<String>> reasons = scores.keySet().stream()
				.collect(Collectors.toMap(Function.identity(), id -> List.of(m.reasons().get(PopularityStrategy.NAME))));
		return response("TOP_SELLERS", List.of(), m, scores, reasons, limit);
	}

	/** Produits a proposer a partir d'un produit consulte. */
	public RecommendationResponse similarTo(Long productId, int limit) {
		productService.getById(productId); // 404 si le produit n'existe pas
		return hybrid("SIMILAR_PRODUCTS", List.of(productId), limit);
	}

	/** Produits a proposer pour completer un panier. */
	public RecommendationResponse completeBasket(List<Long> productIds, int limit) {
		Set<Long> seeds = new LinkedHashSet<>(productIds);
		Set<Long> known = productService.getByIds(seeds).stream().map(ProductResponse::getId).collect(Collectors.toSet());
		List<Long> unknown = seeds.stream().filter(id -> !known.contains(id)).toList();
		if (!unknown.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_REFERENCE, "Unknown product id(s): " + unknown);
		}
		return hybrid("BASKET_COMPLETION", List.copyOf(seeds), limit);
	}

	private RecommendationResponse hybrid(String type, List<Long> seeds, int limit) {
		RecommendationModel m = current();
		Map<Long, Double> scores = new LinkedHashMap<>();
		Map<Long, List<String>> reasons = new LinkedHashMap<>();
		for (Long candidate : m.productIds()) {
			if (seeds.contains(candidate)) {
				continue;
			}
			Map<String, Double> contributions = new LinkedHashMap<>();
			m.weights().forEach((name, weight) -> {
				Scorer scorer = m.scorers().get(name);
				if (scorer != null) {
					contributions.put(name, weight * scorer.score(seeds, candidate));
				}
			});
			double total = contributions.values().stream().mapToDouble(Double::doubleValue).sum();
			if (total > 0) {
				scores.put(candidate, total);
				reasons.put(candidate, contributions.entrySet().stream()
						.filter(e -> e.getValue() >= REASON_SHARE * total)
						.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
						.map(e -> m.reasons().get(e.getKey()))
						.toList());
			}
		}
		return response(type, seeds, m, scores, reasons, limit);
	}

	/** Classement par score decroissant (identifiant croissant en cas d'egalite, pour un ordre stable). */
	private static List<Map.Entry<Long, Double>> rank(Map<Long, Double> scores) {
		return scores.entrySet().stream()
				.sorted(Map.Entry.<Long, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.toList();
	}

	/** Construit la reponse a partir du catalogue courant : nom et prix a jour, produits supprimes ignores. */
	private RecommendationResponse response(String type, List<Long> seeds, RecommendationModel m,
			Map<Long, Double> scores, Map<Long, List<String>> reasons, int limit) {
		List<Map.Entry<Long, Double>> ranked = rank(scores);
		Map<Long, ProductResponse> live = productService.getByIds(ranked.stream().map(Map.Entry::getKey).toList())
				.stream().collect(Collectors.toMap(ProductResponse::getId, Function.identity()));
		List<RecommendedProduct> products = new ArrayList<>();
		for (Map.Entry<Long, Double> entry : ranked) {
			if (products.size() >= limit) {
				break;
			}
			ProductResponse product = live.get(entry.getKey());
			if (product != null) {
				products.add(new RecommendedProduct(product.getId(), product.getName(), product.getPrice(),
						BigDecimal.valueOf(entry.getValue()).setScale(4, RoundingMode.HALF_UP).doubleValue(),
						reasons.getOrDefault(entry.getKey(), List.of())));
			}
		}
		return new RecommendationResponse(type, seeds, m.trainedAt(), products);
	}

	/** Modele courant ; entraine a la premiere utilisation s'il ne l'a pas encore ete. */
	private RecommendationModel current() {
		RecommendationModel m = model.get();
		if (m == null) {
			retrain();
			m = model.get();
		}
		return m;
	}
	private Map<String, Double> normalizedWeights() {
		Map<String, Double> weights = new LinkedHashMap<>();
		for (RecommendationStrategy strategy : strategies) {
			double weight = properties.weights().getOrDefault(strategy.name(), 0.0);
			if (weight > 0) {
				weights.put(strategy.name(), weight);
			}
		}
		double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
		if (sum > 0) {
			weights.replaceAll((name, weight) -> weight / sum);
		}
		return Map.copyOf(weights);
	}

	private static ModelInfoResponse info(RecommendationModel m) {
		Map<String, Map<String, Object>> diagnostics = new LinkedHashMap<>();
		m.scorers().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(e -> diagnostics.put(e.getKey(), e.getValue().diagnostics()));
		Map<String, Double> weights = new LinkedHashMap<>();
		m.weights().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.forEach(e -> weights.put(e.getKey(), Math.round(e.getValue() * 1000.0) / 1000.0));
		return new ModelInfoResponse(m.trainedAt(), m.trainingMillis(), m.salesCount(), m.productIds().size(),
				weights, diagnostics);
	}
}
