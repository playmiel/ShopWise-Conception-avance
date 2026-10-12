package com.shopwise.app.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopwise.app.recommendation.algorithm.Item2VecModel;
import com.shopwise.app.recommendation.config.RecommendationProperties;

/**
 * Reseau Item2Vec sur des paniers synthetiques a deux "familles" : {1,2,3} et {4,5,6}.
 * Le modele ne connait pas les familles ; il doit les retrouver a partir des co-achats.
 */
@DisplayName("Unitaire - reseau Item2Vec")
class Item2VecModelTest {

	private static final RecommendationProperties.Item2Vec PARAMS = new RecommendationProperties.Item2Vec(16, 30, 0.05, 5, 42L);

	private static List<List<Long>> twoFamilies() {
		Random random = new Random(7);
		List<List<Long>> baskets = new ArrayList<>();
		long[][] families = {{1, 2, 3}, {4, 5, 6}};
		for (int i = 0; i < 300; i++) {
			long[] family = families[i % 2];
			List<Long> basket = new ArrayList<>();
			for (long id : family) {
				if (random.nextDouble() < 0.7) {
					basket.add(id);
				}
			}
			if (basket.size() < 2) {
				basket = List.of(family[0], family[1]);
			}
			baskets.add(basket);
		}
		return baskets;
	}

	@Test
	@DisplayName("des produits achetes ensemble ont des embeddings plus proches que des produits jamais associes")
	void learnsCoPurchases() {
		Item2VecModel model = Item2VecModel.train(twoFamilies(), PARAMS);

		for (long a : new long[] {1, 2, 3}) {
			for (long b : new long[] {1, 2, 3}) {
				for (long other : new long[] {4, 5, 6}) {
					if (a != b) {
						assertThat(model.similarity(a, b))
								.as("sim(%d,%d) > sim(%d,%d)", a, b, a, other)
								.isGreaterThan(model.similarity(a, other));
					}
				}
			}
		}
	}

	@Test
	@DisplayName("la perte d'entropie croisee diminue au fil des epoques (le reseau apprend)")
	void lossDecreases() {
		Item2VecModel model = Item2VecModel.train(twoFamilies(), PARAMS);

		List<Double> loss = model.lossHistory();
		assertThat(loss).hasSize(30);
		assertThat(loss.get(loss.size() - 1)).isLessThan(loss.get(0));
		assertThat(model.diagnostics()).containsKeys("initialLoss", "finalLoss", "vocabularySize");
	}

	@Test
	@DisplayName("entrainement deterministe : meme graine, memes resultats")
	void deterministic() {
		Item2VecModel first = Item2VecModel.train(twoFamilies(), PARAMS);
		Item2VecModel second = Item2VecModel.train(twoFamilies(), PARAMS);

		assertThat(first.similarity(1L, 2L)).isEqualTo(second.similarity(1L, 2L));
		assertThat(first.lossHistory()).isEqualTo(second.lossHistory());
	}

	@Test
	@DisplayName("score dans [0,1] et 0 pour un produit inconnu du modele (demarrage a froid)")
	void scoreBoundsAndUnknownProduct() {
		Item2VecModel model = Item2VecModel.train(twoFamilies(), PARAMS);

		assertThat(model.score(List.of(1L), 2L)).isBetween(0.0, 1.0);
		assertThat(model.score(List.of(1L), 99L)).isZero();
		assertThat(model.score(List.of(), 2L)).isZero();
		assertThat(model.knows(99L)).isFalse();
	}

	@Test
	@DisplayName("sans paniers exploitables : modele vide, aucune erreur")
	void emptyData() {
		Item2VecModel model = Item2VecModel.train(List.of(), PARAMS);

		assertThat(model.lossHistory()).isEmpty();
		assertThat(model.score(List.of(1L), 2L)).isZero();
	}
}