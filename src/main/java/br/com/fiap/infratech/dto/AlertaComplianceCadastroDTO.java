package br.com.fiap.infratech.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record AlertaComplianceCadastroDTO(
        @NotNull @Positive Long idUnidade,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "RENOVACAO_LICENCA|LICENCA_VENCIDA|LIMITE_EMISSAO|OPERACAO_SEM_LICENCA|AUDITORIA_INTERNA") String tipoAlerta,
        @NotBlank @Size(max = 400) String descricao,
        LocalDate dataAlerta,
        @Size(max = 10) @Pattern(regexp = "ABERTO|EM_ANALISE|RESOLVIDO") String status
) {
}
