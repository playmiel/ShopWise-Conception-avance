package com.shopwise.app.recommendation;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * API du module Recommandations (US 7). Chaque test enregistre des ventes via l'API, reentraine le
 * modele, puis interroge les recommandations : le chemin complet ventes -> apprentissage -> API.
 * Produits (migration V2) : 1 iPhone 15, 2 Galaxy S24, 3 MacBook Pro, 4 Dell XPS 13.
 * Habitudes simulees : 1 achete avec 3 (Apple), 2 achete avec 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin", roles = "ADMIN")
@Tag("api")
@DisplayName("API - recommandations /api/recommendations (US 7)")
class RecommendationApiTest {

	@Autowired
	private MockMvc mockMvc;

	private void sale(long a, long b) throws Exception {
		mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON)
				.content("{\"lines\":[{\"productId\":%d,\"quantity\":1},{\"productId\":%d,\"quantity\":1}]}".formatted(a, b)))
				.andExpect(status().isCreated());
	}

	@BeforeEach
	void salesHistoryAndTraining() throws Exception {
		for (int i = 0; i < 15; i++) {
			sale(1, 3);
			sale(2, 4);
		}
		sale(1, 2);
		mockMvc.perform(post("/api/recommendations/model/retrain"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.salesCount").value(31));
	}

	@Test
	@DisplayName("recommandations pour un produit : le produit le plus achete avec lui arrive en tete")
	void forProduct() throws Exception {
		mockMvc.perform(get("/api/recommendations/products/1").param("limit", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("SIMILAR_PRODUCTS"))
				.andExpect(jsonPath("$.basedOn[0]").value(1))
				.andExpect(jsonPath("$.modelTrainedAt", notNullValue()))
				.andExpect(jsonPath("$.recommendations", hasSize(2)))
				.andExpect(jsonPath("$.recommendations[0].productId").value(3))
				.andExpect(jsonPath("$.recommendations[0].name").value("MacBook Pro 16\""))
				.andExpect(jsonPath("$.recommendations[0].price").value(2499.99))
				.andExpect(jsonPath("$.recommendations[0].score", notNullValue()))
				.andExpect(jsonPath("$.recommendations[0].reasons", hasItem("BOUGHT_TOGETHER")))
				.andExpect(jsonPath("$.recommendations[*].productId", not(hasItem(1))));
	}

	@Test
	@DisplayName("completion de panier")
	void forBasket() throws Exception {
		mockMvc.perform(get("/api/recommendations/basket").param("productIds", "2").param("limit", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("BASKET_COMPLETION"))
				.andExpect(jsonPath("$.recommendations[0].productId").value(4));
	}

	@Test
	@DisplayName("meilleures ventes")
	void topSellers() throws Exception {
		mockMvc.perform(get("/api/recommendations/top-sellers").param("limit", "3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("TOP_SELLERS"))
				.andExpect(jsonPath("$.recommendations", hasSize(lessThanOrEqualTo(3))))
				.andExpect(jsonPath("$.recommendations[0].reasons[0]").value("BEST_SELLER"));
	}

	@Test
	@DisplayName("etat du modele : volume de donnees et indicateurs du reseau")
	void modelInfo() throws Exception {
		mockMvc.perform(get("/api/recommendations/model"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.salesCount").value(31))
				.andExpect(jsonPath("$.weights.item2vec").value(0.6))
				.andExpect(jsonPath("$.strategies.item2vec.finalLoss", notNullValue()))
				.andExpect(jsonPath("$.strategies.content.vocabularySize", notNullValue()))
				.andExpect(jsonPath("$.strategies.popularity.productsWithSales").value(4));
	}

	@Test
	@DisplayName("404 pour un produit inconnu")
	void unknownProduct() throws Exception {
		mockMvc.perform(get("/api/recommendations/products/9999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	@DisplayName("400 pour une limite hors bornes ou un panier invalide")
	void invalidParameters() throws Exception {
		mockMvc.perform(get("/api/recommendations/top-sellers").param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(get("/api/recommendations/basket").param("productIds", "1,777"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REFERENCE"));
	}

	@Test
	@DisplayName("lecture ouverte a tous les roles, reentrainement reserve a l'ADMIN (403)")
	void security() throws Exception {
		mockMvc.perform(get("/api/recommendations/top-sellers").with(user("viewer").roles("VIEWER")))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/recommendations/model/retrain").with(user("merchant").roles("MERCHANT")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}
}