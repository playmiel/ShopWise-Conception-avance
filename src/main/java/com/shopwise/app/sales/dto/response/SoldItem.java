package com.shopwise.app.sales.dto.response;

import java.time.LocalDateTime;

/** Un produit vendu dans une vente : donnee elementaire exposee aux modules d'analyse (recommandations). */
public record SoldItem(Long saleId, LocalDateTime saleDate, Long productId, Integer quantity) {
}