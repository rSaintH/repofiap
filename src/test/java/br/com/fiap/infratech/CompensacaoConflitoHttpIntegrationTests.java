package br.com.fiap.infratech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompensacaoConflitoHttpIntegrationTests {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transacoes;
    @LocalServerPort int porta;

    private long criarUnidade() {
        String nome = "REST conflito " + UUID.randomUUID();
        jdbc.update("INSERT INTO UNIDADE_OPERACIONAL (NOME,CIDADE,UF,SETOR,LIMITE_CO2_MENSAL) VALUES (?,'Caxias','RS','Industria',100)", nome);
        return jdbc.queryForObject("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE NOME = ?", Long.class, nome);
    }

    private void aguardarPedidoBloqueado(Future<?> pedido) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        CountDownLatch intervalo = new CountDownLatch(1);
        while (System.nanoTime() < limite) {
            if (pedido.isDone()) {
                pedido.get(1, TimeUnit.SECONDS);
                fail("Pedido terminou antes do bloqueio da unidade");
            }
            for (var pilha : Thread.getAllStackTraces().values()) {
                for (var quadro : pilha) {
                    if (quadro.getClassName().endsWith("CompensacaoCarbonoService")
                            && quadro.getMethodName().equals("bloquearUnidade")) return;
                }
            }
            intervalo.await(20, TimeUnit.MILLISECONDS);
        }
        fail("Pedido HTTP não alcançou o bloqueio em 8 segundos");
    }

    @Test
    void mudancaConcorrenteDeUnidadeRetorna409JsonNoServidorReal() throws Exception {
        long origem = criarUnidade();
        long destino = criarUnidade();
        String email = "conflito-" + UUID.randomUUID() + "@example.com";
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch movida = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        Future<?> movimento = null;
        try {
            jdbc.update("INSERT INTO COMPENSACAO_CARBONO (ID_UNIDADE,PROJETO,QTD_TCO2E,STATUS,ORIGEM) VALUES (?,'A_DEFINIR',60,'PENDENTE','MANUAL')", origem);
            long id = jdbc.queryForObject("SELECT ID_COMPENSACAO FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Long.class, origem);
            assertEquals(HttpStatus.CREATED, http.postForEntity("/auth/register", Map.of(
                    "nome", "Teste conflito", "email", email, "senha", "Teste123!", "role", "USER"), String.class).getStatusCode());
            var login = http.postForEntity("/auth/login", Map.of("email", email, "senha", "Teste123!"), Map.class);
            assertEquals(HttpStatus.OK, login.getStatusCode());
            assertNotNull(login.getBody());
            String token = (String) login.getBody().get("token");
            movimento = executor.submit(() -> {
                TransactionTemplate tx = new TransactionTemplate(transacoes);
                tx.setTimeout(30);
                tx.executeWithoutResult(s -> {
                    jdbc.queryForObject("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ? FOR UPDATE", Long.class, origem);
                    jdbc.queryForObject("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ? FOR UPDATE", Long.class, destino);
                    jdbc.update("UPDATE COMPENSACAO_CARBONO SET ID_UNIDADE = ? WHERE ID_COMPENSACAO = ?", destino, id);
                    movida.countDown();
                    try {
                        assertTrue(liberar.await(20, TimeUnit.SECONDS));
                    } catch (InterruptedException erro) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(erro);
                    }
                });
            });
            assertTrue(movida.await(8, TimeUnit.SECONDS));
            assertEquals(origem, jdbc.queryForObject("SELECT ID_UNIDADE FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", Long.class, id));
            try (HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/compensacoes/" + id))
                        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"projeto\":\"REFLORESTAMENTO\"}")).build();
                var pedido = executor.submit(() -> cliente.send(requisicao, HttpResponse.BodyHandlers.ofString()));
                try {
                    aguardarPedidoBloqueado(pedido);
                } finally {
                    liberar.countDown();
                }
                movimento.get(15, TimeUnit.SECONDS);
                var resposta = pedido.get(15, TimeUnit.SECONDS);
                assertEquals(destino, jdbc.queryForObject("SELECT ID_UNIDADE FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", Long.class, id));
                assertEquals("A_DEFINIR", jdbc.queryForObject("SELECT PROJETO FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", String.class, id));
                assertEquals(409, resposta.statusCode(), resposta.body());
                assertTrue(resposta.headers().firstValue("Content-Type").orElse("").contains("application/json"));
                assertTrue(json.readTree(resposta.body()).path("erro").asText().contains("mudou durante a operação"));
            }
        } finally {
            liberar.countDown();
            try {
                if (movimento != null) movimento.get(15, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
                for (long unidade : new long[]{origem, destino}) {
                    jdbc.update("DELETE FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                    jdbc.update("DELETE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", unidade);
                }
                jdbc.update("DELETE FROM TBL_USUARIOS WHERE EMAIL = ?", email);
            }
        }
    }
}
