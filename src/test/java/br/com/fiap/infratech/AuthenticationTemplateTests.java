package br.com.fiap.infratech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationTemplateTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RequestMappingHandlerMapping mappings;

    @Test
    void registrationLoginAndAuthenticatedEndpointStillWork() throws Exception {
        String email = "template-" + UUID.randomUUID() + "@example.com";
        String password = "Teste123!";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nome", "Teste", "email", email,
                        "senha", password, "role", "USER"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("email").value(email))
                .andExpect(jsonPath("senha").doesNotExist());
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).get("token").asText();
        mvc.perform(get("/api/unidades").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRejectsAnonymousAccess() throws Exception {
        mvc.perform(get("/api/unidades")).andExpect(status().isForbidden());
    }

    @Test
    void foodEndpointsAreNotRegistered() {
        assertFalse(mappings.getHandlerMethods().keySet().stream()
                .anyMatch(mapping -> mapping.toString().toLowerCase().contains("alimento")));
    }
}
