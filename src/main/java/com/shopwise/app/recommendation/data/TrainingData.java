package com.shopwise.app.recommendation.data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Jeu d'entrainement, independant de la provenance des donnees (US 8) :
 * le catalogue (caracteristiques des produits) et les paniers (ventes passees).
 *
 * @param referenceDate date de reference de l'entrainement (calcul de l'anciennete des ventes)
 */
public record TrainingData(LocalDateTime referenceDate, List<ProductFeatures> products, List<Basket> baskets) {

	public TrainingData {
		products = List.copyOf(products);
		baskets = List.copyOf(baskets);
	}

	/** Caracteristiques d'un produit exploitees par les algorithmes. */
	public record ProductFeatures(Long id, String name, String description) {
	}

	/** Panier : produits achetes ensemble lors d'une vente, avec leur quantite. */
	public record Basket(Long saleId, LocalDateTime date, Map<Long, Integer> quantities) {

		public Basket {
			quantities = Map.copyOf(quantities);
		}
	}
}