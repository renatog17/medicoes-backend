package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.*;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.exception.RequisicaoInvalidaException;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FaturamentoService {
    public record Falha(Long instalacaoId, Long faturaId, String etapa) {}
    public record Lote(String competencia, int emitidas, int existentes, int semLeitura,
                       int referenciasIniciais, List<Falha> falhas) {}
    private static final Logger log = LoggerFactory.getLogger(FaturamentoService.class);
    private final InstalacaoRepository instalacoes;
    private final FaturaRepository faturas;
    private final FaturaEmissaoService emissao;
    private final FaturaBancoService banco;
    private final Propriedades config;
    private final Banco bancoConfig;
    private final Clock clock;
    public FaturamentoService(InstalacaoRepository instalacoes, FaturaRepository faturas,
            FaturaEmissaoService emissao, FaturaBancoService banco, Propriedades config, Banco bancoConfig, Clock clock) {
        this.instalacoes = instalacoes; this.faturas = faturas; this.emissao = emissao;
        this.banco = banco; this.config = config; this.bancoConfig = bancoConfig; this.clock = clock;
    }
    public Lote emitir(YearMonth competencia) {
        if (!config.habilitado()) throw new RequisicaoInvalidaException("Faturamento desabilitado");
        if (!competencia.isBefore(YearMonth.now(clock)))
            throw new RequisicaoInvalidaException("Competencia deve ser anterior ao mes atual");
        int emitidas = 0, existentes = 0, semLeitura = 0, iniciais = 0;
        var falhas = new ArrayList<Falha>();
        for (var instalacao : instalacoes.findAll()) {
            FaturaEmissaoService.Resultado resultado;
            try {
                resultado = emissao.emitir(instalacao.getId(), competencia);
                switch (resultado.resultado()) {
                    case "EMITIDA" -> emitidas++;
                    case "EXISTENTE" -> existentes++;
                    case "SEM_LEITURA" -> semLeitura++;
                    case "REFERENCIA_INICIAL" -> iniciais++;
                }
            } catch (RuntimeException ex) {
                log.warn("Falha na emissao da instalacao {}: {}", instalacao.getId(), ex.getClass().getSimpleName());
                falhas.add(new Falha(instalacao.getId(), null, "EMISSAO"));
                continue;
            }
            if (bancoConfig.habilitado() && resultado.faturaId() != null) {
                try { banco.registrar(resultado.faturaId()); }
                catch (RuntimeException ex) {
                    log.warn("Falha bancaria na fatura {}: {}", resultado.faturaId(), ex.getClass().getSimpleName());
                    falhas.add(new Falha(instalacao.getId(), resultado.faturaId(), "BANCO"));
                }
            }
        }
        return new Lote(competencia.toString(), emitidas, existentes, semLeitura, iniciais, falhas);
    }
    @Scheduled(cron = "5 0 0 * * *", zone = "America/Sao_Paulo")
    public void emitirAutomaticamente() {
        if (!config.habilitado()) return;
        var lote = emitir(YearMonth.now(clock).minusMonths(1));
        log.info("Faturamento {}: emitidas={}, existentes={}, semLeitura={}, falhas={}",
                lote.competencia(), lote.emitidas(), lote.existentes(), lote.semLeitura(), lote.falhas().size());
    }
    @Scheduled(fixedDelayString = "${app.banco.sincronizacao-ms:900000}")
    public void sincronizarPendentes() {
        if (!bancoConfig.habilitado()) return;
        for (var fatura : faturas.findBySituacao(Fatura.Situacao.ABERTA)) {
            try { banco.sincronizar(fatura.getId()); }
            catch (RuntimeException ex) {
                log.warn("Sincronizacao pendente para fatura {}: {}", fatura.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
