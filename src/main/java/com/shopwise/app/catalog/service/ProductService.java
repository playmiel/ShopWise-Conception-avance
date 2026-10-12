package com.shopwise.app.catalog.service;

import java.util.Collection;
import java.util.List;

import com.shopwise.app.catalog.dto.request.CreateProductRequest;
import com.shopwise.app.catalog.dto.request.UpdateProductRequest;
import com.shopwise.app.catalog.dto.response.ProductResponse;

public interface ProductService {
	
    public ProductResponse create(CreateProductRequest request);

    public ProductResponse getById(Long id);

	public List<ProductResponse> getAll();
    
    public ProductResponse update(Long id, UpdateProductRequest request);

    public void delete(Long id);

    /** Produits correspondant aux identifiants donnes (les identifiants inconnus sont ignores). */
    List<ProductResponse> getByIds(Collection<Long> ids);
}
