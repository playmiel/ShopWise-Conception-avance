package com.shopwise.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopwise.app.catalog.dto.request.CreateProductRequest;
import com.shopwise.app.catalog.dto.request.UpdateProductRequest;
import com.shopwise.app.catalog.dto.response.ProductResponse;
import com.shopwise.app.catalog.entity.Product;
import com.shopwise.app.shared.exception.NotFoundException;
import com.shopwise.app.catalog.mapper.ProductMapper;
import com.shopwise.app.catalog.repository.ProductRepository;
import com.shopwise.app.catalog.service.ProductServiceImpl;

/**
 * Tests unitaires du service catalogue : la base est simulee (Mockito), le mapper est
 * l'implementation reelle generee par MapStruct afin de verifier aussi la conversion DTO/entite.
 */
@ExtendWith(MockitoExtension.class)
@Tag("non-regression")
@DisplayName("Unitaire - ProductServiceImpl")
class ProductServiceImplTest {

	@Mock
	private ProductRepository productRepository;

	private final ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

	private ProductServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new ProductServiceImpl(productRepository, productMapper);
	}

	private static Product product(long id, String name, String price) {
		LocalDateTime now = LocalDateTime.now();
		return new Product(id, name, "desc " + name, new BigDecimal(price), now, now);
	}

	@Test
	@DisplayName("create : convertit la requete, sauvegarde et renvoie le produit persiste")
	void create_savesAndReturnsProduct() {
		CreateProductRequest request = new CreateProductRequest();
		request.setName("Pain");
		request.setDescription("Complet");
		request.setPrice(new BigDecimal("2.40"));
		when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
			Product p = inv.getArgument(0);
			assertThat(p.getId()).as("l'id est genere par la base, pas par le client").isNull();
			p.setId(10L);
			return p;
		});

		ProductResponse response = service.create(request);

		ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo("Pain");
		assertThat(response.getId()).isEqualTo(10L);
		assertThat(response.getPrice()).isEqualByComparingTo("2.40");
	}

	@Test
	@DisplayName("getById : renvoie le produit quand il existe")
	void getById_returnsProduct() {
		when(productRepository.findById(1L)).thenReturn(Optional.of(product(1, "Riz", "1.99")));

		ProductResponse response = service.getById(1L);

		assertThat(response.getName()).isEqualTo("Riz");
		assertThat(response.getDescription()).isEqualTo("desc Riz");
	}

	@Test
	@DisplayName("getById : leve NotFoundException quand le produit n'existe pas")
	void getById_throwsWhenMissing() {
		when(productRepository.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getById(42L))
				.isInstanceOf(NotFoundException.class)
				.hasMessage("Product not found");
	}

	@Test
	@DisplayName("getAll : renvoie tous les produits convertis")
	void getAll_returnsAllProducts() {
		when(productRepository.findAll()).thenReturn(List.of(product(1, "A", "1.00"), product(2, "B", "2.00")));

		assertThat(service.getAll()).extracting(ProductResponse::getName).containsExactly("A", "B");
	}

	@Test
	@DisplayName("update : applique les nouvelles valeurs sur l'entite existante")
	void update_appliesChanges() {
		Product existing = product(5, "Ancien", "1.00");
		when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
		when(productRepository.save(existing)).thenReturn(existing);
		UpdateProductRequest request = new UpdateProductRequest();
		request.setName("Nouveau");
		request.setDescription("maj");
		request.setPrice(new BigDecimal("3.00"));

		ProductResponse response = service.update(5L, request);

		assertThat(existing.getId()).as("l'identifiant n'est pas modifiable").isEqualTo(5L);
		assertThat(response.getName()).isEqualTo("Nouveau");
		assertThat(response.getPrice()).isEqualByComparingTo("3.00");
	}

	@Test
	@DisplayName("update : leve NotFoundException et ne sauvegarde rien si le produit n'existe pas")
	void update_throwsWhenMissing() {
		when(productRepository.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(9L, new UpdateProductRequest()))
				.isInstanceOf(NotFoundException.class);
		verify(productRepository, never()).save(any());
	}

	@Test
	@DisplayName("delete : supprime le produit existant")
	void delete_removesProduct() {
		when(productRepository.existsById(3L)).thenReturn(true);

		service.delete(3L);

		verify(productRepository).deleteById(3L);
	}

	@Test
	@DisplayName("delete : leve NotFoundException et ne supprime rien si le produit n'existe pas")
	void delete_throwsWhenMissing() {
		when(productRepository.existsById(3L)).thenReturn(false);

		assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(NotFoundException.class);
		verify(productRepository, never()).deleteById(any());
	}
}