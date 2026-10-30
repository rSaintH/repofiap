package br.com.fiap.infratech.dto;

import br.com.fiap.infratech.model.AlertaCompliance;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AlertaComplianceExibicaoDTO(
        Long idAlerta,
        Long idUnidade,
        String tipoAlerta,
        String descricao,
        LocalDate dataAlerta,
        String status
) {
    public AlertaComplianceExibicaoDTO(AlertaCompliance alertaCompliance) {
        this(
                alertaCompliance.getIdAlerta(),
                alertaCompliance.getIdUnidade(),
                alertaCompliance.getTipoAlerta(),
                alertaCompliance.getDescricao(),
                alertaCompliance.getDataAlerta(),
                alertaCompliance.getStatus()
        );
    }
}
