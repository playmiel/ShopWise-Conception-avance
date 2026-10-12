package com.shopwise.app.recommendation.data;

/**
 * Source des donnees d'entrainement (US 8). Pour integrer une nouvelle source (ventes en ligne,
 * fichier d'import, entrepot de donnees...), il suffit de fournir une autre implementation.
 */
public interface TrainingDataProvider {

	TrainingData load();
}