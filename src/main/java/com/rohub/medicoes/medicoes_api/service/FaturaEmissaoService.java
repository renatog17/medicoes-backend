package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaturaEmissaoService {
    public record Resultado(Long faturaId, String resultado) {}
    private final FaturaRepository faturas;
    private final InstalacaoRepository instalacoes;
    private final MedicaoRepository medicoes;
    private final Propriedades config;
    private final Clock clock;

    public FaturaEmissaoService(FaturaRepository faturas, InstalacaoRepository instalacoes,
            MedicaoRepository medicoes, Propriedades config, Clock clock) {
        this.faturas = faturas; this.instalacoes = instalacoes; this.medicoes = medicoes;
        this.config = config; this.clock = clock;
    }

    @Transactional
    public Resultado emitir(Long instalacaoId, YearMonth competencia) {
        if (!config.habilitado()) throw new RequisicaoInvalidaException("Faturamento desabilitado");
        if (!competencia.isBefore(YearMonth.now(clock)))
            throw new RequisicaoInvalidaException("Competencia deve ser anterior ao mes atual");
        instalacoes.buscarComBloqueio(instalacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instalacao", instalacaoId));
        var existente = faturas.findByInstalacaoIdAndCompetencia(instalacaoId, competencia.toString());
        if (existente.isPresent()) return new Resultado(existente.get().getId(), "EXISTENTE");
        var inicio = competencia.atDay(1).atStartOfDay(clock.getZone()).toInstant();
        var fim = competencia.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant();
        var atual = medicoes.findFirstByInstalacaoIdAndDataMedicaoGreaterThanEqualAndDataMedicaoLessThanOrderByDataMedicaoDesc(
                instalacaoId, inicio, fim);
        if (atual.isEmpty()) return new Resultado(null, "SEM_LEITURA");
        var anterior = medicoes.findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDesc(
                instalacaoId, atual.get().getDataMedicao());
        if (anterior.isEmpty()) return new Resultado(null, "REFERENCIA_INICIAL");
        var fatura = faturas.saveAndFlush(new Fatura(anterior.get(), atual.get(), competencia, config.valorM3(), clock));
        return new Resultado(fatura.getId(), "EMITIDA");
    }
}
