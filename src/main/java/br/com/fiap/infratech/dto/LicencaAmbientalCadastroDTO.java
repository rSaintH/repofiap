package br.com.fiap.infratech.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record LicencaAmbientalCadastroDTO(
        @NotNull(message = "A unidade é obrigatória!")
        @Positive(message = "O identificador da unidade deve ser positivo!")
        Long idUnidade,

        @NotBlank(message = "O número da licença é obrigatório!")
        @Size(max = 30, message = "O número deve ter no máximo 30 caracteres!")
        String numero,

        @NotBlank(message = "O tipo é obrigatório!")
        @Pattern(regexp = "LP|LI|LO", message = "O tipo deve ser LP, LI ou LO!")
        String tipo,

        @NotBlank(message = "O órgão emissor é obrigatório!")
        @Size(max = 60, message = "O órgão emissor deve ter no máximo 60 caracteres!")
        String orgaoEmissor,

        @NotNull(message = "A data de emissão é obrigatória!")
        LocalDate dataEmissao,

        @NotNull(message = "A data de validade é obrigatória!")
        LocalDate dataValidade,

        @Pattern(regexp = "VIGENTE|A_RENOVAR|VENCIDA|SUSPENSA", message = "Status de licença inválido!")
        String status
) {
}
