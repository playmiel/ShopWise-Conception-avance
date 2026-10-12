package com.shopwise.app.catalog.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.shopwise.app.catalog.dto.request.CreateProductRequest;
import com.shopwise.app.catalog.dto.request.UpdateProductRequest;
import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.entity.Product;

/**
 * Conversion DTO <-> entite. unmappedTargetPolicy = ERROR : un champ ajoute a l'entite sans
 * etre mappe fait echouer la compilation au lieu d'etre silencieusement ignore.
 * L'identifiant et les dates sont geres par la base : jamais recopies depuis une requete.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	Product toEntity(CreateProductRequest dto);

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "createdAt", ignore = true)
	@Mapping(target = "updatedAt", ignore = true)
	void updateEntity(UpdateProductRequest dto, @MappingTarget Product entity);

	ProductResponse toResponse(Product product);

	List<ProductResponse> toResponseList(List<Product> products);
}