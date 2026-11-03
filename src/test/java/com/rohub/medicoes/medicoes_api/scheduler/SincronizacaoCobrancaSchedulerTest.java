package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Banco;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SincronizacaoCobrancaSchedulerTest {
    @Test void preservaIntervaloConfiguravelDeTrintaMinutosAposExecucao() throws Exception {
        var agendamento = SincronizacaoCobrancaScheduler.class.getMethod("executar")
                .getAnnotation(Scheduled.class);
        assertEquals("${app.banco.sincronizacao-ms:1800000}", agendamento.fixedDelayString());
    }

    @Test void inicializacaoApenasInformaConfiguracaoEAgendamentoDelegaAoServico() {
        var service = mock(SincronizacaoCobrancaService.class);
        var scheduler = new SincronizacaoCobrancaScheduler(service,
                new Banco(false, null, null, 5000), 1800000);
        scheduler.informarConfiguracao();
        verifyNoInteractions(service);
        scheduler.executar();
        verify(service).sincronizarPendentes();
        verifyNoMoreInteractions(service);
    }
}
