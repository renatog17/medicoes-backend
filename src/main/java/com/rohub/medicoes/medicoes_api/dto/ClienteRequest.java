package com.rohub.medicoes.medicoes_api.dto;

import jakarta.validation.Valid;
import com.rohub.medicoes.medicoes_api.validation.CinValido;
import jakarta.validation.constraints.*;
public record ClienteRequest(@NotBlank @Size(max = 255) String nome, @NotBlank @CinValido String cin,
    @Email @Size(max = 254) String email, @Size(max = 30) String telefone,
    @NotNull @Valid EnderecoRequest enderecoCobranca) {}
