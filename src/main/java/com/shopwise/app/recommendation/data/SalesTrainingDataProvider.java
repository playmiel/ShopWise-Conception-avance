package com.shopwise.app.recommendation.data;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.app.catalog.service.ProductService;
import com.shopwise.app.sales.dto.response.SoldItem;
import com.shopwise.app.sales.service.SaleService;

/**
 * Source par defaut : catalogue et historique des ventes de l'application. Le module ne lit les
 * donnees qu'a travers les API publiques des modules Catalogue et Ventes (pas leurs tables).
 */
@Component
public class SalesTrainingDataProvider implements TrainingDataProvider {

	private final ProductService productService;
	private final SaleService saleService;
	private final Clock clock;

	public SalesTrainingDataProvider(ProductService productService, SaleService saleService, Clock clock) {
		this.productService = productService;
		this.saleService = saleService;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public TrainingData load() {
		List<TrainingData.ProductFeatures> products = productService.getAll().stream()
				.map(p -> new TrainingData.ProductFeatures(p.getId(), p.getName(), p.getDescription()))
				.toList();

		Map<Long, LocalDateTime> dates = new LinkedHashMap<>();
		Map<Long, Map<Long, Integer>> baskets = new LinkedHashMap<>();
		for (SoldItem item : saleService.getSoldItems()) {
			dates.putIfAbsent(item.saleId(), item.saleDate());
			baskets.computeIfAbsent(item.saleId(), id -> new LinkedHashMap<>())
					.merge(item.productId(), item.quantity(), Integer::sum);
		}
		List<TrainingData.Basket> basketList = new ArrayList<>(baskets.size());
		baskets.forEach((saleId, items) -> basketList.add(new TrainingData.Basket(saleId, dates.get(saleId), items)));

		return new TrainingData(LocalDateTime.now(clock), products, basketList);
	}
}