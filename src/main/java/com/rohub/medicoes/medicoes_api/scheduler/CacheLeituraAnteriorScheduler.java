package com.rohub.medicoes.medicoes_api.scheduler;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CacheLeituraAnteriorScheduler {
    private static final Logger log = LoggerFactory.getLogger(CacheLeituraAnteriorScheduler.class);
    private static final String CRON = "0 0 1 * * *";
    private static final String FUSO = "America/Sao_Paulo";
    private final CacheLeituraAnteriorService cache;

    public CacheLeituraAnteriorScheduler(CacheLeituraAnteriorService cache) {
        this.cache = cache;
    }

    @PostConstruct
    public void informarConfiguracao() {
        log.info("Tarefa de preenchimento do cache de leitura anterior: cron={}, fuso={}", CRON, FUSO);
    }

    @Scheduled(cron = CRON, zone = FUSO)
    public void executar() {
        int atualizadas = cache.preencherPendentes();
        log.info("Cache de leitura anterior: medicoesAtualizadas={}", atualizadas);
    }
}
