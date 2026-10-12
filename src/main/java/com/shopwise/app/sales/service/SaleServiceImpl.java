package com.shopwise.app.sales.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.service.ProductService;
import com.shopwise.app.sales.dto.request.CreateSaleRequest;
import com.shopwise.app.sales.dto.request.SaleLineRequest;
import com.shopwise.app.sales.dto.response.SaleResponse;
import com.shopwise.app.sales.dto.response.SaleSummaryResponse;
import com.shopwise.app.sales.dto.response.SoldItem;
import com.shopwise.app.sales.entity.Sale;
import com.shopwise.app.sales.mapper.SaleMapper;
import com.shopwise.app.sales.repository.SaleRepository;
import com.shopwise.app.shared.exception.ApiException;
import com.shopwise.app.shared.exception.ErrorCode;
import com.shopwise.app.shared.exception.NotFoundException;

@Service
@Transactional
public class SaleServiceImpl implements SaleService {

	private final SaleRepository saleRepository;
	private final SaleMapper saleMapper;
	private final ProductService productService;
	private final Clock clock;

	public SaleServiceImpl(SaleRepository saleRepository, SaleMapper saleMapper, ProductService productService,
			Clock clock) {
		this.saleRepository = saleRepository;
		this.saleMapper = saleMapper;
		this.productService = productService;
		this.clock = clock;
	}

	@Override
	public SaleResponse create(CreateSaleRequest request) {
		List<Long> requestedIds = request.lines().stream().map(SaleLineRequest::productId).toList();
		Set<Long> distinctIds = new LinkedHashSet<>(requestedIds);
		if (distinctIds.size() != requestedIds.size()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"Each product may appear only once in a sale: merge the quantities into a single line");
		}

		// Une seule requete pour tous les produits de la vente
		Map<Long, ProductResponse> products = productService.getByIds(distinctIds).stream()
				.collect(Collectors.toMap(ProductResponse::getId, Function.identity()));
		List<Long> unknownIds = distinctIds.stream().filter(id -> !products.containsKey(id)).toList();
		if (!unknownIds.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_REFERENCE, "Unknown product id(s): " + unknownIds);
		}

		Sale sale = new Sale(LocalDateTime.now(clock));
		for (SaleLineRequest line : request.lines()) {
			ProductResponse product = products.get(line.productId());
			sale.addLine(product.getId(), product.getName(), line.quantity(), product.getPrice());
		}
		return saleMapper.toResponse(saleRepository.save(sale));
	}

	@Override
	@Transactional(readOnly = true)
	public List<SaleSummaryResponse> getAll() {
		return saleRepository.findAllSummaries();
	}

	@Override
	@Transactional(readOnly = true)
	public SaleResponse getById(Long id) {
		return saleRepository.findWithLinesById(id)
				.map(saleMapper::toResponse)
				.orElseThrow(() -> new NotFoundException("Sale not found"));
	}

	@Override
	@Transactional(readOnly = true)
	public List<SoldItem> getSoldItems() {
		return saleRepository.findAllSoldItems();
	}
}
