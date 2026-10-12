package com.shopwise.app.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Sondes de sante utilisees par Docker : publiques, sans detail ; les autres endpoints Actuator restent fermes. */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("api")
@DisplayName("API - sondes de sante (Docker)")
class HealthEndpointTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("health et readiness accessibles sans jeton, statut UP")
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("les autres endpoints Actuator ne sont pas accessibles")
	void otherActuatorEndpointsAreClosed() throws Exception {
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}
}