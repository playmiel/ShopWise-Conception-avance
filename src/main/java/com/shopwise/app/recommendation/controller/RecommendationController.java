package com.shopwise.app.recommendation.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.app.recommendation.dto.ModelInfoResponse;
import com.shopwise.app.recommendation.dto.RecommendationResponse;
import com.shopwise.app.recommendation.service.RecommendationService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Points d'acces du module Recommandations (US 7). */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	/** Meilleures ventes recentes. */
	@GetMapping("/top-sellers")
	public RecommendationResponse topSellers(@RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
		return recommendationService.topSellers(limit);
	}

	/** Produits a proposer a un client qui consulte ou achete le produit donne. */
	@GetMapping("/products/{productId}")
	public RecommendationResponse forProduct(@PathVariable Long productId,
			@RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
		return recommendationService.similarTo(productId, limit);
	}

	/** Produits a proposer pour completer un panier : ?productIds=1,2,3 */
	@GetMapping("/basket")
	public RecommendationResponse forBasket(@RequestParam @NotEmpty @Size(max = 50) List<Long> productIds,
			@RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
		return recommendationService.completeBasket(productIds, limit);
	}

	/** Etat et indicateurs d'entrainement du modele. */
	@GetMapping("/model")
	public ModelInfoResponse model() {
		return recommendationService.modelInfo();
	}

	/** Reentraine le modele immediatement (ADMIN). */
	@PostMapping("/model/retrain")
	public ModelInfoResponse retrain() {
		return recommendationService.retrain();
	}
}