package com.rohub.medicoes.medicoes_api.dto;

import java.time.Instant;
public record InstalacaoResponse(Long id, Long clienteId, Long imovelId, Long medidorId,
    Instant dataInicio, Instant dataFim, String observacoes, long versao, boolean ativa) {}
