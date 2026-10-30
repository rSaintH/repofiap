package br.com.fiap.infratech;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BearerHttpIntegrationTests {
    @Autowired TestRestTemplate http;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Value("${minha.chave.secreta}") String chave;

    private void verificar401(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        ResponseEntity<String> resposta = http.exchange("/api/unidades", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        assertNotNull(resposta.getBody());
        assertTrue(resposta.getBody().contains("Token inválido ou expirado"));
    }

    @Test void bearerMalformadoRetorna401NoServidorReal() { verificar401("Bearer invalido"); }
    @Test void bearerVazioRetorna401NoServidorReal() { verificar401("Bearer "); }
    @Test void bearerExpiradoRetorna401NoServidorReal() {
        String token = JWT.create().withIssuer("infratech").withSubject("teste-expirado@example.com")
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(chave));
        verificar401("Bearer " + token);
    }
    @Test void usuarioInexistenteRetorna401NoServidorReal() {
        String token = JWT.create().withIssuer("infratech").withSubject("inexistente-" + java.util.UUID.randomUUID() + "@example.com")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(chave));
        verificar401("Bearer " + token);
    }

    @Test void headEOptionsFuncionamNoServidorReal() {
        String email = "head-options-" + java.util.UUID.randomUUID() + "@example.com";
        try {
            var cadastro = java.util.Map.of("nome", "Teste HTTP", "email", email, "senha", "Teste123!", "role", "USER");
            assertEquals(HttpStatus.CREATED, http.postForEntity("/auth/register", cadastro, String.class).getStatusCode());
            ResponseEntity<java.util.Map> login = http.postForEntity("/auth/login",
                    java.util.Map.of("email", email, "senha", "Teste123!"), java.util.Map.class);
            assertEquals(HttpStatus.OK, login.getStatusCode());
            assertNotNull(login.getBody());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth((String) login.getBody().get("token"));
            for (String recurso : new String[]{"unidades", "licencas", "emissoes", "compensacoes", "alertas"}) {
                for (String caminho : new String[]{"/api/" + recurso, "/api/" + recurso + "/1"}) {
                    ResponseEntity<String> resposta = http.exchange(caminho, HttpMethod.HEAD, new HttpEntity<>(headers), String.class);
                    assertEquals(HttpStatus.OK, resposta.getStatusCode(), caminho);
                    assertNull(resposta.getBody(), "HEAD nao pode devolver corpo: " + caminho);
                    ResponseEntity<String> opcoes = http.exchange(caminho, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
                    assertEquals(HttpStatus.OK, opcoes.getStatusCode(), caminho);
                    var permitidos = opcoes.getHeaders().getAllow();
                    assertTrue(permitidos.contains(HttpMethod.GET), caminho);
                    assertTrue(permitidos.contains(HttpMethod.HEAD), caminho);
                    assertTrue(permitidos.contains(HttpMethod.OPTIONS), caminho);
                    if (caminho.endsWith("/1")) {
                        assertTrue(permitidos.contains(HttpMethod.PUT), caminho);
                        assertTrue(permitidos.contains(HttpMethod.PATCH), caminho);
                        assertTrue(permitidos.contains(HttpMethod.DELETE), caminho);
                    } else {
                        assertTrue(permitidos.contains(HttpMethod.POST), caminho);
                    }
                }
            }
        } finally {
            jdbc.update("DELETE FROM TBL_USUARIOS WHERE EMAIL = ?", email);
        }
    }
}
