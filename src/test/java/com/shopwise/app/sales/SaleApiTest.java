package com.shopwise.app.sales;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * Tests API du module Ventes : criteres d'acceptation des US 1, 2 et 3.
 * Produits disponibles (migration V2) : 1 iPhone 15 (999.99), 2 Galaxy S24 (899.99),
 * 3 MacBook Pro (2499.99), 4 Dell XPS 13 (1299.99).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// Securite (US 4/5) : execution en ADMIN ; le controle d'acces est teste par SecurityApiTest et AuthorizationMatrixTest
@WithMockUser(username = "admin", roles = "ADMIN")
@Tag("api")
@DisplayName("API - ventes /api/sales (US 1 a 3)")
class SaleApiTest {

	private static final String URL = "/api/sales";

	@Autowired
	private MockMvc mockMvc;

	private long createSale(String json) throws Exception {
		String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	@DisplayName("US 1 : enregistre une vente multi-produits, total calcule automatiquement")
	void createSale() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"lines":[{"productId":1,"quantity":2},{"productId":4,"quantity":1}]}"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", containsString("/api/sales/")))
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.saleDate", notNullValue()))
				.andExpect(jsonPath("$.createdBy").value("admin"))
				.andExpect(jsonPath("$.totalAmount").value(3299.97))
				.andExpect(jsonPath("$.lines", hasSize(2)))
				.andExpect(jsonPath("$.lines[0].productId").value(1))
				.andExpect(jsonPath("$.lines[0].productName").value("iPhone 15"))
				.andExpect(jsonPath("$.lines[0].quantity").value(2))
				.andExpect(jsonPath("$.lines[0].unitPrice").value(999.99))
				.andExpect(jsonPath("$.lines[0].lineTotal").value(1999.98));
	}

	@Test
	@DisplayName("US 1 : la vente est persistee, consultable via son URL Location")
	void createdSaleIsRetrievable() throws Exception {
		String location = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"lines":[{"productId":2,"quantity":1}]}"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalAmount").value(899.99));
	}

	@Test
	@DisplayName("US 1 : un prix envoye par le client est ignore, le prix du catalogue fait foi")
	void clientPriceIsIgnored() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"lines":[{"productId":1,"quantity":1,"unitPrice":0.01}],"totalAmount":0.01}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.lines[0].unitPrice").value(999.99))
				.andExpect(jsonPath("$.totalAmount").value(999.99));
	}

	@Test
	@DisplayName("US 1 : le prix est fige a la vente, un changement de prix du catalogue ne la modifie pas")
	void priceIsSnapshotAtSaleTime() throws Exception {
		long saleId = createSale("""
				{"lines":[{"productId":3,"quantity":1}]}""");

		mockMvc.perform(put("/api/products/3").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"MacBook Pro 16\\"","description":"Apple laptop","price":1999.00}"""))
				.andExpect(status().isOk());

		mockMvc.perform(get(URL + "/{id}", saleId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines[0].unitPrice").value(2499.99))
				.andExpect(jsonPath("$.totalAmount").value(2499.99));
	}

	@Test
	@DisplayName("US 2 : liste des ventes avec infos principales, plus recentes en premier")
	void listSalesNewestFirst() throws Exception {
		long first = createSale("""
				{"lines":[{"productId":1,"quantity":1}]}""");
		long second = createSale("""
				{"lines":[{"productId":2,"quantity":3},{"productId":3,"quantity":1}]}""");

		mockMvc.perform(get(URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(second))
				.andExpect(jsonPath("$[1].id").value(first))
				.andExpect(jsonPath("$[0].saleDate", notNullValue()))
				.andExpect(jsonPath("$[0].totalAmount").value(5199.96))
				.andExpect(jsonPath("$[0].lineCount").value(2))
				.andExpect(jsonPath("$[0].itemCount").value(4))
				.andExpect(jsonPath("$[0].lines").doesNotExist());
	}

	@Test
	@DisplayName("US 2 : liste vide quand aucune vente n'existe")
	void emptyList() throws Exception {
		mockMvc.perform(get(URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	@DisplayName("US 3 : detail avec lignes, quantites, prix unitaires et total")
	void saleDetail() throws Exception {
		long id = createSale("""
				{"lines":[{"productId":4,"quantity":2},{"productId":2,"quantity":1}]}""");

		mockMvc.perform(get(URL + "/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.totalAmount").value(3499.97))
				.andExpect(jsonPath("$.lines", hasSize(2)))
				.andExpect(jsonPath("$.lines[0].productName").value("Dell XPS 13"))
				.andExpect(jsonPath("$.lines[0].quantity").value(2))
				.andExpect(jsonPath("$.lines[0].unitPrice").value(1299.99))
				.andExpect(jsonPath("$.lines[0].lineTotal").value(2599.98))
				.andExpect(jsonPath("$.lines[1].productName").value("Samsung Galaxy S24"));
	}

	@Test
	@DisplayName("US 3 : vente inexistante -> 404 avec message clair")
	void unknownSale() throws Exception {
		mockMvc.perform(get(URL + "/{id}", 424_242))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Sale not found"))
				.andExpect(jsonPath("$.path").value("/api/sales/424242"));
	}

	@Test
	@DisplayName("400 VALIDATION_ERROR : vente sans ligne")
	void rejectsEmptySale() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("lines"));
	}

	@Test
	@DisplayName("400 VALIDATION_ERROR : quantite nulle, avec le champ en faute")
	void rejectsZeroQuantity() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"lines":[{"productId":1,"quantity":0}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("lines[0].quantity"));
	}

	@Test
	@DisplayName("400 VALIDATION_ERROR : meme produit sur deux lignes")
	void rejectsDuplicateProduct() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"lines":[{"productId":1,"quantity":1},{"productId":1,"quantity":2}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message", containsString("only once")));
	}

	@Test
	@DisplayName("400 INVALID_REFERENCE : produit inexistant")
	void rejectsUnknownProduct() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
				{"lines":[{"productId":1,"quantity":1},{"productId":777,"quantity":1}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REFERENCE"))
				.andExpect(jsonPath("$.message", containsString("777")));
	}

	@Test
	@DisplayName("409 CONFLICT : un produit deja vendu ne peut pas etre supprime du catalogue")
	void soldProductCannotBeDeleted() throws Exception {
		createSale("""
				{"lines":[{"productId":2,"quantity":1}]}""");

		mockMvc.perform(delete("/api/products/2"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}
}