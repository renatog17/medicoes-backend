package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FaturaEmissaoServiceTest {
    final Clock clock = Clock.fixed(Instant.parse("2027-01-01T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    final FaturaRepository faturas = mock(FaturaRepository.class);
    final InstalacaoRepository instalacoes = mock(InstalacaoRepository.class);
    final MedicaoRepository medicoes = mock(MedicaoRepository.class);
    final Instalacao instalacao = mock(Instalacao.class);
    final YearMonth mes = YearMonth.of(2026, 12);
    final Instant inicio = Instant.parse("2026-12-01T03:00:00Z");
    final Instant fim = Instant.parse("2027-01-01T03:00:00Z");
    final FaturaEmissaoService service = new FaturaEmissaoService(faturas, instalacoes, medicoes,
            new Propriedades(true, new BigDecimal("3.25")), clock);

    Medicao leitura(String valor, Instant data) {
        when(instalacao.getCliente()).thenReturn(mock(Cliente.class));
        return new Medicao(instalacao, data, new BigDecimal(valor));
    }
    void preparar(Optional<Medicao> atual, Optional<Medicao> anterior) {
        when(instalacoes.buscarComBloqueio(1L)).thenReturn(Optional.of(instalacao));
        when(medicoes.findFirstByInstalacaoIdAndDataMedicaoGreaterThanEqualAndDataMedicaoLessThanOrderByDataMedicaoDesc(
                1L, inicio, fim)).thenReturn(atual);
        atual.ifPresent(m -> when(medicoes.findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDesc(
                1L, m.getDataMedicao())).thenReturn(anterior));
        when(faturas.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void calculaConsumoDatasEArredondamento() {
        var anterior = leitura("100", inicio.minusSeconds(1));
        var atual = leitura("115.001", inicio.plusSeconds(1));
        preparar(Optional.of(atual), Optional.of(anterior));
        assertEquals("EMITIDA", service.emitir(1L, mes).resultado());
        var captor = org.mockito.ArgumentCaptor.forClass(Fatura.class);
        verify(faturas).saveAndFlush(captor.capture());
        var f = captor.getValue();
        assertEquals(new BigDecimal("15.001"), f.getConsumo());
        assertEquals(new BigDecimal("48.75"), f.getTotal());
        assertEquals(LocalDate.of(2027, 1, 1), f.getEmissao());
        assertEquals(LocalDate.of(2027, 1, 10), f.getVencimento());
        assertEquals(clock.instant(), f.getCriadaEm());
    }
    @Test void calculaConsumoComReferenciaInicialNoMesmoMes() {
        var referencia = leitura("100", inicio);
        var mensal = leitura("115", inicio.plusSeconds(3600));
        preparar(Optional.of(mensal), Optional.of(referencia));
        assertEquals("EMITIDA", service.emitir(1L, mes).resultado());
        var captor = org.mockito.ArgumentCaptor.forClass(Fatura.class);
        verify(faturas).saveAndFlush(captor.capture());
        assertEquals(new BigDecimal("15"), captor.getValue().getConsumo());
        assertEquals(new BigDecimal("48.75"), captor.getValue().getTotal());
    }
    @Test void primeiraLeituraNaoCobra() {
        preparar(Optional.of(leitura("100", inicio)), Optional.empty());
        assertEquals("REFERENCIA_INICIAL", service.emitir(1L, mes).resultado());
        verify(faturas, never()).saveAndFlush(any());
    }
    @Test void ausenciaDeLeituraFicaPendente() {
        preparar(Optional.empty(), Optional.empty());
        assertEquals("SEM_LEITURA", service.emitir(1L, mes).resultado());
    }
    @Test void reprocessamentoPreservaFatura() {
        preparar(Optional.empty(), Optional.empty());
        var existente = mock(Fatura.class);
        when(faturas.findByInstalacaoIdAndCompetencia(1L, "2026-12")).thenReturn(Optional.of(existente));
        assertEquals("EXISTENTE", service.emitir(1L, mes).resultado());
        verifyNoInteractions(medicoes);
        verify(faturas, never()).saveAndFlush(any());
    }
    @Test void consumoZeroNaoExigePagamento() {
        var f = new Fatura(leitura("100", inicio.minusSeconds(1)), leitura("100", inicio), mes,
                BigDecimal.ONE, clock);
        assertEquals(Fatura.Situacao.SEM_VALOR, f.getSituacao());
    }
    @Test void quitaSomenteValorIntegralComInstante() {
        var f = new Fatura(leitura("100", inicio.minusSeconds(1)), leitura("110", inicio), mes,
                BigDecimal.ONE, clock);
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
    @Test void arredondaMeioCentavoParaCimaEPreservaTarifa() {
        var tarifa = new BigDecimal("1.005000");
        var f = new Fatura(leitura("100", inicio.minusSeconds(1)), leitura("101", inicio), mes, tarifa, clock);
        assertEquals(new BigDecimal("1.01"), f.getTotal());
        assertEquals(tarifa, f.getTarifa());
    }
}
