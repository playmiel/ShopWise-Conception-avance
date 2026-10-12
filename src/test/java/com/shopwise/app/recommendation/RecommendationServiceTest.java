package com.shopwise.app.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.service.ProductService;
import com.shopwise.app.recommendation.algorithm.ContentSimilarityStrategy;
import com.shopwise.app.recommendation.algorithm.Item2VecStrategy;
import com.shopwise.app.recommendation.algorithm.PopularityStrategy;
import com.shopwise.app.recommendation.config.RecommendationProperties;
import com.shopwise.app.recommendation.data.TrainingData;
import com.shopwise.app.recommendation.data.TrainingData.Basket;
import com.shopwise.app.recommendation.data.TrainingData.ProductFeatures;
import com.shopwise.app.recommendation.dto.RecommendationResponse;
import com.shopwise.app.recommendation.dto.RecommendedProduct;
import com.shopwise.app.recommendation.service.RecommendationService;
import com.shopwise.app.shared.exception.ApiException;
import com.shopwise.app.shared.exception.ErrorCode;
import com.shopwise.app.shared.exception.NotFoundException;

/**
 * Moteur hybride avec les vraies strategies, une source de donnees en memoire (illustration de
 * l'US 8 : la source est interchangeable) et un catalogue simule.
 * Familles d'achat : pates {1,2,3}, petit-dejeuner {4,5,6} ; 7 n'est jamais vendu.
 */
@DisplayName("Unitaire - moteur de recommandation hybride")
class RecommendationServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);
	private static final String[] NAMES = {"", "Spaghetti", "Sauce tomate", "Parmesan", "Cafe", "Croissant", "Beurre", "Sel fin"};

	private final ProductService productService = mock(ProductService.class);
	private final Set<Long> catalog = new java.util.HashSet<>(Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
	private RecommendationService service;

	private static TrainingData data() {
		List<ProductFeatures> products = new ArrayList<>();
		for (long id = 1; id <= 7; id++) {
			products.add(new ProductFeatures(id, NAMES[(int) id], "produit " + NAMES[(int) id]));
		}
		List<Basket> baskets = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			Map<Long, Integer> items = i % 2 == 0 ? Map.of(1L, 1, 2L, 1, 3L, 1) : Map.of(4L, 2, 5L, 1, 6L, 1);
			baskets.add(new Basket((long) i, NOW.minusDays(i % 30), items));
		}
		baskets.add(new Basket(999L, NOW, Map.of(4L, 5)));  // le cafe est la meilleure vente
		return new TrainingData(NOW, products, baskets);
	}

	private static ProductResponse product(long id) {
		ProductResponse p = new ProductResponse();
		p.setId(id);
		p.setName(NAMES[(int) id]);
		p.setPrice(BigDecimal.ONE);
		return p;
	}

	@BeforeEach
	void setUp() {
		RecommendationProperties properties = new RecommendationProperties(
				Map.of("item2vec", 0.6, "content", 0.25, "popularity", 0.15),
				new RecommendationProperties.Item2Vec(16, 30, 0.05, 5, 42L),
				new RecommendationProperties.Popularity(Duration.ofDays(30)));
		when(productService.getByIds(anyCollection())).thenAnswer(inv -> ((Collection<Long>) inv.getArgument(0)).stream()
				.filter(catalog::contains).map(RecommendationServiceTest::product).toList());
		when(productService.getById(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(inv -> {
			long id = inv.getArgument(0);
			if (!catalog.contains(id)) {
				throw new NotFoundException("Product not found");
			}
			return product(id);
		});
		service = new RecommendationService(RecommendationServiceTest::data,
				List.of(new Item2VecStrategy(properties), new ContentSimilarityStrategy(), new PopularityStrategy(properties)),
				properties, productService);
		service.retrain();
	}

	private static List<Long> ids(RecommendationResponse response) {
		return response.recommendations().stream().map(RecommendedProduct::productId).toList();
	}

	@Test
	@DisplayName("produit consulte : les complements achetes ensemble arrivent en tete, avec leur raison")
	void similarProducts() {
		RecommendationResponse response = service.similarTo(1L, 3);

		assertThat(response.type()).isEqualTo("SIMILAR_PRODUCTS");
		assertThat(ids(response)).hasSize(3).doesNotContain(1L);
		assertThat(ids(response).subList(0, 2)).containsExactlyInAnyOrder(2L, 3L);
		assertThat(response.recommendations().get(0).reasons()).contains("BOUGHT_TOGETHER");
		assertThat(response.recommendations()).isSortedAccordingTo(
				(a, b) -> Double.compare(b.score(), a.score()));
	}

	@Test
	@DisplayName("panier : propose ce qui complete le panier, jamais ce qu'il contient deja")
	void basketCompletion() {
		RecommendationResponse response = service.completeBasket(List.of(4L, 5L), 2);

		assertThat(response.type()).isEqualTo("BASKET_COMPLETION");
		assertThat(ids(response)).first().isEqualTo(6L);
		assertThat(ids(response)).doesNotContain(4L, 5L);
	}

	@Test
	@DisplayName("meilleures ventes : classement par popularite, raison BEST_SELLER")
	void topSellers() {
		RecommendationResponse response = service.topSellers(3);

		assertThat(ids(response)).first().isEqualTo(4L);
		assertThat(ids(response)).doesNotContain(7L);
		assertThat(response.recommendations()).allSatisfy(r -> assertThat(r.reasons()).containsExactly("BEST_SELLER"));
	}

	@Test
	@DisplayName("un produit supprime du catalogue depuis l'entrainement n'est plus recommande")
	void deletedProductIsFiltered() {
		catalog.remove(2L);

		assertThat(ids(service.similarTo(1L, 5))).doesNotContain(2L).contains(3L);
	}

	@Test
	@DisplayName("la limite demandee est respectee, meme superieure au nombre de candidats")
	void limit() {
		assertThat(ids(service.similarTo(1L, 1))).hasSize(1);
		assertThat(ids(service.similarTo(1L, 50))).hasSizeLessThanOrEqualTo(6);
	}

	@Test
	@DisplayName("erreurs : produit inconnu -> 404, panier avec produit inconnu -> INVALID_REFERENCE")
	void errors() {
		assertThatThrownBy(() -> service.similarTo(42L, 3)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.completeBasket(List.of(1L, 42L), 3))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFERENCE);
	}

	@Test
	@DisplayName("etat du modele : volume de donnees, poids normalises et indicateurs par strategie")
	void modelInfo() {
		var info = service.modelInfo();

		assertThat(info.salesCount()).isEqualTo(201);
		assertThat(info.productCount()).isEqualTo(7);
		assertThat(info.weights()).containsEntry("item2vec", 0.6).containsEntry("content", 0.25).containsEntry("popularity", 0.15);
		assertThat(info.strategies()).containsKeys("item2vec", "content", "popularity");
	}
}