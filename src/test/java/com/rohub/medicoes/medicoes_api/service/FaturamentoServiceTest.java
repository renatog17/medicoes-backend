package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.*;
import com.rohub.medicoes.medicoes_api.domain.Instalacao;
import com.rohub.medicoes.medicoes_api.integracao.BancoIndisponivelException;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class FaturamentoServiceTest {
    @Test void agendaFaturamentoDiarioNoHorarioDeBrasilia() throws Exception {
        var agendamento = FaturamentoService.class.getMethod("emitirAutomaticamente")
                .getAnnotation(Scheduled.class);
        assertNotNull(agendamento);
        assertEquals("America/Sao_Paulo", agendamento.zone());
        var inicio = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, ZoneId.of(agendamento.zone()));
        var cron = CronExpression.parse(agendamento.cron());
        var primeira = cron.next(inicio);
        assertEquals(inicio.toLocalDate().plusDays(1).atTime(0, 0, 5).atZone(inicio.getZone()), primeira);
        assertEquals(primeira.plusDays(1), cron.next(primeira));
    }

    final InstalacaoRepository instalacoes = mock(InstalacaoRepository.class);
    final FaturaRepository faturas = mock(FaturaRepository.class);
    final FaturaEmissaoService emissao = mock(FaturaEmissaoService.class);
    final FaturaBancoService banco = mock(FaturaBancoService.class);
    final Clock clock = Clock.fixed(Instant.parse("2027-01-01T02:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    FaturamentoService service(boolean habilitado, boolean bancoHabilitado) {
        return new FaturamentoService(instalacoes, faturas, emissao, banco,
                new Propriedades(habilitado, BigDecimal.ONE),
                new Banco(bancoHabilitado, "http://banco", "segredo", 1000), clock);
    }
    void instalacao() {
        var i = mock(Instalacao.class);
        when(i.getId()).thenReturn(1L);
        when(instalacoes.findAll()).thenReturn(List.of(i));
    }
    @Test void usaMesLocalERecuperaAgendamentoSemBanco() {
        instalacao();
        var competencia = YearMonth.of(2026, 11);
        when(emissao.emitir(1L, competencia)).thenReturn(new FaturaEmissaoService.Resultado(7L, "EMITIDA"));
        service(true, false).emitirAutomaticamente();
        verify(emissao).emitir(1L, competencia);
        verifyNoInteractions(banco);
    }
    @Test void falhaBancariaPreservaContagemDeEmitidas() {
        instalacao();
        when(emissao.emitir(1L, YearMonth.of(2026, 11)))
                .thenReturn(new FaturaEmissaoService.Resultado(7L, "EMITIDA"));
        when(banco.registrar(7L)).thenThrow(new BancoIndisponivelException("Timeout"));
        var lote = service(true, true).emitir(YearMonth.of(2026, 11));
        assertEquals(1, lote.emitidas());
        assertEquals("BANCO", lote.falhas().getFirst().etapa());
        assertEquals(7L, lote.falhas().getFirst().faturaId());
    }
    @Test void desabilitadoNaoExecutaAgendamentos() {
        var service = service(false, false);
        service.emitirAutomaticamente();
        service.sincronizarPendentes();
        verifyNoInteractions(emissao, banco, faturas, instalacoes);
    }
    @Test void emissaoAutomaticaRegistraCobranca() {
        instalacao();
        when(emissao.emitir(1L, YearMonth.of(2026, 11)))
                .thenReturn(new FaturaEmissaoService.Resultado(7L, "EMITIDA"));
        service(true, true).emitirAutomaticamente();
        verify(banco).registrar(7L);
    }
    @Test void sincronizacaoContinuaAposFalhaEmOutraFatura() {
        var primeira = mock(com.rohub.medicoes.medicoes_api.domain.Fatura.class);
        var segunda = mock(com.rohub.medicoes.medicoes_api.domain.Fatura.class);
        when(primeira.getId()).thenReturn(7L);
        when(segunda.getId()).thenReturn(8L);
        when(faturas.findBySituacao(com.rohub.medicoes.medicoes_api.domain.Fatura.Situacao.ABERTA))
                .thenReturn(List.of(primeira, segunda));
        when(banco.sincronizar(7L)).thenThrow(new BancoIndisponivelException("Timeout"));
        service(true, true).sincronizarPendentes();
        verify(banco).sincronizar(7L);
        verify(banco).sincronizar(8L);
    }
}
