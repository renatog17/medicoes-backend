package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaturamentoServiceTest {
    final InstalacaoRepository instalacoes = mock(InstalacaoRepository.class);
    final MedicaoRepository medicoes = mock(MedicaoRepository.class);
    final FaturaRepository faturas = mock(FaturaRepository.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-12-20T03:00:05Z"), ZoneId.of("America/Sao_Paulo"));
    final Instalacao instalacao = new Instalacao(new Cliente(), new Imovel(), new Medidor(),
            Instant.parse("2026-01-01T03:00:00Z"));

    @BeforeEach void preparar() {
        ReflectionTestUtils.setField(instalacao, "id", 1L);
        instalacao.setDiaEmissao(20);
        when(manager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
    }

    FaturamentoService service(Clock clock, boolean habilitado) {
        return new FaturamentoService(instalacoes, medicoes, faturas,
                new Propriedades(habilitado, new BigDecimal("3.25")), clock, manager);
    }

    Medicao prepararMedicao(Long id, String data, String leitura, String anterior) {
        var atual = new Medicao(instalacao, Instant.parse(data), new BigDecimal(leitura));
        ReflectionTestUtils.setField(atual, "id", id);
        when(medicoes.buscarInstalacaoId(id)).thenReturn(Optional.of(1L));
        when(instalacoes.buscarComBloqueio(1L)).thenReturn(Optional.of(instalacao));
        when(medicoes.findById(id)).thenReturn(Optional.of(atual));
        when(medicoes.findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDescIdDesc(
                1L, atual.getDataMedicao())).thenReturn(Optional.of(
                        new Medicao(instalacao, atual.getDataMedicao().minusSeconds(1), new BigDecimal(anterior))));
        return atual;
    }

    void pendentes(Long... ids) {
        when(medicoes.buscarPendentesDeEmissao(20, 31, clock.instant())).thenReturn(List.of(ids));
    }

    @Test void emiteTodasAsPendentesComCompetenciaHistoricaEDatasReais() {
        prepararMedicao(7L, "2026-10-10T03:00:00Z", "115.001", "100");
        prepararMedicao(8L, "2026-10-20T03:00:00Z", "120.001", "115.001");
        pendentes(7L, 8L);

        service(clock, true).emitirAutomaticamente();

        var captor = ArgumentCaptor.forClass(Fatura.class);
        verify(faturas, times(2)).saveAndFlush(captor.capture());
        var primeira = captor.getAllValues().getFirst();
        assertEquals("2026-10", primeira.getCompetencia());
        assertEquals(new BigDecimal("15.001"), primeira.getConsumo());
        assertEquals(new BigDecimal("48.75"), primeira.getTotal());
        assertEquals(LocalDate.of(2026, 12, 20), primeira.getEmissao());
        assertEquals(LocalDate.of(2026, 12, 30), primeira.getVencimento());
        assertEquals(clock.instant(), primeira.getCriadaEm());
        assertEquals("2026-10", captor.getAllValues().getLast().getCompetencia());
        var definicoes = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, times(2)).getTransaction(definicoes.capture());
        assertTrue(definicoes.getAllValues().stream().allMatch(
                d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(manager, times(2)).commit(any());
    }

    @Test void falhaDesfazSomenteUmaMedicaoMarcaEmOutraTransacaoEContinua() {
        prepararMedicao(7L, "2026-10-10T03:00:00Z", "90", "100");
        prepararMedicao(8L, "2026-11-10T03:00:00Z", "110", "90");
        pendentes(7L, 8L);

        service(clock, true).emitirAutomaticamente();

        var ordem = inOrder(manager, medicoes, faturas);
        ordem.verify(manager).rollback(any());
        ordem.verify(medicoes).marcarFalhaEmissaoFatura(7L);
        ordem.verify(manager).commit(any());
        ordem.verify(faturas).saveAndFlush(any());
        ordem.verify(manager).commit(any());
        verify(medicoes, never()).marcarFalhaEmissaoFatura(8L);
    }

    @Test void falhaNaPersistenciaDaMarcacaoPropagaErro() {
        prepararMedicao(7L, "2026-10-10T03:00:00Z", "90", "100");
        pendentes(7L);
        doThrow(new IllegalStateException("Sem banco")).when(medicoes).marcarFalhaEmissaoFatura(7L);
        assertThrows(IllegalStateException.class, () -> service(clock, true).emitirAutomaticamente());
        verify(manager, times(2)).rollback(any());
    }

    @Test void confereNovamenteSeMedicaoJaFoiFaturadaOuBloqueada() {
        prepararMedicao(7L, "2026-10-10T03:00:00Z", "110", "100");
        var bloqueada = prepararMedicao(8L, "2026-11-10T03:00:00Z", "120", "110");
        ReflectionTestUtils.setField(bloqueada, "falhaEmissaoFatura", true);
        when(faturas.existsByMedicaoAtualId(7L)).thenReturn(true);
        pendentes(7L, 8L);
        service(clock, true).emitirAutomaticamente();
        verify(faturas, never()).saveAndFlush(any());
        verify(medicoes, never()).marcarFalhaEmissaoFatura(any());
    }

    @Test void primeiraLeituraEFuturasNaoGeramFaturaNemFalha() {
        var primeira = prepararMedicao(7L, "2026-10-10T03:00:00Z", "100", "0");
        when(medicoes.findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDescIdDesc(
                1L, primeira.getDataMedicao())).thenReturn(Optional.empty());
        prepararMedicao(8L, "2027-01-01T03:00:00Z", "110", "100");
        pendentes(7L, 8L);
        service(clock, true).emitirAutomaticamente();
        verify(faturas, never()).saveAndFlush(any());
        verify(medicoes, never()).marcarFalhaEmissaoFatura(any());
    }

    @ParameterizedTest
    @CsvSource({"2027-02-28,29", "2027-02-28,30", "2027-02-28,31", "2028-02-29,31", "2026-04-30,31"})
    void ajustaDiaInexistenteParaUltimoDiaDoMes(LocalDate hoje, int diaEmissao) {
        var execucao = Clock.fixed(hoje.atStartOfDay(clock.getZone()).plusSeconds(5).toInstant(), clock.getZone());
        instalacao.setDiaEmissao(diaEmissao);
        prepararMedicao(7L, "2026-01-10T03:00:00Z", "110", "100");
        when(medicoes.buscarPendentesDeEmissao(hoje.getDayOfMonth(), hoje.lengthOfMonth(), execucao.instant()))
                .thenReturn(List.of(7L));
        service(execucao, true).emitirAutomaticamente();
        verify(faturas).saveAndFlush(any());
    }

    @Test void ignoraInstalacaoForaDoDia() {
        prepararMedicao(7L, "2026-10-10T03:00:00Z", "110", "100");
        instalacao.setDiaEmissao(21);
        pendentes(7L);
        service(clock, true).emitirAutomaticamente();
        verify(faturas, never()).saveAndFlush(any());
        verify(medicoes, never()).marcarFalhaEmissaoFatura(any());
    }

    @Test void desabilitadoNaoExecutaNada() {
        service(clock, false).emitirAutomaticamente();
        verifyNoInteractions(instalacoes, medicoes, faturas, manager);
    }
}
