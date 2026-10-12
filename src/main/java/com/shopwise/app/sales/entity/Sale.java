package com.shopwise.app.sales.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Vente : agregat racine qui possede ses lignes.
 *
 * Le total n'est jamais fourni par le client : il est recalcule a chaque ajout de ligne
 * (US 1 : "le total est calcule automatiquement"). Les lignes ne sont modifiables qu'a
 * travers {@link #addLine}, ce qui garantit que le total reste coherent.
 * L'auteur (createdBy) est renseigne automatiquement par l'audit JPA (US 5).
 */
@Entity
@Table(name = "SALES")
@EntityListeners(AuditingEntityListener.class)
public class Sale {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sale_date", nullable = false)
	private LocalDateTime saleDate;

	@CreatedBy
	@Column(name = "created_by", length = 50, updatable = false)
	private String createdBy;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

	@OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<SaleLine> lines = new ArrayList<>();

	/** Reserve a JPA. */
	protected Sale() {
	}

	public Sale(LocalDateTime saleDate) {
		this.saleDate = Objects.requireNonNull(saleDate, "saleDate");
	}

	/**
	 * Ajoute une ligne en figeant le nom et le prix unitaire du produit a la date de la vente,
	 * puis recalcule le total.
	 */
	public SaleLine addLine(Long productId, String productName, int quantity, BigDecimal unitPrice) {
		SaleLine line = new SaleLine(this, productId, productName, quantity, unitPrice);
		lines.add(line);
		totalAmount = lines.stream()
				.map(SaleLine::getLineTotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
		return line;
	}

	public Long getId() {
		return id;
	}

	public LocalDateTime getSaleDate() {
		return saleDate;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public List<SaleLine> getLines() {
		return Collections.unmodifiableList(lines);
	}
}