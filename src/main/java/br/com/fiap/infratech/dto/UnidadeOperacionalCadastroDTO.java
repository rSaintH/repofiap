package br.com.fiap.infratech.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UnidadeOperacionalCadastroDTO(
        @NotBlank(message = "O nome é obrigatório!")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres!")
        String nome,

        @NotBlank(message = "A cidade é obrigatória!")
        @Size(max = 60, message = "A cidade deve ter no máximo 60 caracteres!")
        String cidade,

        @NotBlank(message = "A UF é obrigatória!")
        @Pattern(regexp = "[A-Z]{2}", message = "A UF deve conter duas letras maiúsculas!")
        String uf,

        @NotBlank(message = "O setor é obrigatório!")
        @Size(max = 50, message = "O setor deve ter no máximo 50 caracteres!")
        String setor,

        @NotNull(message = "O limite mensal é obrigatório!")
        @DecimalMin(value = "0", inclusive = false, message = "O limite mensal deve ser positivo!")
        @Digits(integer = 9, fraction = 3, message = "O limite aceita até 9 inteiros e 3 casas decimais!")
        BigDecimal limiteCo2Mensal
) {
}
