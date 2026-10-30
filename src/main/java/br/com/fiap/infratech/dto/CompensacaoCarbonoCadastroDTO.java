package br.com.fiap.infratech.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CompensacaoCarbonoCadastroDTO(
        @NotNull @Positive Long idUnidade,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "REFLORESTAMENTO|CREDITO_CARBONO|ENERGIA_RENOVAVEL|A_DEFINIR") String projeto,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 9, fraction = 3) BigDecimal qtdTco2e,
        LocalDate dataRegistro,
        @Size(max = 12) @Pattern(regexp = "PENDENTE|EM_ANDAMENTO|CONCLUIDA") String status,
        @Size(max = 10) @Pattern(regexp = "MANUAL|AUTOMATICA") String origem
) {
}
