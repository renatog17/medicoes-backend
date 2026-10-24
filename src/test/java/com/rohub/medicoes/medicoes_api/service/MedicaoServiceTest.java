package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MedicaoServiceTest {
    private final MedicaoRepository medicoes = mock(MedicaoRepository.class);
    private final InstalacaoRepository instalacoes = mock(InstalacaoRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.of("America/Bahia"));
    private final MedicaoService service = new MedicaoService(medicoes, instalacoes, clock);
    private final Instant inicio = Instant.parse("2026-01-01T00:00:00Z");
    private final Instalacao instalacao = new Instalacao(new Cliente(), new Imovel(), new Medidor(), inicio);

    @BeforeEach
    void preparar() {
        when(instalacoes.buscarComBloqueio(1L)).thenReturn(Optional.of(instalacao));
        when(medicoes.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "125.123456"})
    void aceitaReferenciaInicialSemExigirLeituraZero(String leitura) {
        var resposta = service.criar(new MedicaoRequest(1L, inicio, new BigDecimal(leitura)));
        assertEquals(new BigDecimal(leitura), resposta.leitura());
        assertEquals(inicio, resposta.dataMedicao());
        var ordem = inOrder(instalacoes, medicoes);
        ordem.verify(instalacoes).buscarComBloqueio(1L);
        ordem.verify(medicoes).findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L);
        ordem.verify(medicoes).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.000000", "11"})
    void aceitaLeituraIgualOuMaior(String leitura) {
        when(medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L))
                .thenReturn(Optional.of(new Medicao(instalacao, inicio, BigDecimal.TEN)));
        assertEquals(new BigDecimal(leitura), service.criar(
                new MedicaoRequest(1L, Instant.parse("2026-02-01T03:00:00Z"), new BigDecimal(leitura))).leitura());
    }

    @Test
    void rejeitaReducaoEDatasSemAvanco() {
        when(medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L))
                .thenReturn(Optional.of(new Medicao(instalacao, inicio.plusSeconds(2), BigDecimal.TEN)));
        var erro = assertThrows(ConflitoException.class, () -> service.criar(
                new MedicaoRequest(1L, Instant.parse("2026-02-01T03:00:00Z"), BigDecimal.ONE)));
        assertEquals("Leitura nao pode ser menor que a ultima medicao", erro.getMessage());
        for (long segundos : new long[]{1, 2}) {
            assertThrows(ConflitoException.class, () -> service.criar(new MedicaoRequest(1L, inicio.plusSeconds(segundos), BigDecimal.TEN)));
        }
        verify(medicoes, never()).saveAndFlush(any());
    }

    @Test
    void validaPeriodoInclusiveLimites() {
        instalacao.setDataFim(inicio.plusSeconds(10));
        service.criar(new MedicaoRequest(1L, inicio, BigDecimal.ZERO));
        service.criar(new MedicaoRequest(1L, instalacao.getDataFim(), BigDecimal.ZERO));
        assertThrows(RequisicaoInvalidaException.class, () -> service.criar(new MedicaoRequest(1L, inicio.minusSeconds(1), BigDecimal.ZERO)));
        assertThrows(RequisicaoInvalidaException.class, () -> service.criar(new MedicaoRequest(1L, inicio.plusSeconds(11), BigDecimal.ZERO)));
    }

    @Test
    void recursosInexistentes() {
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(99L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.criar(new MedicaoRequest(99L, inicio, BigDecimal.ZERO)));
        verify(medicoes, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-15T12:00:00Z", "2026-02-01T02:59:59Z"})
    void rejeitaMesmoMesNaBahia(String data) {
        when(medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L))
                .thenReturn(Optional.of(new Medicao(instalacao, Instant.parse("2026-01-01T03:00:00Z"), BigDecimal.TEN)));
        var erro = assertThrows(ConflitoException.class, () -> service.criar(
                new MedicaoRequest(1L, Instant.parse(data), BigDecimal.TEN)));
        assertEquals("Ja existe uma medicao para esta instalacao neste mes", erro.getMessage());
        verify(medicoes, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-01T03:00:00Z", "2027-01-01T03:00:00Z"})
    void aceitaOutroMesOuOutroAno(String data) {
        when(medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L))
                .thenReturn(Optional.of(new Medicao(instalacao, Instant.parse("2026-01-31T23:00:00Z"), BigDecimal.TEN)));
        assertEquals(Instant.parse(data), service.criar(
                new MedicaoRequest(1L, Instant.parse(data), BigDecimal.TEN)).dataMedicao());
    }

    @Test
    void paginaComESemFiltro() {
        var pageable = Paginacao.of(0, 20);
        when(medicoes.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));
        when(medicoes.findByInstalacaoId(1L, pageable)).thenReturn(new PageImpl<>(List.of()));
        assertEquals(0, service.listar(0, 20, null).totalElements());
        assertEquals(0, service.listar(0, 20, 1L).totalElements());
        assertThrows(RequisicaoInvalidaException.class, () -> service.listar(-1, 20, null));
        assertThrows(RequisicaoInvalidaException.class, () -> service.listar(0, 101, null));
    }
}
