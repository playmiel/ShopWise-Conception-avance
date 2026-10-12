package com.shopwise.app.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.service.ProductService;
import com.shopwise.app.sales.dto.request.CreateSaleRequest;
import com.shopwise.app.sales.dto.request.SaleLineRequest;
import com.shopwise.app.sales.dto.response.SaleResponse;
import com.shopwise.app.sales.entity.Sale;
import com.shopwise.app.sales.mapper.SaleMapper;
import com.shopwise.app.sales.repository.SaleRepository;
import com.shopwise.app.sales.service.SaleServiceImpl;
import com.shopwise.app.shared.exception.ApiException;
import com.shopwise.app.shared.exception.ErrorCode;
import com.shopwise.app.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unitaire - SaleServiceImpl")
class SaleServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-10-12T08:30:00Z");

	@Mock
	private SaleRepository saleRepository;

	@Mock
	private ProductService productService;

	private final SaleMapper saleMapper = Mappers.getMapper(SaleMapper.class);

	private SaleServiceImpl service;

	@BeforeEach
	void setUp() {
		Clock fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
		service = new SaleServiceImpl(saleRepository, saleMapper, productService, fixedClock);
	}

	private static ProductResponse product(long id, String name, String price) {
		ProductResponse p = new ProductResponse();
		p.setId(id);
		p.setName(name);
		p.setPrice(new BigDecimal(price));
		return p;
	}

	private static CreateSaleRequest request(SaleLineRequest... lines) {
		return new CreateSaleRequest(List.of(lines));
	}

	@Test
	@DisplayName("create : prix lus dans le catalogue, total calcule, date fournie par l'horloge")
	void create_usesCatalogPricesAndClock() {
		when(productService.getByIds(anyCollection()))
				.thenReturn(List.of(product(1, "Pain", "1.20"), product(2, "Lait", "1.35")));
		when(saleRepository.save(any(Sale.class))).thenAnswer(inv -> inv.getArgument(0));

		SaleResponse response = service.create(request(new SaleLineRequest(1L, 3), new SaleLineRequest(2L, 2)));

		assertThat(response.saleDate()).isEqualTo(LocalDateTime.of(2026, 10, 12, 8, 30));
		assertThat(response.totalAmount()).isEqualByComparingTo("6.30");
		assertThat(response.lines()).extracting(l -> l.productName()).containsExactly("Pain", "Lait");
		assertThat(response.lines().get(0).unitPrice()).isEqualByComparingTo("1.20");
		assertThat(response.lines().get(0).lineTotal()).isEqualByComparingTo("3.60");
	}

	@Test
	@DisplayName("create : refuse un produit present deux fois (VALIDATION_ERROR), sans interroger le catalogue")
	void create_rejectsDuplicateProduct() {
		assertThatThrownBy(() -> service.create(request(new SaleLineRequest(1L, 1), new SaleLineRequest(1L, 2))))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getErrorCode())
				.isEqualTo(ErrorCode.VALIDATION_ERROR);
		verify(productService, never()).getByIds(anyCollection());
		verify(saleRepository, never()).save(any());
	}

	@Test
	@DisplayName("create : refuse un produit inconnu (INVALID_REFERENCE) et liste les identifiants en cause")
	void create_rejectsUnknownProduct() {
		when(productService.getByIds(anyCollection())).thenReturn(List.of(product(1, "Pain", "1.20")));

		assertThatThrownBy(() -> service.create(request(new SaleLineRequest(1L, 1), new SaleLineRequest(99L, 1))))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("99")
				.extracting(e -> ((ApiException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REFERENCE);
		verify(saleRepository, never()).save(any());
	}

	@Test
	@DisplayName("getById : leve NotFoundException si la vente n'existe pas")
	void getById_throwsWhenMissing() {
		when(saleRepository.findWithLinesById(7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getById(7L))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("Sale not found");
	}

	@Test
	@DisplayName("getById : renvoie la vente et ses lignes")
	void getById_returnsSale() {
		Sale sale = new Sale(LocalDateTime.of(2026, 1, 1, 10, 0));
		sale.addLine(1L, "Pain", 2, new BigDecimal("1.00"));
		when(saleRepository.findWithLinesById(3L)).thenReturn(Optional.of(sale));

		SaleResponse response = service.getById(3L);

		assertThat(response.totalAmount()).isEqualByComparingTo("2.00");
		assertThat(response.lines()).hasSize(1);
	}
}