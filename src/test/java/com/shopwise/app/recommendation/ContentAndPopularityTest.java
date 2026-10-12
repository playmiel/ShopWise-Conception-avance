package com.shopwise.app.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopwise.app.recommendation.algorithm.ContentSimilarityStrategy;
import com.shopwise.app.recommendation.algorithm.PopularityStrategy;
import com.shopwise.app.recommendation.algorithm.Scorer;
import com.shopwise.app.recommendation.config.RecommendationProperties;
import com.shopwise.app.recommendation.data.TrainingData;
import com.shopwise.app.recommendation.data.TrainingData.Basket;
import com.shopwise.app.recommendation.data.TrainingData.ProductFeatures;

@DisplayName("Unitaire - strategies contenu (TF-IDF) et popularite")
class ContentAndPopularityTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);
	private static final RecommendationProperties PROPERTIES = new RecommendationProperties(Map.of(), null,
			new RecommendationProperties.Popularity(Duration.ofDays(30)));

	@Test
	@DisplayName("TF-IDF : les produits qui partagent des mots rares sont les plus proches")
	void contentSimilarity() {
		TrainingData data = new TrainingData(NOW, List.of(
				new ProductFeatures(1L, "Parmesan rape", "Fromage parmesan AOP"),
				new ProductFeatures(2L, "Fromage parmesan", "Parmesan en bloc"),
				new ProductFeatures(3L, "Biere blonde", "Biere artisanale"),
				new ProductFeatures(4L, "Sel", null)), List.of());

		Scorer scorer = new ContentSimilarityStrategy().train(data);

		assertThat(scorer.score(List.of(1L), 2L)).isGreaterThan(0.5);
		assertThat(scorer.score(List.of(1L), 3L)).isZero();
		assertThat(scorer.score(List.of(1L), 4L)).isZero();
		assertThat(scorer.score(List.of(), 2L)).isZero();
	}

	@Test
	@DisplayName("TF-IDF : un produit est parfaitement similaire a lui-meme")
	void selfSimilarity() {
		TrainingData data = new TrainingData(NOW, List.of(
				new ProductFeatures(1L, "Cafe moulu", "Cafe arabica"),
				new ProductFeatures(2L, "The vert", "Infusion")), List.of());

		assertThat(new ContentSimilarityStrategy().train(data).score(List.of(1L), 1L)).isCloseTo(1.0, within(1e-9));
	}

	@Test
	@DisplayName("popularite : une vente recente pese plus qu'une vente ancienne plus volumineuse")
	void popularityTimeDecay() {
		TrainingData data = new TrainingData(NOW, List.of(), List.of(
				new Basket(1L, NOW.minusDays(90), Map.of(10L, 3)),   // 3 x 0.5^3 = 0.375
				new Basket(2L, NOW, Map.of(20L, 1))));               // 1 x 1     = 1.0

		Scorer scorer = new PopularityStrategy(PROPERTIES).train(data);

		assertThat(scorer.score(List.of(), 20L)).isCloseTo(1.0, within(1e-9));
		assertThat(scorer.score(List.of(), 10L)).isCloseTo(0.375, within(1e-9));
		assertThat(scorer.score(List.of(), 30L)).isZero();
	}

	@Test
	@DisplayName("popularite : aucune vente -> tous les scores a 0")
	void popularityWithoutSales() {
		Scorer scorer = new PopularityStrategy(PROPERTIES).train(new TrainingData(NOW, List.of(), List.of()));

		assertThat(scorer.score(List.of(), 1L)).isZero();
	}
}