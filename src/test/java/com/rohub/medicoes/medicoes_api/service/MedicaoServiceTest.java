package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
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
    private final MedicaoService service = new MedicaoService(medicoes, instalacoes);
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
                new MedicaoRequest(1L, inicio.plusSeconds(1), new BigDecimal(leitura))).leitura());
    }

    @Test
    void rejeitaReducaoEDatasSemAvanco() {
        when(medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L))
                .thenReturn(Optional.of(new Medicao(instalacao, inicio.plusSeconds(2), BigDecimal.TEN)));
        assertThrows(ConflitoException.class, () -> service.criar(new MedicaoRequest(1L, inicio.plusSeconds(3), BigDecimal.ONE)));
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
