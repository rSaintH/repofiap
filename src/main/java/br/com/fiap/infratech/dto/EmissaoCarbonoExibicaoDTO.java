package br.com.fiap.infratech.dto;

import br.com.fiap.infratech.model.EmissaoCarbono;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmissaoCarbonoExibicaoDTO(
        Long idEmissao,
        Long idUnidade,
        LocalDate dataRegistro,
        String fonte,
        Integer escopo,
        BigDecimal qtdTco2e
) {
    public EmissaoCarbonoExibicaoDTO(EmissaoCarbono emissaoCarbono) {
        this(
                emissaoCarbono.getIdEmissao(),
                emissaoCarbono.getIdUnidade(),
                emissaoCarbono.getDataRegistro(),
                emissaoCarbono.getFonte(),
                emissaoCarbono.getEscopo(),
                emissaoCarbono.getQtdTco2e()
        );
    }
}
