package com.shopwise.app.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopwise.app.catalog.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

}
