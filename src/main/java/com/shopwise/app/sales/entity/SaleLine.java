package com.shopwise.app.sales.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ligne de vente : un produit, une quantite et le prix unitaire applique.
 *
 * Le produit est reference par son identifiant (cle etrangere en base) et non par une
 * association JPA vers l'entite Product : le module Ventes ne depend ainsi que de l'API
 * publique du catalogue (ProductService), pas de son modele de persistance.
 */
@Entity
@Table(name = "SALE_LINES")
public class SaleLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sale_id", nullable = false)
	private Sale sale;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "product_name", nullable = false, length = 100)
	private String productName;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "line_total", nullable = false, precision = 12, scale = 2)
	private BigDecimal lineTotal;

	/** Reserve a JPA. */
	protected SaleLine() {
	}

	SaleLine(Sale sale, Long productId, String productName, int quantity, BigDecimal unitPrice) {
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be greater than 0");
		}
		if (unitPrice == null || unitPrice.signum() < 0) {
			throw new IllegalArgumentException("unitPrice must be positive or zero");
		}
		this.sale = Objects.requireNonNull(sale, "sale");
		this.productId = Objects.requireNonNull(productId, "productId");
		this.productName = Objects.requireNonNull(productName, "productName");
		this.quantity = quantity;
		this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
		this.lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public String getProductName() {
		return productName;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}
}