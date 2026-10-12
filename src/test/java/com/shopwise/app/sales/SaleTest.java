package com.shopwise.app.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shopwise.app.sales.entity.Sale;
import com.shopwise.app.sales.entity.SaleLine;

@DisplayName("Unitaire - entite Sale (calcul du total)")
class SaleTest {

	private final Sale sale = new Sale(LocalDateTime.of(2026, 10, 1, 9, 30));

	@Test
	@DisplayName("une vente vide a un total de 0.00")
	void emptySale() {
		assertThat(sale.getTotalAmount()).isEqualByComparingTo("0.00");
		assertThat(sale.getLines()).isEmpty();
	}

	@Test
	@DisplayName("le total de ligne vaut prix unitaire x quantite")
	void lineTotal() {
		SaleLine line = sale.addLine(1L, "Pain", 3, new BigDecimal("1.20"));

		assertThat(line.getLineTotal()).isEqualByComparingTo("3.60");
		assertThat(line.getUnitPrice()).isEqualByComparingTo("1.20");
	}

	@Test
	@DisplayName("le total de la vente est la somme des lignes, recalcule a chaque ajout")
	void saleTotalIsSumOfLines() {
		sale.addLine(1L, "Pain", 3, new BigDecimal("1.20"));
		assertThat(sale.getTotalAmount()).isEqualByComparingTo("3.60");

		sale.addLine(2L, "Lait", 2, new BigDecimal("1.35"));
		assertThat(sale.getTotalAmount()).isEqualByComparingTo("6.30");
		assertThat(sale.getLines()).hasSize(2);
	}

	@Test
	@DisplayName("les montants sont arrondis au centime (pas d'erreur de virgule flottante)")
	void roundingToCents() {
		sale.addLine(1L, "Vrac", 3, new BigDecimal("0.10"));
		sale.addLine(2L, "Vrac 2", 1, new BigDecimal("0.20"));

		assertThat(sale.getTotalAmount()).isEqualByComparingTo("0.50");
		assertThat(sale.getTotalAmount().scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("refuse une quantite nulle ou negative")
	void rejectsNonPositiveQuantity() {
		assertThatThrownBy(() -> sale.addLine(1L, "Pain", 0, BigDecimal.ONE))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> sale.addLine(1L, "Pain", -2, BigDecimal.ONE))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(sale.getLines()).isEmpty();
	}

	@Test
	@DisplayName("refuse un prix unitaire negatif")
	void rejectsNegativePrice() {
		assertThatThrownBy(() -> sale.addLine(1L, "Pain", 1, new BigDecimal("-1")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("la liste des lignes n'est pas modifiable de l'exterieur")
	void linesAreReadOnly() {
		assertThatThrownBy(() -> sale.getLines().clear()).isInstanceOf(UnsupportedOperationException.class);
	}
}