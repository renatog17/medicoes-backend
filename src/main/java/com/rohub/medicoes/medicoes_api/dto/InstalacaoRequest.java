package com.rohub.medicoes.medicoes_api.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record InstalacaoRequest(
        @NotNull @Positive Long clienteId,
        @NotNull @Positive Long imovelId,
        @NotNull @Positive Long medidorId,
        @NotNull Instant dataInicio,
        @Size(max = 2000) String observacoes) {
}
