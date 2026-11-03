package com.rohub.medicoes.medicoes_api.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FaturamentoScheduler {
    private final FaturamentoService faturamento;

    public FaturamentoScheduler(FaturamentoService faturamento) {
        this.faturamento = faturamento;
    }

    @Scheduled(cron = "5 0 0 * * *", zone = "America/Sao_Paulo")
    public void executar() {
        faturamento.emitirAutomaticamente();
    }
}
