package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.integracao.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaturaBancoServiceTest {
    final FaturaRepository faturas = mock(FaturaRepository.class);
    final ClienteRepository clientes = mock(ClienteRepository.class);
    final BancoGateway banco = mock(BancoGateway.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final FaturaBancoService service = new FaturaBancoService(faturas, clientes, banco, manager);
    Fatura fatura;
    @BeforeEach void preparar() {
        var instalacao = mock(Instalacao.class);
        var cliente = mock(Cliente.class);
        when(instalacao.getCliente()).thenReturn(cliente);
        when(cliente.getId()).thenReturn(2L);
        when(cliente.getNome()).thenReturn("Ana Silva");
        when(cliente.getCin()).thenReturn("12345678909");
        var clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        fatura = new Fatura(new Medicao(instalacao, clock.instant().minusSeconds(2), BigDecimal.ZERO),
                new Medicao(instalacao, clock.instant().minusSeconds(1), BigDecimal.TEN),
                YearMonth.of(2026, 10), BigDecimal.ONE, clock);
        ReflectionTestUtils.setField(fatura, "id", 1L);
        when(faturas.findById(1L)).thenReturn(Optional.of(fatura));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(clientes.findById(2L)).thenReturn(Optional.of(cliente));
    }
    BancoGateway.Cobranca cobranca(String referencia, String situacao) {
        return new BancoGateway.Cobranca("cob-1", referencia, BigDecimal.TEN, "BRL", situacao,
                "pix", "PAGA".equals(situacao) ? BigDecimal.TEN : null,
                "PAGA".equals(situacao) ? Instant.parse("2026-11-08T12:00:00Z") : null);
    }
    @Test void recuperaRegistroAposTimeoutSemDuplicar() {
        when(banco.buscarPorReferencia("fatura-1")).thenReturn(Optional.empty());
        when(banco.registrar(any())).thenThrow(new BancoIndisponivelException("Timeout"));
        assertThrows(BancoIndisponivelException.class, () -> service.registrar(1L));
        assertEquals(Fatura.Situacao.ABERTA, fatura.getSituacao());
        assertNull(fatura.getBancoId());
        when(banco.buscarPorReferencia("fatura-1")).thenReturn(Optional.of(cobranca("fatura-1", "PENDENTE")));
        assertEquals("cob-1", service.registrar(1L).bancoId());
        verify(banco, times(1)).registrar(any());
    }
    @Test void naoAceitaRespostaDeOutraFatura() {
        when(banco.buscarPorReferencia("fatura-1")).thenReturn(Optional.of(cobranca("fatura-2", "PAGA")));
        assertThrows(BancoIndisponivelException.class, () -> service.registrar(1L));
        assertEquals(Fatura.Situacao.ABERTA, fatura.getSituacao());
        verify(faturas, never()).saveAndFlush(any());
    }
    @Test void sincronizaPagamentoIntegral() {
        fatura.atualizarBanco("cob-1", "pix", "PENDENTE", null, null);
        when(banco.consultar("cob-1")).thenReturn(cobranca("fatura-1", "PAGA"));
        assertEquals(Fatura.Situacao.PAGA, service.sincronizar(1L).situacao());
        service.sincronizar(1L);
        verify(banco, times(1)).consultar("cob-1");
    }
    @Test void consumoZeroNaoChamaBanco() {
        ReflectionTestUtils.setField(fatura, "situacao", Fatura.Situacao.SEM_VALOR);
        service.registrar(1L);
        verifyNoInteractions(banco);
    }
    @Test void registraDadosDaFaturaEDoPagador() {
        when(banco.buscarPorReferencia("fatura-1")).thenReturn(Optional.empty());
        when(banco.registrar(any())).thenReturn(cobranca("fatura-1", "PENDENTE"));
        var resposta = service.registrar(1L);
        verify(banco).registrar(new BancoGateway.Registro("fatura-1", fatura.getTotal(), "BRL",
                LocalDate.of(2026, 11, 11), new BancoGateway.Pagador("Ana Silva", "12345678909")));
        assertEquals("cob-1", resposta.bancoId());
        assertEquals("pix", resposta.pix());
        assertEquals(2L, resposta.clienteId());
        assertEquals(Fatura.Situacao.ABERTA, resposta.situacao());
    }
    @Test void sincronizacaoSemIdentificacaoBancariaNaoEnviaCobranca() {
        var resposta = service.sincronizar(1L);
        assertNull(resposta.bancoId());
        assertEquals(Fatura.Situacao.ABERTA, resposta.situacao());
        verifyNoInteractions(banco);
        verify(faturas, never()).saveAndFlush(any());
    }
    @Test void cancelamentoEExpiracaoNaoQuitamNemRecriamCobranca() {
        for (String situacao : new String[] {"CANCELADA", "EXPIRADA"}) {
            fatura.atualizarBanco("cob-1", "pix", "PENDENTE", null, null);
            when(banco.consultar("cob-1")).thenReturn(cobranca("fatura-1", situacao));
            var resposta = service.sincronizar(1L);
            assertEquals(Fatura.Situacao.ABERTA, resposta.situacao());
            assertEquals(situacao, resposta.situacaoBancaria());
            service.registrar(1L);
        }
        verify(banco, never()).registrar(any());
        verify(banco, never()).buscarPorReferencia(any());
    }
    @Test void pagamentoIncompletoNaoQuita() {
        fatura.atualizarBanco("cob-1", "pix", "PENDENTE", null, null);
        when(banco.consultar("cob-1")).thenReturn(new BancoGateway.Cobranca("cob-1", "fatura-1",
                BigDecimal.TEN, "BRL", "PAGA", "pix", BigDecimal.ONE, Instant.now()));
        assertEquals(Fatura.Situacao.ABERTA, service.sincronizar(1L).situacao());
        when(banco.consultar("cob-1")).thenReturn(new BancoGateway.Cobranca("cob-1", "fatura-1",
                BigDecimal.TEN, "BRL", "PAGA", "pix", BigDecimal.TEN, null));
        assertEquals(Fatura.Situacao.ABERTA, service.sincronizar(1L).situacao());
    }
}
