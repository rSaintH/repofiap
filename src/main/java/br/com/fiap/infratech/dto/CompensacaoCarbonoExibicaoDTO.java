package br.com.fiap.infratech.dto;

import br.com.fiap.infratech.model.CompensacaoCarbono;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CompensacaoCarbonoExibicaoDTO(
        Long idCompensacao,
        Long idUnidade,
        String projeto,
        BigDecimal qtdTco2e,
        LocalDate dataRegistro,
        String status,
        String origem,
        String mesReferencia
) {
    public CompensacaoCarbonoExibicaoDTO(CompensacaoCarbono compensacaoCarbono) {
        this(
                compensacaoCarbono.getIdCompensacao(),
                compensacaoCarbono.getIdUnidade(),
                compensacaoCarbono.getProjeto(),
                compensacaoCarbono.getQtdTco2e(),
                compensacaoCarbono.getDataRegistro(),
                compensacaoCarbono.getStatus(),
                compensacaoCarbono.getOrigem(),
                compensacaoCarbono.getMesReferencia()
        );
    }
}
