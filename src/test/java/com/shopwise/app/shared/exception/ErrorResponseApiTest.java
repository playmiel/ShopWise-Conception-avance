package com.shopwise.app.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * US 6 : toutes les erreurs suivent le meme format JSON {status, code, message, path, timestamp}
 * avec des codes HTTP coherents. Plusieurs cas renvoyaient 500 avant la correction
 * (JSON mal forme, identifiant non numerique, methode non supportee, route inconnue).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// Securite (US 4/5) : execution en ADMIN ; le controle d'acces est teste par SecurityApiTest et AuthorizationMatrixTest
@WithMockUser(username = "admin", roles = "ADMIN")
@Tag("api")
@DisplayName("API - format normalise des erreurs (US 6)")
class ErrorResponseApiTest {

	@Autowired
	private MockMvc mockMvc;

	private static void assertApiError(ResultActions result, int status, String code, String path) throws Exception {
		result.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.message", notNullValue()))
				.andExpect(jsonPath("$.path").value(path))
				.andExpect(jsonPath("$.timestamp", notNullValue()));
	}

	@Test
	@DisplayName("404 RESOURCE_NOT_FOUND quand le produit n'existe pas")
	void unknownProduct() throws Exception {
		ResultActions result = mockMvc.perform(get("/api/products/{id}", 999_999));

		assertApiError(result, 404, "RESOURCE_NOT_FOUND", "/api/products/999999");
		result.andExpect(jsonPath("$.message").value("Product not found"))
				.andExpect(jsonPath("$.errors").doesNotExist());
	}

	@Test
	@DisplayName("400 VALIDATION_ERROR avec la liste des champs en faute")
	void validationError() throws Exception {
		ResultActions result = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\",\"price\":-1}"));

		assertApiError(result, 400, "VALIDATION_ERROR", "/api/products");
		result.andExpect(jsonPath("$.errors[*].field", hasItems("name", "price")))
				.andExpect(jsonPath("$.errors[0].message", notNullValue()));
	}

	@Test
	@DisplayName("400 MALFORMED_REQUEST pour un JSON illisible (500 avant correction)")
	void malformedJson() throws Exception {
		ResultActions result = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Pain\", \"price\": "));

		assertApiError(result, 400, "MALFORMED_REQUEST", "/api/products");
	}

	@Test
	@DisplayName("400 INVALID_PARAMETER pour un identifiant non numerique (500 avant correction)")
	void nonNumericId() throws Exception {
		ResultActions result = mockMvc.perform(get("/api/products/abc"));

		assertApiError(result, 400, "INVALID_PARAMETER", "/api/products/abc");
		result.andExpect(jsonPath("$.message", containsString("abc")));
	}

	@Test
	@DisplayName("405 METHOD_NOT_ALLOWED pour une methode non supportee (500 avant correction)")
	void methodNotAllowed() throws Exception {
		ResultActions result = mockMvc.perform(patch("/api/products/1").contentType(MediaType.APPLICATION_JSON)
				.content("{}"));

		assertApiError(result, 405, "METHOD_NOT_ALLOWED", "/api/products/1");
	}

	@Test
	@DisplayName("404 RESOURCE_NOT_FOUND pour une route inconnue (500 avant correction)")
	void unknownRoute() throws Exception {
		ResultActions result = mockMvc.perform(get("/api/does-not-exist"));

		assertApiError(result, 404, "RESOURCE_NOT_FOUND", "/api/does-not-exist");
	}

	@Test
	@DisplayName("415 UNSUPPORTED_MEDIA_TYPE quand le corps n'est pas du JSON")
	void unsupportedMediaType() throws Exception {
		ResultActions result = mockMvc.perform(post("/api/products").contentType(MediaType.TEXT_PLAIN)
				.content("name=Pain"));

		assertApiError(result, 415, "UNSUPPORTED_MEDIA_TYPE", "/api/products");
	}
}