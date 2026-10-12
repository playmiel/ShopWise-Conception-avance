package com.shopwise.app.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * Tests de non-regression du catalogue produits (fonctionnalite historique).
 *
 * Ils figent le contrat HTTP de /api/products tel qu'il existait AVANT les evolutions
 * (ventes, securite, recommandations) : codes HTTP, champs JSON retournes, regles de validation.
 * Chaque evolution doit laisser cette suite au vert.
 *
 * Seuls les codes HTTP sont verifies sur les cas d'erreur : le format du corps d'erreur
 * sera volontairement normalise par l'US 6 (il est couvert par ses propres tests).
 *
 * Chaque test s'execute dans une transaction annulee a la fin : le jeu de donnees
 * initial (data.sql, 4 produits) est donc identique pour tous les tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// Securite (US 4/5) : execution en ADMIN ; le controle d'acces est teste par SecurityApiTest et AuthorizationMatrixTest
@WithMockUser(username = "admin", roles = "ADMIN")
@Tag("non-regression")
@DisplayName("Non-regression - API catalogue /api/products")
class ProductApiNonRegressionTest {

	private static final String URL = "/api/products";

	@Autowired
	private MockMvc mockMvc;

	private long createProduct(String json) throws Exception {
		String body = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Nested
	@DisplayName("GET /api/products")
	class ListProducts {

		@Test
		@DisplayName("retourne le catalogue initial (4 produits) avec tous les champs")
		void returnsSeededCatalog() throws Exception {
			mockMvc.perform(get(URL))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(4)))
					.andExpect(jsonPath("$[*].name", hasItem("iPhone 15")))
					.andExpect(jsonPath("$[*].name", hasItem("Dell XPS 13")))
					.andExpect(jsonPath("$[0].id", notNullValue()))
					.andExpect(jsonPath("$[0].description", notNullValue()))
					.andExpect(jsonPath("$[0].price", notNullValue()))
					.andExpect(jsonPath("$[0].createdAt", notNullValue()))
					.andExpect(jsonPath("$[0].updatedAt", notNullValue()));
		}

		@Test
		@DisplayName("inclut un produit nouvellement cree")
		void includesNewProduct() throws Exception {
			createProduct("""
					{"name":"Baguette","description":"Tradition","price":1.20}""");

			mockMvc.perform(get(URL))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(5)))
					.andExpect(jsonPath("$[*].name", hasItem("Baguette")));
		}
	}

	@Nested
	@DisplayName("GET /api/products/{id}")
	class GetProduct {

		@Test
		@DisplayName("retourne le produit existant")
		void returnsExistingProduct() throws Exception {
			long id = createProduct("""
					{"name":"Cafe moulu","description":"Arabica 250g","price":4.90}""");

			mockMvc.perform(get(URL + "/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id))
					.andExpect(jsonPath("$.name").value("Cafe moulu"))
					.andExpect(jsonPath("$.description").value("Arabica 250g"))
					.andExpect(jsonPath("$.price").value(4.90))
					.andExpect(jsonPath("$.createdAt", notNullValue()))
					.andExpect(jsonPath("$.updatedAt", notNullValue()));
		}

		@Test
		@DisplayName("renvoie 404 pour un produit inexistant")
		void returns404WhenUnknown() throws Exception {
			mockMvc.perform(get(URL + "/{id}", 999_999))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("POST /api/products")
	class CreateProduct {

		@Test
		@DisplayName("cree un produit et renvoie 201 avec l'identifiant genere")
		void createsProduct() throws Exception {
			mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Lait entier","description":"1L bio","price":1.35}"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.id", notNullValue()))
					.andExpect(jsonPath("$.name").value("Lait entier"))
					.andExpect(jsonPath("$.description").value("1L bio"))
					.andExpect(jsonPath("$.price").value(1.35))
					.andExpect(jsonPath("$.createdAt", notNullValue()));
		}

		@Test
		@DisplayName("accepte un produit sans description (champ optionnel)")
		void descriptionIsOptional() throws Exception {
			mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Sel","price":0.80}"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.description", nullValue()));
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@ValueSource(strings = {
				"{\"description\":\"sans nom\",\"price\":1.00}",
				"{\"name\":\"   \",\"price\":1.00}",
				"{\"name\":\"Sans prix\"}",
				"{\"name\":\"Prix nul\",\"price\":0}",
				"{\"name\":\"Prix negatif\",\"price\":-5}"
		})
		@DisplayName("refuse une requete invalide avec 400")
		void rejectsInvalidPayload(String json) throws Exception {
			mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("refuse un nom de plus de 100 caracteres avec 400")
		void rejectsTooLongName() throws Exception {
			String name = "x".repeat(101);
			mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"" + name + "\",\"price\":1.00}"))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("refuse une description de plus de 500 caracteres avec 400")
		void rejectsTooLongDescription() throws Exception {
			String description = "x".repeat(501);
			mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Ok\",\"description\":\"" + description + "\",\"price\":1.00}"))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("PUT /api/products/{id}")
	class UpdateProduct {

		@Test
		@DisplayName("met a jour un produit existant et la modification est persistee")
		void updatesProduct() throws Exception {
			long id = createProduct("""
					{"name":"Yaourt","description":"nature","price":0.50}""");

			mockMvc.perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Yaourt grec","description":"nature 4x125g","price":2.10}"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id))
					.andExpect(jsonPath("$.name").value("Yaourt grec"))
					.andExpect(jsonPath("$.price").value(2.10));

			mockMvc.perform(get(URL + "/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.name").value("Yaourt grec"))
					.andExpect(jsonPath("$.description").value("nature 4x125g"));
		}

		@Test
		@DisplayName("renvoie 404 pour un produit inexistant")
		void returns404WhenUnknown() throws Exception {
			mockMvc.perform(put(URL + "/{id}", 999_999).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Fantome","price":1.00}"""))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("refuse une requete invalide avec 400")
		void rejectsInvalidPayload() throws Exception {
			long id = createProduct("""
					{"name":"Beurre","price":2.50}""");

			mockMvc.perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"","price":-1}"""))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("DELETE /api/products/{id}")
	class DeleteProduct {

		@Test
		@DisplayName("supprime un produit existant (204) qui n'est ensuite plus trouvable")
		void deletesProduct() throws Exception {
			long id = createProduct("""
					{"name":"Confiture","price":3.20}""");

			mockMvc.perform(delete(URL + "/{id}", id))
					.andExpect(status().isNoContent());

			mockMvc.perform(get(URL + "/{id}", id))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("renvoie 404 pour un produit inexistant")
		void returns404WhenUnknown() throws Exception {
			mockMvc.perform(delete(URL + "/{id}", 999_999))
					.andExpect(status().isNotFound());
		}
	}
}