package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Banco;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SincronizacaoCobrancaScheduler {
    private static final Logger log = LoggerFactory.getLogger(SincronizacaoCobrancaScheduler.class);
    private static final String INTERVALO = "${app.banco.sincronizacao-ms:1800000}";
    private final SincronizacaoCobrancaService sincronizacao;
    private final Banco config;
    private final long intervaloMs;

    public SincronizacaoCobrancaScheduler(SincronizacaoCobrancaService sincronizacao, Banco config,
            @Value(INTERVALO) long intervaloMs) {
        this.sincronizacao = sincronizacao;
        this.config = config;
        this.intervaloMs = intervaloMs;
    }

    @PostConstruct
    public void informarConfiguracao() {
        log.info("Tarefa de consulta de pagamentos: habilitada={}, intervaloAposExecucaoMs={}",
                config.habilitado(), intervaloMs);
    }

    @Scheduled(fixedDelayString = INTERVALO)
    public void executar() {
        sincronizacao.sincronizarPendentes();
    }
}
