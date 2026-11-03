package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Banco;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.repository.FaturaRepository;
import com.rohub.medicoes.medicoes_api.service.FaturaBancoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SincronizacaoCobrancaService {
    private static final Logger log = LoggerFactory.getLogger(SincronizacaoCobrancaService.class);
    private final FaturaRepository faturas;
    private final FaturaBancoService banco;
    private final Banco config;

    public SincronizacaoCobrancaService(FaturaRepository faturas, FaturaBancoService banco, Banco config) {
        this.faturas = faturas;
        this.banco = banco;
        this.config = config;
    }

    public void sincronizarPendentes() {
        if (!config.habilitado()) return;
        for (var fatura : faturas.findBySituacao(Fatura.Situacao.ABERTA)) {
            try { banco.sincronizar(fatura.getId()); }
            catch (RuntimeException ex) {
                log.warn("Sincronizacao pendente para fatura {}: {}", fatura.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
