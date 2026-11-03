package com.rohub.medicoes.medicoes_api.domain;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaturaTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-12-31T02:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    final Instalacao instalacao = new Instalacao();

    Fatura fatura(String anterior, String atual, String tarifa) {
        return new Fatura(new Medicao(instalacao, clock.instant().minusSeconds(2), new BigDecimal(anterior)),
                new Medicao(instalacao, clock.instant().minusSeconds(1), new BigDecimal(atual)),
                YearMonth.of(2026, 10), new BigDecimal(tarifa), clock);
    }

    @Test void datasUsamEmissaoRealNoFusoLocalComVencimentoNoProximoMes() {
        var f = fatura("100", "115.001", "3.25");
        assertEquals(new BigDecimal("15.001"), f.getConsumo());
        assertEquals(new BigDecimal("48.75"), f.getTotal());
        assertEquals("2026-10", f.getCompetencia());
        assertEquals(LocalDate.of(2026, 12, 30), f.getEmissao());
        assertEquals(LocalDate.of(2027, 1, 9), f.getVencimento());
    }

    @Test void consumoZeroNaoExigePagamento() {
        assertEquals(Fatura.Situacao.SEM_VALOR, fatura("100", "100", "1").getSituacao());
    }

    @Test void rejeitaConsumoNegativo() {
        assertThrows(IllegalArgumentException.class, () -> fatura("100", "90", "1"));
    }

    @Test void arredondaMeioCentavoParaCimaEPreservaTarifa() {
        var f = fatura("100", "101", "1.005000");
        assertEquals(new BigDecimal("1.01"), f.getTotal());
        assertEquals(new BigDecimal("1.005000"), f.getTarifa());
    }

    @Test void quitaSomenteValorIntegralComInstante() {
        var f = fatura("100", "110", "1");
        f.atualizarBanco("id", "pix", "PAGA", BigDecimal.ONE, clock.instant());
        assertEquals(Fatura.Situacao.ABERTA, f.getSituacao());
        f.atualizarBanco("id", "pix", "PAGA", BigDecimal.TEN, null);
        assertEquals(Fatura.Situacao.ABERTA, f.getSituacao());
        f.atualizarBanco("id", "pix", "PAGA", BigDecimal.TEN, clock.instant());
        assertEquals(Fatura.Situacao.PAGA, f.getSituacao());
    }

    @Test void validaTarifaNaInicializacao() {
        assertThrows(IllegalArgumentException.class, () -> new Propriedades(true, null));
        assertThrows(IllegalArgumentException.class, () -> new Propriedades(true, BigDecimal.ZERO));
        assertDoesNotThrow(() -> new Propriedades(false, null));
    }
}
