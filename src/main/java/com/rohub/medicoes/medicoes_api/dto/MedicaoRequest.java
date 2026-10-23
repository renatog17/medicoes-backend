package com.rohub.medicoes.medicoes_api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record MedicaoRequest(
        @NotNull @Positive Long instalacaoId,
        @NotNull Instant dataMedicao,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 6) BigDecimal leitura) {
}
