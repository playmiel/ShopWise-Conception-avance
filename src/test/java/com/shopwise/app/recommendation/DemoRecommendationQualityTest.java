package com.shopwise.app.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.service.ProductService;
import com.shopwise.app.recommendation.dto.ModelInfoResponse;
import com.shopwise.app.recommendation.dto.RecommendedProduct;
import com.shopwise.app.recommendation.service.RecommendationService;

/**
 * Test d'acceptation de la qualite du modele sur le jeu de demonstration (1500 ventes generees
 * par themes : petit-dejeuner, pates, apero, fromage et vin...). Le modele ne connait pas les
 * themes : il doit les retrouver. Base H2 dediee pour ne pas polluer les autres tests.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo_quality;DB_CLOSE_DELAY=-1")
@ActiveProfiles("demo")
@Tag("ml")
@DisplayName("Acceptation ML - qualite des recommandations sur le jeu de demonstration")
class DemoRecommendationQualityTest {

	@Autowired
	private RecommendationService recommendationService;

	@Autowired
	private ProductService productService;

	private List<String> top3(String productName) {
		Map<String, Long> ids = productService.getAll().stream()
				.collect(Collectors.toMap(ProductResponse::getName, ProductResponse::getId, (a, b) -> a));
		return recommendationService.similarTo(ids.get(productName), 3).recommendations().stream()
				.map(RecommendedProduct::name).toList();
	}

	@Test
	@DisplayName("le modele est entraine sur les 1500 ventes et sa perte diminue")
	void trainedOnDemoData() {
		ModelInfoResponse info = recommendationService.modelInfo();

		assertThat(info.salesCount()).isEqualTo(1500);
		Map<String, Object> item2vec = info.strategies().get("item2vec");
		assertThat((Double) item2vec.get("finalLoss")).isLessThan((Double) item2vec.get("initialLoss"));
	}

	@Test
	@DisplayName("pates : spaghetti -> sauce tomate, parmesan ou huile d'olive")
	void pasta() {
		assertThat(top3("Spaghetti")).allMatch(name -> List.of("Sauce tomate basilic", "Parmesan rape",
				"Huile d'olive vierge extra").contains(name));
	}

	@Test
	@DisplayName("apero : biere -> chips, olives, saucisson")
	void aperitif() {
		assertThat(top3("Biere blonde")).containsExactlyInAnyOrder("Chips nature", "Olives vertes", "Saucisson sec");
	}

	@Test
	@DisplayName("petit-dejeuner : cafe -> produits du petit-dejeuner")
	void breakfast() {
		assertThat(top3("Cafe moulu arabica")).allMatch(name -> List.of("Baguette tradition", "Croissant pur beurre",
				"Beurre doux", "Confiture de fraises", "Lait demi-ecreme").contains(name));
	}

	@Test
	@DisplayName("fromage et vin : vin rouge -> camembert et pain de campagne en tete")
	void cheeseAndWine() {
		assertThat(top3("Vin rouge Bordeaux").subList(0, 2))
				.containsExactlyInAnyOrder("Camembert de Normandie", "Pain de campagne");
	}
}