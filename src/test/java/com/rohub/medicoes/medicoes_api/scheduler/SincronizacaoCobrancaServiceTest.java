package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Banco;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.integracao.BancoIndisponivelException;
import com.rohub.medicoes.medicoes_api.repository.FaturaRepository;
import com.rohub.medicoes.medicoes_api.service.FaturaBancoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class SincronizacaoCobrancaServiceTest {
    final FaturaRepository faturas = mock(FaturaRepository.class);
    final FaturaBancoService banco = mock(FaturaBancoService.class);

    SincronizacaoCobrancaService service(boolean habilitado) {
        return new SincronizacaoCobrancaService(faturas, banco,
                new Banco(habilitado, "http://banco", "segredo", 5000));
    }

    @Test void desabilitadoNaoConsultaBancoNemRepositorio() {
        service(false).sincronizarPendentes();
        verifyNoInteractions(faturas, banco);
    }

    @Test void semFaturasAbertasNaoConsultaBanco() {
        when(faturas.findBySituacao(Fatura.Situacao.ABERTA)).thenReturn(List.of());
        service(true).sincronizarPendentes();
        verifyNoInteractions(banco);
    }

    @Test void consultaFaturasAbertasEContinuaAposFalhaIndividual() {
        var primeira = mock(Fatura.class);
        var segunda = mock(Fatura.class);
        when(primeira.getId()).thenReturn(7L);
        when(segunda.getId()).thenReturn(8L);
        when(faturas.findBySituacao(Fatura.Situacao.ABERTA)).thenReturn(List.of(primeira, segunda));
        when(banco.sincronizar(7L)).thenThrow(new BancoIndisponivelException("Timeout"));
        service(true).sincronizarPendentes();
        var ordem = inOrder(banco);
        ordem.verify(banco).sincronizar(7L);
        ordem.verify(banco).sincronizar(8L);
        verify(banco, never()).registrar(any());
    }
}
