package com.rohub.medicoes.medicoes_api.scheduler;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FaturamentoSchedulerTest {
    @Test void executaTodosOsDiasCincoSegundosAposMeiaNoiteDeBrasilia() throws Exception {
        var agendamento = FaturamentoScheduler.class.getMethod("executar").getAnnotation(Scheduled.class);
        assertEquals("America/Sao_Paulo", agendamento.zone());
        var inicio = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 0, ZoneId.of(agendamento.zone()));
        var cron = CronExpression.parse(agendamento.cron());
        var primeira = inicio.toLocalDate().plusDays(1).atTime(0, 0, 5).atZone(inicio.getZone());
        assertEquals(primeira, cron.next(inicio));
        assertEquals(primeira.plusDays(1), cron.next(primeira));
        assertEquals(primeira.plusDays(2), cron.next(primeira.plusDays(1)));
    }

    @Test void agendamentoDelegaAoServico() {
        var service = mock(FaturamentoService.class);
        var scheduler = new FaturamentoScheduler(service);
        verifyNoInteractions(service);
        scheduler.executar();
        verify(service).emitirAutomaticamente();
        verifyNoMoreInteractions(service);
    }
}
