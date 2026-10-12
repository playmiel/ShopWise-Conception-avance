package com.shopwise.app.recommendation.algorithm;

import java.util.List;

import org.springframework.stereotype.Component;

import com.shopwise.app.recommendation.config.RecommendationProperties;
import com.shopwise.app.recommendation.data.TrainingData;

/** Strategie "achetes ensemble" : entraine le reseau Item2Vec sur les paniers d'au moins 2 produits. */
@Component
public class Item2VecStrategy implements RecommendationStrategy {

	public static final String NAME = "item2vec";

	private final RecommendationProperties.Item2Vec params;

	public Item2VecStrategy(RecommendationProperties properties) {
		this.params = properties.item2vec();
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String reason() {
		return "BOUGHT_TOGETHER";
	}

	@Override
	public Scorer train(TrainingData data) {
		List<List<Long>> baskets = data.baskets().stream()
				.map(basket -> basket.quantities().keySet().stream().sorted().toList())
				.filter(basket -> basket.size() >= 2)
				.toList();
		return Item2VecModel.train(baskets, params);
	}
}