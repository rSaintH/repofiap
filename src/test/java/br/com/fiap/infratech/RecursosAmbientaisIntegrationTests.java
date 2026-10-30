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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class RecursosAmbientaisIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacoes;

    private Map<String, Object> unidade() {
        return new HashMap<>(Map.of("nome", "Teste API " + UUID.randomUUID(), "cidade", "Caxias do Sul",
                "uf", "RS", "setor", "Metalurgia", "limiteCo2Mensal", 100));
    }

    private long criarUnidade() throws Exception {
        return resposta(enviar("/api/unidades", unidade()).andExpect(status().isCreated())).get("idUnidade").asLong();
    }

    private ResultActions enviar(String caminho, Object dados) throws Exception {
        return mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)));
    }

    private JsonNode resposta(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private LocalDate hoje() {
        return jdbc.queryForObject("SELECT TRUNC(SYSDATE) FROM DUAL", java.sql.Date.class).toLocalDate();
    }

    private Map<String, Object> licenca(long unidade, LocalDate validade) {
        return new HashMap<>(Map.of("idUnidade", unidade, "numero", UUID.randomUUID().toString().substring(0, 28),
                "tipo", "LO", "orgaoEmissor", "FEPAM", "dataEmissao", hoje().minusYears(1).toString(),
                "dataValidade", validade.toString()));
    }

    private Map<String, Object> emissao(long unidade) {
        return new HashMap<>(Map.of("idUnidade", unidade, "fonte", "DIESEL", "escopo", 1, "qtdTco2e", 125));
    }

    @Test
    void unidadePermiteCrudEPreservaCamposMantidosPeloBanco() throws Exception {
        long id = criarUnidade();
        jdbc.update("UPDATE UNIDADE_OPERACIONAL SET EMISSAO_ACUMULADA_MES = 25, MES_REFERENCIA = '2026-01' WHERE ID_UNIDADE = ?", id);
        mvc.perform(get("/api/unidades/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("idUnidade").value(id)).andExpect(jsonPath("emissaoAcumuladaMes").value(25));
        mvc.perform(get("/api/unidades")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idUnidade == " + id + ")]").isNotEmpty());
        Map<String, Object> alterada = unidade();
        alterada.put("nome", "Nome atualizado");
        alterada.put("emissaoAcumuladaMes", 999);
        alterada.put("mesReferencia", "2099-12");
        mvc.perform(put("/api/unidades/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(alterada)))
                .andExpect(status().isOk()).andExpect(jsonPath("nome").value("Nome atualizado"))
                .andExpect(jsonPath("emissaoAcumuladaMes").value(25)).andExpect(jsonPath("mesReferencia").value("2026-01"));
        mvc.perform(delete("/api/unidades/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/unidades/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void recursosInexistentesRetornam404() throws Exception {
        for (String recurso : new String[]{"unidades", "licencas", "emissoes", "compensacoes", "alertas"}) {
            mvc.perform(get("/api/" + recurso + "/9223372036854775807")).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/unidades/9223372036854775807")).andExpect(status().isNotFound());
        mvc.perform(put("/api/unidades/9223372036854775807").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(unidade()))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/licencas/9223372036854775807")).andExpect(status().isNotFound());
        mvc.perform(put("/api/licencas/9223372036854775807").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(licenca(Long.MAX_VALUE, hoje().plusYears(1))))).andExpect(status().isNotFound());
    }

    @Test
    void unidadeValidaCamposObrigatoriosELimite() throws Exception {
        Map<String, Object> dados = unidade();
        dados.put("nome", "");
        dados.put("limiteCo2Mensal", 0);
        dados.put("uf", "RSA");
        enviar("/api/unidades", dados).andExpect(status().isBadRequest());
        enviar("/api/unidades", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void unidadeComLicencaNaoPodeSerExcluida() throws Exception {
        long id = criarUnidade();
        enviar("/api/licencas", licenca(id, hoje().plusYears(1))).andExpect(status().isCreated());
        mvc.perform(delete("/api/unidades/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("erro").isNotEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Usuário já cadastrado"))));
    }

    @Test
    void licencaPermiteCrudEStatusVemDoTrigger() throws Exception {
        long unidade = criarUnidade();
        Map<String, Object> dados = licenca(unidade, hoje().plusYears(1));
        dados.put("status", "VENCIDA");
        long id = resposta(enviar("/api/licencas", dados).andExpect(status().isCreated())
                .andExpect(jsonPath("status").value("VIGENTE"))).get("idLicenca").asLong();
        mvc.perform(get("/api/licencas/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("tipo").value("LO"));
        mvc.perform(get("/api/licencas")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idLicenca == " + id + ")]").isNotEmpty());
        dados.put("dataValidade", hoje().plusDays(60).toString());
        mvc.perform(put("/api/licencas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("A_RENOVAR"));
        dados.put("dataValidade", hoje().minusDays(1).toString());
        mvc.perform(put("/api/licencas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("VENCIDA"));
        dados.put("status", "SUSPENSA");
        dados.put("dataValidade", hoje().plusYears(1).toString());
        mvc.perform(put("/api/licencas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("SUSPENSA"));
        dados.remove("status");
        mvc.perform(put("/api/licencas/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("VIGENTE"));
        mvc.perform(delete("/api/licencas/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/licencas/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void licencaValidaDatasTipoStatusEUnidade() throws Exception {
        long id = criarUnidade();
        Map<String, Object> dados = licenca(id, hoje().plusYears(1));
        dados.put("dataValidade", dados.get("dataEmissao"));
        enviar("/api/licencas", dados).andExpect(status().isBadRequest());
        dados.put("dataValidade", hoje().minusYears(2).toString());
        enviar("/api/licencas", dados).andExpect(status().isBadRequest());
        dados = licenca(id, hoje().plusYears(1));
        dados.put("tipo", "XX");
        enviar("/api/licencas", dados).andExpect(status().isBadRequest());
        dados.put("tipo", "LO");
        dados.put("status", "INVALIDO");
        enviar("/api/licencas", dados).andExpect(status().isBadRequest());
        enviar("/api/licencas", licenca(Long.MAX_VALUE, hoje().plusYears(1))).andExpect(status().isNotFound());
    }

    @Test
    void numeroDeLicencaDuplicadoRetorna409() throws Exception {
        long id = criarUnidade();
        Map<String, Object> dados = licenca(id, hoje().plusYears(1));
        enviar("/api/licencas", dados).andExpect(status().isCreated());
        enviar("/api/licencas", dados).andExpect(status().isConflict()).andExpect(jsonPath("erro").isNotEmpty());
    }

    @Test
    void emissaoValidaQuantidadeEscopoFonteEUnidade() throws Exception {
        long id = criarUnidade();
        Map<String, Object> dados = emissao(id);
        dados.put("qtdTco2e", 0);
        enviar("/api/emissoes", dados).andExpect(status().isBadRequest());
        dados.put("qtdTco2e", -1);
        enviar("/api/emissoes", dados).andExpect(status().isBadRequest());
        dados.put("qtdTco2e", 1);
        dados.put("escopo", 4);
        enviar("/api/emissoes", dados).andExpect(status().isBadRequest());
        dados.put("escopo", 1);
        dados.put("fonte", "INVALIDA");
        enviar("/api/emissoes", dados).andExpect(status().isBadRequest());
        enviar("/api/emissoes", emissao(Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void emissaoAcumulaEGeraCompensacaoEAlertaSomentePeloBanco() throws Exception {
        long unidade = criarUnidade();
        enviar("/api/licencas", licenca(unidade, hoje().plusYears(1))).andExpect(status().isCreated());
        long emissao = resposta(enviar("/api/emissoes", emissao(unidade)).andExpect(status().isCreated())
                .andExpect(jsonPath("dataRegistro").value(LocalDate.now().toString()))).get("idEmissao").asLong();
        mvc.perform(get("/api/emissoes/{id}", emissao)).andExpect(status().isOk()).andExpect(jsonPath("qtdTco2e").value(125));
        mvc.perform(get("/api/emissoes")).andExpect(status().isOk());
        mvc.perform(get("/api/unidades/{id}", unidade)).andExpect(status().isOk())
                .andExpect(jsonPath("emissaoAcumuladaMes").value(125));
        long compensacao = jdbc.queryForObject("SELECT ID_COMPENSACAO FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", Long.class, unidade);
        mvc.perform(get("/api/compensacoes/{id}", compensacao)).andExpect(status().isOk())
                .andExpect(jsonPath("origem").value("AUTOMATICA")).andExpect(jsonPath("qtdTco2e").value(25));
        mvc.perform(get("/api/compensacoes")).andExpect(status().isOk());
        long alerta = jdbc.queryForObject("SELECT ID_ALERTA FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ? AND TIPO_ALERTA = 'LIMITE_EMISSAO'", Long.class, unidade);
        mvc.perform(get("/api/alertas/{id}", alerta)).andExpect(status().isOk()).andExpect(jsonPath("tipoAlerta").value("LIMITE_EMISSAO"));
        mvc.perform(get("/api/alertas")).andExpect(status().isOk());
        mvc.perform(put("/api/emissoes/{id}", emissao).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/emissoes/{id}", emissao)).andExpect(status().isNoContent());
        enviar("/api/compensacoes", Map.of()).andExpect(status().isBadRequest());
        enviar("/api/alertas", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void emissaoSemLoRetorna422EPreservaAlertaAutonomoComLimpeza() throws Exception {
        TransactionTemplate transacao = new TransactionTemplate(transacoes);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String nome = "Teste API autonomo " + UUID.randomUUID();
        Long unidade = transacao.execute(status -> {
            jdbc.update("INSERT INTO UNIDADE_OPERACIONAL (NOME, CIDADE, UF, SETOR, LIMITE_CO2_MENSAL) VALUES (?, 'Caxias do Sul', 'RS', 'Metalurgia', 100)", nome);
            return jdbc.queryForObject("SELECT ID_UNIDADE FROM UNIDADE_OPERACIONAL WHERE NOME = ?", Long.class, nome);
        });
        try {
            enviar("/api/emissoes", emissao(unidade)).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("erro").value(org.hamcrest.Matchers.containsString("sem Licença de Operação válida")));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM EMISSAO_CARBONO WHERE ID_UNIDADE = ?", Integer.class, unidade));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ? AND TIPO_ALERTA = 'OPERACAO_SEM_LICENCA'", Integer.class, unidade));
        } finally {
            transacao.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM ALERTA_COMPLIANCE WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM COMPENSACAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM EMISSAO_CARBONO WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM LICENCA_AMBIENTAL WHERE ID_UNIDADE = ?", unidade);
                jdbc.update("DELETE FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", unidade);
            });
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM UNIDADE_OPERACIONAL WHERE ID_UNIDADE = ?", Integer.class, unidade));
    }

    @Test
    void bearerInvalidoRetorna401SemErroDeNull() throws Exception {
        mvc.perform(get("/api/unidades").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
    }
}
