package com.shopwise.app.catalog.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.shopwise.app.catalog.dto.request.CreateProductRequest;
import com.shopwise.app.catalog.dto.request.UpdateProductRequest;
import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.entity.Product;
import com.shopwise.app.shared.exception.NotFoundException;
import com.shopwise.app.catalog.mapper.ProductMapper;
import com.shopwise.app.catalog.repository.ProductRepository;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

	private final ProductRepository productRepository;
	private final ProductMapper productMapper;
	
	public ProductServiceImpl(ProductRepository productRepository, ProductMapper productMapper) {
		super();
		this.productRepository = productRepository;
		this.productMapper = productMapper;
	}
	
    public ProductResponse create(CreateProductRequest request) {
        Product product = productMapper.toEntity(request);
        Product saved = productRepository.save(product);
        return productMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        return productMapper.toResponse(product);
    }

	@Transactional(readOnly = true)
	public List<ProductResponse> getAll() {
	    List<Product> products = productRepository.findAll();
	    return productMapper.toResponseList(products);
	}

    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found"));

        productMapper.updateEntity(request, product);

        Product updated = productRepository.save(product);
        return productMapper.toResponse(updated);
    }

    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new NotFoundException("Product not found");
        }
        productRepository.deleteById(id);
        // flush immediat : un produit reference par une vente leve ici une violation de contrainte (-> 409)
        productRepository.flush();
    }
	

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productMapper.toResponseList(productRepository.findAllById(ids));
    }
}
