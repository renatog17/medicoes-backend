package com.rohub.medicoes.medicoes_api.dto;


public record ClienteResponse(Long id, String nome, String cin, String email, String telefone, EnderecoResponse enderecoCobranca) {}
