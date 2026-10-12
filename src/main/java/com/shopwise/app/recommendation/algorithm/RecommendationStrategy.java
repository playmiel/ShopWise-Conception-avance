package com.shopwise.app.recommendation.algorithm;

import com.shopwise.app.recommendation.data.TrainingData;

/**
 * Algorithme de recommandation (pattern Strategy, US 8). Chaque implementation declaree comme
 * bean Spring est automatiquement entrainee et combinee dans le modele hybride, avec le poids
 * shopwise.recommendation.weights.&lt;name&gt;. Ajouter un algorithme = ajouter une classe.
 */
public interface RecommendationStrategy {

	/** Identifiant de la strategie (cle de son poids dans la configuration). */
	String name();

	/** Raison affichee quand cette strategie contribue fortement a une recommandation. */
	String reason();

	/** Apprend a partir des donnees et renvoie un modele immuable. */
	Scorer train(TrainingData data);
}