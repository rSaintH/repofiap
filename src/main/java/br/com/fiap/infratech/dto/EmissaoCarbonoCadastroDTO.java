package br.com.fiap.infratech.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmissaoCarbonoCadastroDTO(
        @NotNull(message = "A unidade é obrigatória!")
        @Positive(message = "O identificador da unidade deve ser positivo!")
        Long idUnidade,

        LocalDate dataRegistro,

        @NotBlank(message = "A fonte é obrigatória!")
        @Pattern(regexp = "DIESEL|GASOLINA|GAS_NATURAL|ENERGIA_ELETRICA|GLP|PROCESSO", message = "Fonte de emissão inválida!")
        String fonte,

        @NotNull(message = "O escopo é obrigatório!")
        @Min(value = 1, message = "O escopo deve ser 1, 2 ou 3!")
        @Max(value = 3, message = "O escopo deve ser 1, 2 ou 3!")
        Integer escopo,

        @NotNull(message = "A quantidade é obrigatória!")
        @DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser positiva!")
        @Digits(integer = 9, fraction = 3, message = "A quantidade aceita até 9 inteiros e 3 casas decimais!")
        BigDecimal qtdTco2e
) {
}
