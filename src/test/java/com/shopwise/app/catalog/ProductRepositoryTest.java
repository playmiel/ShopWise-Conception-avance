package com.shopwise.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.shopwise.app.catalog.entity.Product;
import com.shopwise.app.catalog.repository.ProductRepository;

/**
 * Test d'integration de la couche persistance (JPA + H2) : verifie le mapping de l'entite
 * Product sur la table PRODUCTS et le remplissage automatique des horodatages.
 */
@DataJpaTest
@Tag("non-regression")
@DisplayName("Integration - ProductRepository")
class ProductRepositoryTest {

	@Autowired
	private ProductRepository productRepository;

	@Test
	@DisplayName("le jeu de donnees initial (data.sql) est charge")
	void seedDataIsLoaded() {
		assertThat(productRepository.findAll())
				.extracting(Product::getName)
				.contains("iPhone 15", "Samsung Galaxy S24", "MacBook Pro 16\"", "Dell XPS 13");
	}

	@Test
	@DisplayName("save : genere l'identifiant et les dates de creation / mise a jour")
	void save_generatesIdAndTimestamps() {
		Product product = new Product();
		product.setName("Huile d'olive");
		product.setPrice(new BigDecimal("8.75"));

		Product saved = productRepository.saveAndFlush(product);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(productRepository.findById(saved.getId()))
				.get()
				.extracting(Product::getPrice)
				.satisfies(price -> assertThat(price).isEqualByComparingTo("8.75"));
	}

	@Test
	@DisplayName("deleteById : le produit n'existe plus ensuite")
	void deleteById_removesRow() {
		Product saved = productRepository.saveAndFlush(newProduct("Sucre", "1.10"));

		productRepository.deleteById(saved.getId());
		productRepository.flush();

		assertThat(productRepository.existsById(saved.getId())).isFalse();
	}

	private static Product newProduct(String name, String price) {
		Product p = new Product();
		p.setName(name);
		p.setPrice(new BigDecimal(price));
		return p;
	}
}