package com.rohub.medicoes.medicoes_api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MedicaoResponse(Long id, Long instalacaoId, Instant dataMedicao, BigDecimal leitura) {
}
