package br.com.fiap.infratech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

// Apenas HTTP/JDBC: copiar esta classe sobre a fonte anterior para executar RED.
// Sem @Transactional: fixtures precisam estar COMMITADAS para as duas conexões.
@SpringBootTest
@AutoConfigureMockMvc
class RestConcorrenciaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacoes;

    private TransactionTemplate transacao() {
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        tx.setTimeout(30);
        return tx;
    }

    private long criarUnidade() {
        return transacao().execute(s -> {
            String nome = "REST concorrencia " + UUID.randomUUID();
            jdbc.update("INSERT INTO UNIDADE_OPERACIONAL (NOME,CIDADE,UF,SETOR,LIMITE_CO2_MENSAL) VALUES (?, 'Caxias','RS','Industria',100)", nome);
            long unidade = jdbc.queryForObject("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE NOME = ?", Long.class, nome);
            jdbc.update("INSERT INTO LICENCA_AMBIENTAL (ID_UNIDADE,NUMERO,TIPO,ORGAO_EMISSOR,DATA_EMISSAO,DATA_VALIDADE) VALUES (?,?,'LO','FEPAM',TRUNC(SYSDATE)-365,TRUNC(SYSDATE)+730)", unidade, UUID.randomUUID().toString().substring(0, 28));
            return unidade;
        });
    }

    private long criarEmissao(long unidade) {
        return transacao().execute(s -> {
            jdbc.update("INSERT INTO EMISSAO_CARBONO (ID_UNIDADE,DATA_REGISTRO,FONTE,ESCOPO,QTD_TCO2E) VALUES (?,TRUNC(SYSDATE),'DIESEL',1,160)", unidade);
            return jdbc.queryForObject("SELECT ID_EMISSAO FROM EMISSAO_CARBONO WHERE ID_UNIDADE = ?", Long.class, unidade);
        });
    }

    private long compensacao(long unidade) {
        return jdbc.queryForObject("SELECT ID_COMPENSACAO FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Long.class, unidade);
    }

    private MvcResult alterar(String recurso, long id, Map<String, Object> campos) throws Exception {
        // SecurityContext não é herdado pelo executor: autenticar cada pedido.
        return mvc.perform(patch("/api/" + recurso + "/" + id).with(user("concorrencia"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(campos))).andReturn();
    }

    private void quantidade(long id, int esperado) {
        assertEquals(0, jdbc.queryForObject("SELECT QTD_TCO2E FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", BigDecimal.class, id)
                .compareTo(BigDecimal.valueOf(esperado)));
    }

    private void limpar(long unidade) {
        transacao().executeWithoutResult(s -> {
            // Remover legado ambíguo antes de disparar o trigger de DELETE de emissão.
            jdbc.update("DELETE FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
            jdbc.update("DELETE FROM EMISSAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
            jdbc.update("DELETE FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ?", unidade);
            jdbc.update("DELETE FROM LICENCA_AMBIENTAL WHERE ID_UNIDADE = ?", unidade);
            jdbc.update("DELETE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", unidade);
        });
    }

    private void aguardarBloqueio(AtomicReference<Thread> trabalhador, Future<?> pedido) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        CountDownLatch intervalo = new CountDownLatch(1);
        while (System.nanoTime() < limite) {
            if (pedido.isDone()) {
                pedido.get(1, TimeUnit.SECONDS); // Expor também eventual erro HTTP/JDBC precoce.
                fail("PATCH terminou antes de aguardar o bloqueio da unidade");
            }
            Thread thread = trabalhador.get();
            if (thread != null) {
                for (StackTraceElement quadro : thread.getStackTrace()) {
                    if (quadro.getClassName().endsWith("CompensacaoCarbonoService")
                            && quadro.getMethodName().equals("bloquearUnidade")) return;
                }
            }
            intervalo.await(20, TimeUnit.MILLISECONDS); // Poll limitado, sem hooks na aplicação.
        }
        fail("PATCH não alcançou bloquearUnidade dentro de 8 segundos");
    }

    private void executarConcorrencia(Map<String, Object> campos, boolean excluirEmissao) throws Exception {
        long unidade = criarUnidade();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch alterada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        Future<?> emissaoTx = null;
        Future<MvcResult> pedido = null;
        try {
            long emissao = criarEmissao(unidade);
            long comp = compensacao(unidade);
            quantidade(comp, 60);
            emissaoTx = executor.submit(() -> transacao().executeWithoutResult(s -> {
                if (excluirEmissao) jdbc.update("DELETE FROM EMISSAO_CARBONO WHERE ID_EMISSAO = ?", emissao);
                else jdbc.update("UPDATE EMISSAO_CARBONO SET QTD_TCO2E = 180 WHERE ID_EMISSAO = ?", emissao);
                if (excluirEmissao) assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", Integer.class, comp));
                else quantidade(comp, 80); // Trigger já recalculou; txA ainda segura unidade.
                alterada.countDown();
                try {
                    assertTrue(liberar.await(20, TimeUnit.SECONDS), "txA não foi liberada");
                } catch (InterruptedException erro) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(erro);
                }
            }));
            assertTrue(alterada.await(8, TimeUnit.SECONDS), "txA não alterou a emissão");
            quantidade(comp, 60); // Outra conexão ainda enxerga o estado COMMITADO antigo.
            AtomicReference<Thread> trabalhador = new AtomicReference<>();
            pedido = executor.submit(() -> {
                trabalhador.set(Thread.currentThread());
                return alterar("compensacoes", comp, campos);
            });
            try {
                aguardarBloqueio(trabalhador, pedido);
            } finally {
                liberar.countDown(); // Inclusive se a coordenação/assertiva falhar.
            }
            emissaoTx.get(15, TimeUnit.SECONDS);
            MvcResult resposta = pedido.get(15, TimeUnit.SECONDS);
            assertEquals(excluirEmissao ? 404 : 200, resposta.getResponse().getStatus(), resposta.getResponse().getContentAsString());
            if (excluirEmissao) {
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Integer.class, unidade));
            } else {
                quantidade(comp, 80);
                assertEquals(0, json.readTree(resposta.getResponse().getContentAsString()).get("qtdTco2e").decimalValue().compareTo(BigDecimal.valueOf(80)));
                for (var campo : campos.entrySet()) {
                    assertEquals(campo.getValue().toString(), json.readTree(resposta.getResponse().getContentAsString()).get(campo.getKey()).asText());
                }
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Integer.class, unidade));
            }
        } finally {
            liberar.countDown();
            // Liberar txA antes de interromper o executor: JDBC pode ignorar interrupt.
            try {
                if (emissaoTx != null) emissaoTx.get(15, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                try {
                    assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "Executor não encerrou; evitar cleanup concorrente");
                } finally {
                    if (executor.isTerminated()) limpar(unidade);
                }
            }
        }
    }

    @Test
    void patchSomenteStatusPreservaSaldoRecalculadoEnquantoAguardaUnidade() throws Exception {
        executarConcorrencia(Map.of("status", "EM_ANDAMENTO"), false);
    }

    @Test
    void patchSomenteProjetoPreservaSaldoRecalculadoEnquantoAguardaUnidade() throws Exception {
        executarConcorrencia(Map.of("projeto", "REFLORESTAMENTO"), false);
    }

    @Test
    void patchDeCompensacaoExcluidaPeloTriggerEnquantoAguardaRetorna404() throws Exception {
        executarConcorrencia(Map.of("projeto", "REFLORESTAMENTO"), true);
    }

    @Test
    void legadoSemMesBloqueiaEmissaoMesmoEmAndamentoOuConcluidoAteConversaoManual() throws Exception {
        long unidade = criarUnidade();
        try {
            long emissao = criarEmissao(unidade);
            long comp = compensacao(unidade);
            transacao().executeWithoutResult(s -> jdbc.update("UPDATE COMPENSACAO_CARBONO SET MES_REFERENCIA = NULL WHERE ID_COMPENSACAO = ?", comp));
            for (String estado : new String[]{"PENDENTE", "EM_ANDAMENTO", "CONCLUIDA"}) {
                assertEquals(200, alterar("compensacoes", comp, Map.of("status", estado)).getResponse().getStatus());
                MvcResult resposta = alterar("emissoes", emissao, Map.of("qtdTco2e", 180));
                assertEquals(409, resposta.getResponse().getStatus(), "Legado " + estado + ": " + resposta.getResponse().getContentAsString());
                assertEquals(0, jdbc.queryForObject("SELECT QTD_TCO2E FROM EMISSAO_CARBONO WHERE ID_EMISSAO = ?", BigDecimal.class, emissao).compareTo(BigDecimal.valueOf(160)));
                quantidade(comp, 60);
                assertNull(jdbc.queryForObject("SELECT MES_REFERENCIA FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", String.class, comp));
                assertEquals(estado, jdbc.queryForObject("SELECT STATUS FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", String.class, comp));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Integer.class, unidade));
            }
            assertEquals(200, alterar("compensacoes", comp, Map.of("origem", "MANUAL")).getResponse().getStatus());
            assertEquals(200, alterar("emissoes", emissao, Map.of("qtdTco2e", 180)).getResponse().getStatus());
            quantidade(comp, 60); // Histórico concluído manual permanece intacto.
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ? AND ORIGEM = 'AUTOMATICA' AND STATUS = 'PENDENTE' AND MES_REFERENCIA = TO_CHAR(SYSDATE,'YYYY-MM') AND QTD_TCO2E = 80", Integer.class, unidade));
        } finally {
            limpar(unidade);
        }
    }
}
