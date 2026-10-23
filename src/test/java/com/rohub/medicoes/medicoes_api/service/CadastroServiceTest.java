package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.domain.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CadastroServiceTest {
    @Test
    void excluiMedidorLogicamentePreservandoDados() {
        var repo = mock(MedidorRepository.class);
        var medidor = new Medidor("SERIE");
        medidor.setFabricante("Fabricante");
        medidor.setModelo("Modelo");
        when(repo.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(medidor));
        new MedidorService(repo, mock(InstalacaoRepository.class)).excluir(1L);
        assertTrue(medidor.isExcluido());
        assertEquals("SERIE", medidor.getNumeroSerie());
        assertEquals("Fabricante", medidor.getFabricante());
        assertEquals("Modelo", medidor.getModelo());
        verify(repo).saveAndFlush(medidor);
        verify(repo, never()).delete(any());
    }

    @Test
    void medidorExcluidoNaoPodeSerConsultadoAlteradoOuExcluidoNovamente() {
        var repo = mock(MedidorRepository.class);
        var service = new MedidorService(repo, mock(InstalacaoRepository.class));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(1L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(1L, null));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(1L));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void exclusaoLogicaDeImovelPreservaEndereco() {
        var imoveis = mock(ImovelRepository.class);
        var endereco = new Endereco();
        var imovel = new Imovel(endereco);
        when(imoveis.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(imovel));
        new ImovelService(imoveis, mock(InstalacaoRepository.class), null).excluir(1L);
        assertTrue(imovel.isExcluido());
        assertSame(endereco, imovel.getEndereco());
        verify(imoveis).saveAndFlush(imovel);
        verify(imoveis, never()).delete(any());
    }

    @Test
    void imovelExcluidoNaoPodeSerConsultadoAlteradoOuExcluidoNovamente() {
        var imoveis = mock(ImovelRepository.class);
        var service = new ImovelService(imoveis, mock(InstalacaoRepository.class), null);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(1L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(1L, null));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(1L));
        verify(imoveis, never()).saveAndFlush(any());
    }

    @Test
    void exclusaoLogicaPreservaRegistroEEndereco() {
        var clientes = mock(ClienteRepository.class);
        var instalacoes = mock(InstalacaoRepository.class);
        var endereco = new Endereco();
        var cliente = new Cliente("Cliente", endereco);
        cliente.setCin("52998224725");
        when(clientes.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(cliente));
        new ClienteService(clientes, instalacoes, null).excluir(1L);
        assertTrue(cliente.isExcluido());
        assertSame(endereco, cliente.getEnderecoCobranca());
        assertEquals("52998224725", cliente.getCin());
        verify(clientes).saveAndFlush(cliente);
        verify(clientes, never()).delete(any());
    }

    @Test
    void clienteExcluidoNaoPodeSerConsultadoAlteradoOuExcluidoNovamente() {
        var clientes = mock(ClienteRepository.class);
        var service = new ClienteService(clientes, mock(InstalacaoRepository.class), null);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.buscar(1L));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(1L, null));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(1L));
        verify(clientes, never()).saveAndFlush(any());
    }

    @Test
    void bloqueiaExclusaoDosTresCadastrosComHistorico() {
        var instalacoes = mock(InstalacaoRepository.class);
        var clientes = mock(ClienteRepository.class);
        var imoveis = mock(ImovelRepository.class);
        var medidores = mock(MedidorRepository.class);
        when(clientes.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(new Cliente()));
        when(imoveis.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(new Imovel()));
        when(medidores.findByIdAndExcluidoFalse(1L)).thenReturn(Optional.of(new Medidor()));
        when(instalacoes.existsByClienteId(1L)).thenReturn(true);
        when(instalacoes.existsByImovelId(1L)).thenReturn(true);
        when(instalacoes.existsByMedidorId(1L)).thenReturn(true);
        assertThrows(ConflitoException.class, () -> new ClienteService(clientes, instalacoes, null).excluir(1L));
        assertThrows(ConflitoException.class, () -> new ImovelService(imoveis, instalacoes, null).excluir(1L));
        assertThrows(ConflitoException.class, () -> new MedidorService(medidores, instalacoes).excluir(1L));
        verify(clientes, never()).delete(any());
        verify(imoveis, never()).delete(any());
        verify(medidores, never()).delete(any());
    }
    @Test
    void naoModificaEnderecoCompartilhado() {
        var repo = mock(EnderecoRepository.class);
        var clientes = mock(ClienteRepository.class);
        var imoveis = mock(ImovelRepository.class);
        var atual = new Endereco();
        atual.setCidade("Antiga");
        when(clientes.countByEnderecoCobrancaId(null)).thenReturn(2L);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var novo = new EnderecoService(repo, clientes, imoveis).salvar(atual,
            new EnderecoRequest(null, null, null, null, "Nova", null, null));
        assertNotSame(atual, novo);
        assertEquals("Antiga", atual.getCidade());
        assertEquals("Nova", novo.getCidade());
    }
    @Test
    void rejeitaSerieDuplicada() {
        var repo = mock(MedidorRepository.class);
        when(repo.existsByNumeroSerie("ABC")).thenReturn(true);
        var service = new MedidorService(repo, mock(InstalacaoRepository.class));
        assertThrows(ConflitoException.class, () -> service.criar(new MedidorRequest("ABC", null, null)));
    }
}
