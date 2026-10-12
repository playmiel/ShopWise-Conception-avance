package com.shopwise.app.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Matrice d'autorisation (US 4) : chaque role x chaque operation sensible -> statut attendu.
 * C'est la specification executable de la table "qui peut faire quoi" du README.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("api")
@DisplayName("API - matrice d'autorisation par role (US 4)")
class AuthorizationMatrixTest {

	private static final String PRODUCT = "{\"name\":\"Test\",\"price\":1.00}";
	private static final String SALE = "{\"lines\":[{\"productId\":1,\"quantity\":1}]}";

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest(name = "{0} {1} {2} -> {4}")
	@CsvSource(delimiter = '|', textBlock = """
			VIEWER   | GET    | /api/products   |         | 200
			VIEWER   | GET    | /api/products/1 |         | 200
			VIEWER   | GET    | /api/sales      |         | 200
			VIEWER   | POST   | /api/products   | PRODUCT | 403
			VIEWER   | PUT    | /api/products/1 | PRODUCT | 403
			VIEWER   | DELETE | /api/products/1 |         | 403
			VIEWER   | POST   | /api/sales      | SALE    | 403
			MERCHANT | GET    | /api/products   |         | 200
			MERCHANT | GET    | /api/sales      |         | 200
			MERCHANT | POST   | /api/products   | PRODUCT | 403
			MERCHANT | PUT    | /api/products/1 | PRODUCT | 403
			MERCHANT | DELETE | /api/products/1 |         | 403
			MERCHANT | POST   | /api/sales      | SALE    | 201
			ADMIN    | GET    | /api/products   |         | 200
			ADMIN    | GET    | /api/sales      |         | 200
			ADMIN    | POST   | /api/products   | PRODUCT | 201
			ADMIN    | PUT    | /api/products/1 | PRODUCT | 200
			ADMIN    | DELETE | /api/products/1 |         | 204
			ADMIN    | POST   | /api/sales      | SALE    | 201
			""")
	void roleMatrix(String role, String method, String url, String body, int expectedStatus) throws Exception {
		MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), url)
				.with(user(role.toLowerCase()).roles(role));
		if ("PRODUCT".equals(body)) {
			request.contentType(MediaType.APPLICATION_JSON).content(PRODUCT);
		} else if ("SALE".equals(body)) {
			request.contentType(MediaType.APPLICATION_JSON).content(SALE);
		}
		mockMvc.perform(request).andExpect(status().is(expectedStatus));
	}

	@ParameterizedTest(name = "anonyme {0} {1} -> 401")
	@CsvSource(delimiter = '|', textBlock = """
			GET    | /api/products
			GET    | /api/sales
			POST   | /api/products
			DELETE | /api/products/1
			POST   | /api/sales
			GET    | /api/auth/me
			""")
	void anonymousIsRejected(String method, String url) throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(method), url)).andExpect(status().isUnauthorized());
	}
}