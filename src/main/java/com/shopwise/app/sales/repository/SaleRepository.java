package com.shopwise.app.sales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.shopwise.app.sales.dto.response.SaleSummaryResponse;
import com.shopwise.app.sales.entity.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {

	/** Vente et lignes chargees en une seule requete (jointure), pour le detail. */
	@EntityGraph(attributePaths = "lines")
	Optional<Sale> findWithLinesById(Long id);

	/** Resume de toutes les ventes, plus recentes en premier (l'id departage les ex aequo). */
	@Query("""
			select new com.shopwise.app.sales.dto.response.SaleSummaryResponse(
			    s.id, s.saleDate, s.createdBy, s.totalAmount, count(l), coalesce(sum(l.quantity), 0L))
			from Sale s left join s.lines l
			group by s.id, s.saleDate, s.createdBy, s.totalAmount
			order by s.saleDate desc, s.id desc
			""")
	List<SaleSummaryResponse> findAllSummaries();
}