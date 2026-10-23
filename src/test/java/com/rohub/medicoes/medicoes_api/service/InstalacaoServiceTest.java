package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.domain.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstalacaoServiceTest {
    private final InstalacaoRepository instalacoes = mock(InstalacaoRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ImovelRepository imoveis = mock(ImovelRepository.class);
    private final MedidorRepository medidores = mock(MedidorRepository.class);
    private final MedicaoRepository medicoes = mock(MedicaoRepository.class);
    private final InstalacaoService service = new InstalacaoService(instalacoes, clientes, imoveis, medidores, medicoes);
    private final Instant inicio = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rejeitaMedidorExcluido() {
        when(clientes.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(new Cliente()));
        when(imoveis.findByIdAndExcluidoFalse(2L)).thenReturn(Optional.of(new Imovel()));
        assertThrows(RecursoNaoEncontradoException.class,
            () -> service.criar(new InstalacaoRequest(1L, 2L, 3L, inicio, null)));
        verify(medidores).findByIdAndExcluidoFalse(3L);
        verify(instalacoes, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaImovelExcluido() {
        when(clientes.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(new Cliente()));
        assertThrows(RecursoNaoEncontradoException.class,
            () -> service.criar(new InstalacaoRequest(1L, 2L, 3L, inicio, null)));
        verify(imoveis).findByIdAndExcluidoFalse(2L);
        verify(instalacoes, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaReferenciaInexistente() {
        assertThrows(RecursoNaoEncontradoException.class,
            () -> service.criar(new InstalacaoRequest(1L, 2L, 3L, inicio, null)));
        verify(instalacoes, never()).saveAndFlush(any());
    }
    @Test
    void rejeitaMedidorOcupado() {
        when(clientes.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(new Cliente()));
        when(imoveis.findByIdAndExcluidoFalse(2L)).thenReturn(Optional.of(new Imovel()));
        when(medidores.findByIdAndExcluidoFalse(3L)).thenReturn(Optional.of(new Medidor()));
        when(instalacoes.existsByMedidorIdAndDataFimIsNull(3L)).thenReturn(true);
        assertThrows(ConflitoException.class,
            () -> service.criar(new InstalacaoRequest(1L, 2L, 3L, inicio, null)));
        verify(instalacoes, never()).saveAndFlush(any());
    }
    @Test
    void rejeitaEncerramentoInvalidoSemAlterarEntidade() {
        var entidade = new Instalacao(new Cliente(), new Imovel(), new Medidor(), inicio);
        when(instalacoes.buscarComBloqueio(1L)).thenReturn(Optional.of(entidade));
        assertThrows(RequisicaoInvalidaException.class,
            () -> service.encerrar(1L, new EncerramentoRequest(inicio)));
        assertNull(entidade.getDataFim());
        assertThrows(RequisicaoInvalidaException.class,
            () -> service.encerrar(1L, new EncerramentoRequest(inicio.minusSeconds(1))));
    }
    @Test
    void encerraEPreservaVinculos() {
        var entidade = new Instalacao(new Cliente(), new Imovel(), new Medidor(), inicio);
        when(instalacoes.buscarComBloqueio(1L)).thenReturn(Optional.of(entidade));
        when(instalacoes.saveAndFlush(entidade)).thenReturn(entidade);
        var resposta = service.encerrar(1L, new EncerramentoRequest(inicio.plusSeconds(1)));
        assertFalse(resposta.ativa());
        assertEquals(inicio, resposta.dataInicio());
        assertThrows(ConflitoException.class,
            () -> service.encerrar(1L, new EncerramentoRequest(inicio.plusSeconds(2))));
    }
    @Test
    void validaPaginacao() {
        assertThrows(RequisicaoInvalidaException.class, () -> Paginacao.of(-1, 20));
        assertThrows(RequisicaoInvalidaException.class, () -> Paginacao.of(0, 101));
        assertEquals(20, Paginacao.of(0, 20).getPageSize());
    }
    @Test
    void encerramentoNaoPodeAntecederUltimaMedicao() {
        var entidade = new Instalacao(new Cliente(), new Imovel(), new Medidor(), inicio);
        when(instalacoes.buscarComBloqueio(1L)).thenReturn(Optional.of(entidade));
        when(medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(1L))
                .thenReturn(Optional.of(new Medicao(entidade, inicio.plusSeconds(20), java.math.BigDecimal.TEN)));
        assertThrows(RequisicaoInvalidaException.class,
                () -> service.encerrar(1L, new EncerramentoRequest(inicio.plusSeconds(10))));
        assertNull(entidade.getDataFim());
        verify(instalacoes, never()).saveAndFlush(any());
        when(instalacoes.saveAndFlush(entidade)).thenReturn(entidade);
        assertFalse(service.encerrar(1L, new EncerramentoRequest(inicio.plusSeconds(20))).ativa());
    }
}
