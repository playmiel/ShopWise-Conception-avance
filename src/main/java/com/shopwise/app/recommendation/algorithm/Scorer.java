package com.shopwise.app.recommendation.algorithm;

import java.util.Collection;
import java.util.Map;

/** Resultat immuable de l'entrainement d'une strategie : sait noter un produit candidat. */
public interface Scorer {

	/**
	 * Score du candidat dans [0, 1] par rapport aux produits de depart.
	 * @param seeds produits de depart (produit consulte, panier) ; vide pour un score sans contexte
	 */
	double score(Collection<Long> seeds, Long candidate);

	/** Indicateurs d'entrainement exposes par GET /api/recommendations/model. */
	default Map<String, Object> diagnostics() {
		return Map.of();
	}
}