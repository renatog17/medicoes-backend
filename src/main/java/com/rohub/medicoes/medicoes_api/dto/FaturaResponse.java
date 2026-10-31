package com.rohub.medicoes.medicoes_api.dto;

import com.rohub.medicoes.medicoes_api.domain.Fatura;
import java.math.BigDecimal;
import java.time.*;

public record FaturaResponse(Long id, Long instalacaoId, Long clienteId, Long medicaoAnteriorId,
        Long medicaoAtualId, String competencia, BigDecimal consumo, BigDecimal tarifa, BigDecimal total,
        LocalDate emissao, LocalDate vencimento, Instant criadaEm, Fatura.Situacao situacao,
        String bancoId, String pix, String situacaoBancaria, BigDecimal valorPago, Instant pagoEm) {
    public static FaturaResponse from(Fatura f) {
        return new FaturaResponse(f.getId(), f.getInstalacaoId(), f.getClienteId(), f.getMedicaoAnteriorId(),
                f.getMedicaoAtualId(), f.getCompetencia(), f.getConsumo(), f.getTarifa(), f.getTotal(),
                f.getEmissao(), f.getVencimento(), f.getCriadaEm(), f.getSituacao(), f.getBancoId(),
                f.getPix(), f.getSituacaoBancaria(), f.getValorPago(), f.getPagoEm());
    }
}
