package br.com.fiap.infratech.dto;

import br.com.fiap.infratech.model.LicencaAmbiental;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LicencaAmbientalExibicaoDTO(
        Long idLicenca,
        Long idUnidade,
        String numero,
        String tipo,
        String orgaoEmissor,
        LocalDate dataEmissao,
        LocalDate dataValidade,
        String status
) {
    public LicencaAmbientalExibicaoDTO(LicencaAmbiental licencaAmbiental) {
        this(
                licencaAmbiental.getIdLicenca(),
                licencaAmbiental.getIdUnidade(),
                licencaAmbiental.getNumero(),
                licencaAmbiental.getTipo(),
                licencaAmbiental.getOrgaoEmissor(),
                licencaAmbiental.getDataEmissao(),
                licencaAmbiental.getDataValidade(),
                licencaAmbiental.getStatus()
        );
    }
}
