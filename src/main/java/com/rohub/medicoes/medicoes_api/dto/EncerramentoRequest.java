package com.rohub.medicoes.medicoes_api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record EncerramentoRequest(@NotNull Instant dataFim) {}
