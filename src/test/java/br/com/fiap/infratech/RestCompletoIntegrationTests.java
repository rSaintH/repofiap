package br.com.fiap.infratech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Somente HTTP/JDBC: também compila sobre a aplicação anterior para o ciclo RED.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class RestCompletoIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacoes;

    private LocalDate hoje() {
        return jdbc.queryForObject("SELECT TRUNC(SYSDATE) FROM DUAL", java.sql.Date.class).toLocalDate();
    }

    private Map<String, Object> unidade() {
        return new HashMap<>(Map.of("nome", "REST " + UUID.randomUUID(), "cidade", "Caxias", "uf", "RS",
                "setor", "Industria", "limiteCo2Mensal", 100));
    }

    private Map<String, Object> licenca(long unidade) {
        return new HashMap<>(Map.of("idUnidade", unidade, "numero", UUID.randomUUID().toString().substring(0, 28),
                "tipo", "LO", "orgaoEmissor", "FEPAM", "dataEmissao", hoje().minusYears(3).toString(),
                "dataValidade", hoje().plusYears(3).toString()));
    }

    private Map<String, Object> emissao(long unidade, LocalDate data, int quantidade) {
        return new HashMap<>(Map.of("idUnidade", unidade, "dataRegistro", data.toString(),
                "fonte", "DIESEL", "escopo", 1, "qtdTco2e", quantidade));
    }

    private Map<String, Object> compensacao(long unidade) {
        return new HashMap<>(Map.of("idUnidade", unidade, "projeto", "REFLORESTAMENTO", "qtdTco2e", 12));
    }

    private Map<String, Object> alerta(long unidade) {
        return new HashMap<>(Map.of("idUnidade", unidade, "tipoAlerta", "AUDITORIA_INTERNA", "descricao", "Inspecao REST"));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder pedido, Object dados) throws Exception {
        return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)));
    }

    private JsonNode corpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private long criar(String recurso, String chave, Map<String, Object> dados) throws Exception {
        return corpo(enviar(post("/api/" + recurso), dados).andExpect(status().isCreated())).get(chave).asLong();
    }

    private long criarUnidadeComLo() throws Exception {
        long id = criar("unidades", "idUnidade", unidade());
        criar("licencas", "idLicenca", licenca(id));
        return id;
    }

    @Test
    void cincoRecursosPermitemTodosVerbosEHeadOptionsAutomaticos() throws Exception {
        long unidade = criarUnidadeComLo();
        String[] recursos = {"unidades", "licencas", "emissoes", "compensacoes", "alertas"};
        String[] chaves = {"idUnidade", "idLicenca", "idEmissao", "idCompensacao", "idAlerta"};
        var dados = java.util.List.of(unidade(), licenca(unidade), emissao(unidade, hoje(), 10), compensacao(unidade), alerta(unidade));
        var mudancas = java.util.List.of(Map.of("cidade", "Porto Alegre"), Map.of("orgaoEmissor", "IBAMA"),
                Map.of("qtdTco2e", 11), Map.of("qtdTco2e", 13), Map.of("descricao", "Atualizada"));
        for (int i = 0; i < recursos.length; i++) {
            String caminho = "/api/" + recursos[i];
            long id = criar(recursos[i], chaves[i], dados.get(i));
            String item = caminho + "/" + id;
            mvc.perform(get(caminho)).andExpect(status().isOk());
            mvc.perform(get(item)).andExpect(status().isOk()).andExpect(jsonPath(chaves[i]).value(id));
            mvc.perform(head(caminho)).andExpect(status().isOk());
            mvc.perform(head(item)).andExpect(status().isOk());
            mvc.perform(options(caminho)).andExpect(status().isOk()).andExpect(header().string("Allow", containsString("GET")))
                    .andExpect(header().string("Allow", containsString("POST"))).andExpect(header().string("Allow", containsString("HEAD")));
            mvc.perform(options(item)).andExpect(status().isOk()).andExpect(header().string("Allow", containsString("GET")))
                    .andExpect(header().string("Allow", containsString("HEAD"))).andExpect(header().string("Allow", containsString("PUT")))
                    .andExpect(header().string("Allow", containsString("PATCH"))).andExpect(header().string("Allow", containsString("DELETE")));
            enviar(post(caminho), Map.of()).andExpect(status().isBadRequest());
            enviar(put(item), Map.of()).andExpect(status().isBadRequest());
            enviar(put(item), dados.get(i)).andExpect(status().isOk());
            JsonNode antes = corpo(mvc.perform(get(item)));
            JsonNode depois = corpo(enviar(patch(item), mudancas.get(i)).andExpect(status().isOk()));
            for (String campo : mudancas.get(i).keySet()) {
                JsonNode esperado = json.valueToTree(mudancas.get(i).get(campo));
                if (esperado.isNumber()) assertEquals(0, esperado.decimalValue().compareTo(depois.get(campo).decimalValue()));
                else assertEquals(esperado, depois.get(campo));
            }
            assertEquals(antes.get(chaves[i]), depois.get(chaves[i]));
            enviar(patch(item), Map.of()).andExpect(status().isOk());
            mvc.perform(delete(item)).andExpect(status().isNoContent());
            mvc.perform(get(item)).andExpect(status().isNotFound());
            mvc.perform(delete(item)).andExpect(status().isNotFound());
            enviar(put(item), dados.get(i)).andExpect(status().isNotFound());
            enviar(patch(item), Map.of()).andExpect(status().isNotFound());
        }
    }

    @Test
    void patchDistingueOmissaoNullERejeitaPropriedadesForaDoDto() throws Exception {
        long unidade = criarUnidadeComLo();
        long licenca = criar("licencas", "idLicenca", licenca(unidade));
        long emissao = criar("emissoes", "idEmissao", emissao(unidade, hoje(), 10));
        long compensacao = criar("compensacoes", "idCompensacao", compensacao(unidade));
        long alerta = criar("alertas", "idAlerta", alerta(unidade));
        String[] caminhos = {"unidades/" + unidade, "licencas/" + licenca, "emissoes/" + emissao,
                "compensacoes/" + compensacao, "alertas/" + alerta};
        String[] obrigatorios = {"nome", "numero", "qtdTco2e", "projeto", "descricao"};
        String[] ids = {"idUnidade", "idLicenca", "idEmissao", "idCompensacao", "idAlerta"};
        for (int i = 0; i < caminhos.length; i++) {
            String caminho = "/api/" + caminhos[i];
            JsonNode antes = corpo(mvc.perform(get(caminho)));
            assertEquals(antes, corpo(enviar(patch(caminho), Map.of()).andExpect(status().isOk())));
            Map<String, Object> nulo = new HashMap<>();
            nulo.put(obrigatorios[i], null);
            enviar(patch(caminho), nulo).andExpect(status().isBadRequest());
            enviar(patch(caminho), Map.of("naoExiste", 1)).andExpect(status().isBadRequest());
            enviar(patch(caminho), Map.of(ids[i], 99)).andExpect(status().isBadRequest());
            enviar(patch(caminho), Map.of("class", "java.lang.String")).andExpect(status().isBadRequest());
            assertEquals(antes, corpo(mvc.perform(get(caminho))));
        }
        enviar(patch("/api/unidades/" + unidade), Map.of("emissaoAcumuladaMes", 999)).andExpect(status().isBadRequest());
        enviar(patch("/api/unidades/" + unidade), Map.of("mesReferencia", "2099-01")).andExpect(status().isBadRequest());
        enviar(patch("/api/emissoes/" + emissao), Map.of("dataRegistro", "nao-data")).andExpect(status().isBadRequest());
        enviar(patch("/api/emissoes/" + emissao), Map.of("escopo", 4)).andExpect(status().isBadRequest());
        enviar(patch("/api/licencas/" + licenca), Map.of("dataValidade", hoje().minusYears(4).toString())).andExpect(status().isBadRequest());
        Map<String, Object> nulo = new HashMap<>();
        nulo.put("status", null); // suspensão pode ser removida explicitamente.
        enviar(patch("/api/licencas/" + licenca), Map.of("status", "SUSPENSA")).andExpect(status().isOk());
        enviar(patch("/api/licencas/" + licenca), nulo).andExpect(status().isOk()).andExpect(jsonPath("status").value("VIGENTE"));
    }

    @Test
    void novosDtosValidamChecksTamanhosQuantidadeEUnidadeComDefaults() throws Exception {
        long unidade = criarUnidadeComLo();
        long comp = criar("compensacoes", "idCompensacao", compensacao(unidade));
        mvc.perform(get("/api/compensacoes/" + comp)).andExpect(jsonPath("origem").value("MANUAL"))
                .andExpect(jsonPath("status").value("PENDENTE")).andExpect(jsonPath("dataRegistro").value(LocalDate.now().toString()));
        long alerta = criar("alertas", "idAlerta", alerta(unidade));
        mvc.perform(get("/api/alertas/" + alerta)).andExpect(jsonPath("status").value("ABERTO"))
                .andExpect(jsonPath("dataAlerta").value(LocalDate.now().toString()));
        for (var invalido : java.util.List.of(Map.of("projeto", "INVALIDO"), Map.of("status", "INVALIDO"),
                Map.of("origem", "INVALIDA"), Map.of("qtdTco2e", 0), Map.of("qtdTco2e", -1), Map.of("qtdTco2e", "0.0001"))) {
            enviar(patch("/api/compensacoes/" + comp), invalido).andExpect(status().isBadRequest());
        }
        for (var invalido : java.util.List.of(Map.of("tipoAlerta", "INVALIDO"), Map.of("status", "INVALIDO"),
                Map.of("descricao", "x".repeat(401)), Map.of("descricao", ""))) {
            enviar(patch("/api/alertas/" + alerta), invalido).andExpect(status().isBadRequest());
        }
        enviar(post("/api/compensacoes"), Map.of()).andExpect(status().isBadRequest());
        enviar(post("/api/alertas"), Map.of()).andExpect(status().isBadRequest());
        enviar(post("/api/compensacoes"), compensacao(Long.MAX_VALUE)).andExpect(status().isNotFound());
        enviar(post("/api/alertas"), alerta(Long.MAX_VALUE)).andExpect(status().isNotFound());
        enviar(patch("/api/compensacoes/" + comp), Map.of("idUnidade", Long.MAX_VALUE)).andExpect(status().isNotFound());
        enviar(patch("/api/alertas/" + alerta), Map.of("idUnidade", Long.MAX_VALUE)).andExpect(status().isNotFound());
        enviar(patch("/api/compensacoes/" + comp), Map.of("origem", "AUTOMATICA")).andExpect(status().isOk());
    }

    private void saldo(long unidade, String mes, int quantidade) throws Exception {
        mvc.perform(get("/api/unidades/" + unidade)).andExpect(status().isOk()).andExpect(jsonPath("emissaoAcumuladaMes").value(quantidade));
        assertEquals(mes, jdbc.queryForObject("SELECT MES_REFERENCIA FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", String.class, unidade));
    }

    private void pendente(long unidade, String mes, int quantidade) {
        BigDecimal total = jdbc.queryForObject("SELECT NVL(SUM(QTD_TCO2E),0) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ? AND ORIGEM = 'AUTOMATICA' AND STATUS = 'PENDENTE' AND MES_REFERENCIA = ?", BigDecimal.class, unidade, mes);
        assertEquals(0, total.compareTo(BigDecimal.valueOf(quantidade)));
        int linhas = jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ? AND ORIGEM = 'AUTOMATICA' AND STATUS = 'PENDENTE' AND MES_REFERENCIA = ?", Integer.class, unidade, mes);
        assertEquals(quantidade == 0 ? 0 : 1, linhas);
    }

    @Test
    void corrigirMoverRetroagirEExcluirEmissaoRecomputaMesMaisRecenteESugestoes() throws Exception {
        long a = criarUnidadeComLo();
        long b = criarUnidadeComLo();
        LocalDate atual = hoje().withDayOfMonth(1);
        LocalDate anterior = atual.minusMonths(1);
        String mes = atual.toString().substring(0, 7);
        String mesAnterior = anterior.toString().substring(0, 7);
        long recente = criar("emissoes", "idEmissao", emissao(a, atual, 160));
        long antigo = criar("emissoes", "idEmissao", emissao(a, anterior, 140));
        saldo(a, mes, 160); pendente(a, mes, 60); pendente(a, mesAnterior, 40);
        enviar(put("/api/emissoes/" + recente), emissao(a, atual, 130)).andExpect(status().isOk()).andExpect(jsonPath("qtdTco2e").value(130));
        saldo(a, mes, 130); pendente(a, mes, 30);
        enviar(patch("/api/emissoes/" + recente), Map.of("idUnidade", b)).andExpect(status().isOk());
        saldo(a, mesAnterior, 140); saldo(b, mes, 130); pendente(a, mes, 0); pendente(b, mes, 30);
        enviar(patch("/api/emissoes/" + recente), Map.of("dataRegistro", anterior.toString())).andExpect(status().isOk());
        saldo(b, mesAnterior, 130); pendente(b, mes, 0); pendente(b, mesAnterior, 30);
        mvc.perform(delete("/api/emissoes/" + recente)).andExpect(status().isNoContent());
        saldo(b, null, 0); pendente(b, mesAnterior, 0);
        mvc.perform(delete("/api/emissoes/" + antigo)).andExpect(status().isNoContent());
        saldo(a, null, 0); pendente(a, mesAnterior, 0);
    }

    @Test
    void excluirUltimoMesRetornaAoAnteriorECompensacoesManuaisConcluidasNaoSaoReescritas() throws Exception {
        long unidade = criarUnidadeComLo();
        LocalDate atual = hoje().withDayOfMonth(1);
        long antiga = criar("emissoes", "idEmissao", emissao(unidade, atual.minusMonths(1), 50));
        long recente = criar("emissoes", "idEmissao", emissao(unidade, atual, 160));
        long manual = criar("compensacoes", "idCompensacao", compensacao(unidade));
        long automatica = jdbc.queryForObject("SELECT ID_COMPENSACAO FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ? AND ORIGEM = 'AUTOMATICA'", Long.class, unidade);
        enviar(patch("/api/compensacoes/" + automatica), Map.of("status", "CONCLUIDA")).andExpect(status().isOk());
        JsonNode concluida = corpo(mvc.perform(get("/api/compensacoes/" + automatica)));
        JsonNode manualAntes = corpo(mvc.perform(get("/api/compensacoes/" + manual)));
        enviar(patch("/api/emissoes/" + recente), Map.of("qtdTco2e", 180)).andExpect(status().isOk());
        pendente(unidade, atual.toString().substring(0, 7), 20);
        enviar(patch("/api/emissoes/" + recente), Map.of("qtdTco2e", 120)).andExpect(status().isOk());
        pendente(unidade, atual.toString().substring(0, 7), 0);
        assertEquals(concluida, corpo(mvc.perform(get("/api/compensacoes/" + automatica))));
        assertEquals(manualAntes, corpo(mvc.perform(get("/api/compensacoes/" + manual))));
        mvc.perform(delete("/api/emissoes/" + recente)).andExpect(status().isNoContent());
        saldo(unidade, atual.minusMonths(1).toString().substring(0, 7), 50);
        mvc.perform(delete("/api/compensacoes/" + automatica)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/compensacoes/" + manual)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Integer.class, unidade));
        mvc.perform(delete("/api/emissoes/" + antiga)).andExpect(status().isNoContent());
    }

    @Test
    void excluirSugestaoPendenteNaoRecriaAteProximaAlteracaoDeEmissao() throws Exception {
        long unidade = criarUnidadeComLo();
        long id = criar("emissoes", "idEmissao", emissao(unidade, hoje(), 160));
        long comp = jdbc.queryForObject("SELECT ID_COMPENSACAO FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Long.class, unidade);
        mvc.perform(delete("/api/compensacoes/" + comp)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Integer.class, unidade));
        enviar(patch("/api/emissoes/" + id), Map.of("qtdTco2e", 170)).andExpect(status().isOk());
        pendente(unidade, hoje().toString().substring(0, 7), 70);
        long outro = criar("emissoes", "idEmissao", emissao(unidade, hoje(), 20));
        pendente(unidade, hoje().toString().substring(0, 7), 90);
        mvc.perform(delete("/api/emissoes/" + outro)).andExpect(status().isNoContent());
        pendente(unidade, hoje().toString().substring(0, 7), 70);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legadoSemMesExigeRevisao409SemImpedirCrudDaCompensacao() throws Exception {
        long unidade = criar("unidades", "idUnidade", unidade());
        try {
            criar("licencas", "idLicenca", licenca(unidade));
            long id = criar("emissoes", "idEmissao", emissao(unidade, hoje(), 10));
            Map<String, Object> dados = compensacao(unidade);
            dados.put("origem", "AUTOMATICA");
            long comp = criar("compensacoes", "idCompensacao", dados);
            jdbc.update("UPDATE COMPENSACAO_CARBONO SET MES_REFERENCIA = NULL WHERE ID_COMPENSACAO = ?", comp);
            enviar(patch("/api/emissoes/" + id), Map.of("qtdTco2e", 20)).andExpect(status().isConflict());
            mvc.perform(get("/api/emissoes/" + id)).andExpect(jsonPath("qtdTco2e").value(10));
            enviar(patch("/api/compensacoes/" + comp), Map.of()).andExpect(status().isOk());
            assertNull(jdbc.queryForObject("SELECT MES_REFERENCIA FROM COMPENSACAO_CARBONO WHERE ID_COMPENSACAO = ?", String.class, comp));
            enviar(patch("/api/compensacoes/" + comp), Map.of("origem", "MANUAL")).andExpect(status().isOk());
            enviar(patch("/api/emissoes/" + id), Map.of("qtdTco2e", 20)).andExpect(status().isOk());
            mvc.perform(get("/api/compensacoes/" + comp)).andExpect(jsonPath("qtdTco2e").value(12));
        } finally {
            new TransactionTemplate(transacoes).executeWithoutResult(s -> {
                // Primeiro remover pendências ambíguas para liberar a correção de emissões.
                jdbc.update("DELETE FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM EMISSAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM LICENCA_AMBIENTAL WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", unidade);
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateBloqueadoRetorna422MantemOriginalERegistraAlertaAutonomo() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        String nome = "REST autonomo " + UUID.randomUUID();
        long unidade = tx.execute(s -> {
            jdbc.update("INSERT INTO UNIDADE_OPERACIONAL (NOME,CIDADE,UF,SETOR,LIMITE_CO2_MENSAL) VALUES (?, 'Caxias','RS','Industria',100)", nome);
            return jdbc.queryForObject("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE NOME = ?", Long.class, nome);
        });
        try {
            criar("licencas", "idLicenca", licenca(unidade));
            long id = criar("emissoes", "idEmissao", emissao(unidade, hoje(), 10));
            // Data fora da validade: não altera a emissão e mantém o alerta autônomo.
            enviar(patch("/api/emissoes/" + id), Map.of("dataRegistro", hoje().plusYears(4).toString()))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(get("/api/emissoes/" + id)).andExpect(jsonPath("dataRegistro").value(hoje().toString()));
            jdbc.update("UPDATE LICENCA_AMBIENTAL SET STATUS = 'SUSPENSA' WHERE ID_UNIDADE = ?", unidade);
            enviar(put("/api/emissoes/" + id), emissao(unidade, hoje(), 20)).andExpect(status().isUnprocessableEntity());
            mvc.perform(get("/api/emissoes/" + id)).andExpect(jsonPath("qtdTco2e").value(10));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ? AND TIPO_ALERTA = 'OPERACAO_SEM_LICENCA'", Integer.class, unidade));
            // DELETE não precisa de LO para corrigir/remover um lançamento anterior.
            mvc.perform(delete("/api/emissoes/" + id)).andExpect(status().isNoContent());
        } finally {
            tx.executeWithoutResult(s -> {
                jdbc.update("DELETE FROM EMISSAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM LICENCA_AMBIENTAL WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", unidade);
            });
        }
    }
}
