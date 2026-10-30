package br.com.fiap.infratech.dto;

import br.com.fiap.infratech.model.UnidadeOperacional;
import java.math.BigDecimal;

public record UnidadeOperacionalExibicaoDTO(
        Long idUnidade,
        String nome,
        String cidade,
        String uf,
        String setor,
        BigDecimal limiteCo2Mensal,
        BigDecimal emissaoAcumuladaMes,
        String mesReferencia
) {
    public UnidadeOperacionalExibicaoDTO(UnidadeOperacional unidade) {
        this(
                unidade.getIdUnidade(),
                unidade.getNome(),
                unidade.getCidade(),
                unidade.getUf(),
                unidade.getSetor(),
                unidade.getLimiteCo2Mensal(),
                unidade.getEmissaoAcumuladaMes(),
                unidade.getMesReferencia()
        );
    }
}
