package com.shopwise.app.sales.service;

import java.util.List;

import com.shopwise.app.sales.dto.request.CreateSaleRequest;
import com.shopwise.app.sales.dto.response.SaleResponse;
import com.shopwise.app.sales.dto.response.SaleSummaryResponse;
import com.shopwise.app.sales.dto.response.SoldItem;

public interface SaleService {

	/** Enregistre une vente ; prix et total sont calcules a partir du catalogue (US 1). */
	SaleResponse create(CreateSaleRequest request);

	/** Toutes les ventes, plus recentes en premier (US 2). */
	List<SaleSummaryResponse> getAll();

	/** Detail d'une vente ; NotFoundException si elle n'existe pas (US 3). */
	SaleResponse getById(Long id);

	/** Historique des produits vendus (lecture seule), pour les modules d'analyse. */
	List<SoldItem> getSoldItems();
}
