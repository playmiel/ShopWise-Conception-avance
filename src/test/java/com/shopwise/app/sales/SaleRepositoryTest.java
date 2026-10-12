package com.shopwise.app.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.shopwise.app.sales.dto.response.SaleSummaryResponse;
import com.shopwise.app.sales.entity.Sale;
import com.shopwise.app.sales.repository.SaleRepository;

/**
 * Integration JPA + schema Flyway (V3) : cascade des lignes, requete de resume,
 * chargement du detail et contraintes d'integrite.
 * Les produits 1 a 4 proviennent de la migration V2.
 */
@DataJpaTest
@DisplayName("Integration - SaleRepository")
class SaleRepositoryTest {

	@Autowired
	private SaleRepository saleRepository;

	private Sale sale(LocalDateTime date, long productId, int quantity, String unitPrice) {
		Sale sale = new Sale(date);
		sale.addLine(productId, "Produit " + productId, quantity, new BigDecimal(unitPrice));
		return sale;
	}

	@Test
	@DisplayName("save : la vente et ses lignes sont persistees ensemble (cascade)")
	void save_cascadesLines() {
		Sale sale = new Sale(LocalDateTime.of(2026, 10, 1, 10, 0));
		sale.addLine(1L, "iPhone 15", 1, new BigDecimal("999.99"));
		sale.addLine(4L, "Dell XPS 13", 2, new BigDecimal("1299.99"));

		Long id = saleRepository.saveAndFlush(sale).getId();

		Sale reloaded = saleRepository.findWithLinesById(id).orElseThrow();
		assertThat(reloaded.getLines()).hasSize(2).allSatisfy(l -> assertThat(l.getId()).isNotNull());
		assertThat(reloaded.getTotalAmount()).isEqualByComparingTo("3599.97");
	}

	@Test
	@DisplayName("findAllSummaries : plus recentes en premier, avec nombre de lignes et d'articles")
	void findAllSummaries_sortedByDateDesc() {
		Sale older = saleRepository.save(sale(LocalDateTime.of(2026, 1, 10, 9, 0), 1L, 2, "10.00"));
		Sale newer = new Sale(LocalDateTime.of(2026, 3, 5, 18, 0));
		newer.addLine(2L, "A", 1, new BigDecimal("5.00"));
		newer.addLine(3L, "B", 4, new BigDecimal("2.50"));
		newer = saleRepository.save(newer);
		saleRepository.flush();

		List<SaleSummaryResponse> summaries = saleRepository.findAllSummaries();

		assertThat(summaries).extracting(SaleSummaryResponse::id).containsExactly(newer.getId(), older.getId());
		SaleSummaryResponse first = summaries.get(0);
		assertThat(first.lineCount()).isEqualTo(2L);
		assertThat(first.itemCount()).isEqualTo(5L);
		assertThat(first.totalAmount()).isEqualByComparingTo("15.00");
	}

	@Test
	@DisplayName("findWithLinesById : vide pour un identifiant inconnu")
	void findWithLinesById_unknown() {
		assertThat(saleRepository.findWithLinesById(123_456L)).isEmpty();
	}

	@Test
	@DisplayName("contrainte : une ligne ne peut pas referencer un produit inexistant")
	void foreignKeyOnProduct() {
		Sale sale = sale(LocalDateTime.of(2026, 2, 1, 12, 0), 999_999L, 1, "1.00");

		assertThatThrownBy(() -> saleRepository.saveAndFlush(sale))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}