package com.shopwise.app.sales.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import com.shopwise.app.sales.dto.response.SaleLineResponse;
import com.shopwise.app.sales.dto.response.SaleResponse;
import com.shopwise.app.sales.entity.Sale;
import com.shopwise.app.sales.entity.SaleLine;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SaleMapper {

	SaleResponse toResponse(Sale sale);

	SaleLineResponse toLineResponse(SaleLine line);
}