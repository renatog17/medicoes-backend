package com.rohub.medicoes.medicoes_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record MedidorRequest(@NotBlank @Size(max = 255) String numeroSerie,
    @Size(max = 100) String fabricante, @Size(max = 100) String modelo) {}
