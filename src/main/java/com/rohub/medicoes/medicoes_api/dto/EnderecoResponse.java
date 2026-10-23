package com.rohub.medicoes.medicoes_api.dto;


public record EnderecoResponse(Long id, String logradouro, String numero,
    String complemento, String bairro, String cidade, String uf, String cep) {}
