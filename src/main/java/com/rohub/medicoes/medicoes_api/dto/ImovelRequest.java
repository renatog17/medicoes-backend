package com.rohub.medicoes.medicoes_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record ImovelRequest(@NotNull @Valid EnderecoRequest endereco) {}
