package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.config.FaturamentoConfig.Propriedades;
import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.domain.Instalacao;
import com.rohub.medicoes.medicoes_api.exception.RecursoNaoEncontradoException;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FaturamentoService {
    private final InstalacaoRepository instalacoes;
    private final MedicaoRepository medicoes;
    private final FaturaRepository faturas;
    private final Propriedades config;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public FaturamentoService(InstalacaoRepository instalacoes, MedicaoRepository medicoes,
            FaturaRepository faturas, Propriedades config, Clock clock, PlatformTransactionManager manager) {
        this.instalacoes = instalacoes;
        this.medicoes = medicoes;
        this.faturas = faturas;
        this.config = config;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void emitirAutomaticamente() {
        if (!config.habilitado()) return;
        var execucao = Clock.fixed(clock.instant(), clock.getZone());
        var hoje = LocalDate.now(execucao);
        for (var medicaoId : medicoes.buscarPendentesDeEmissao(
                hoje.getDayOfMonth(), hoje.lengthOfMonth(), execucao.instant())) {
            try {
                transactions.executeWithoutResult(status -> emitirMedicao(medicaoId, execucao));
            } catch (RuntimeException ex) {
                transactions.executeWithoutResult(status -> {
                    bloquearInstalacao(medicaoId);
                    medicoes.marcarFalhaEmissaoFatura(medicaoId);
                });
            }
        }
    }

    private void emitirMedicao(Long medicaoId, Clock execucao) {
        var instalacao = bloquearInstalacao(medicaoId);
        var atual = medicoes.findById(medicaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicao", medicaoId));
        var hoje = LocalDate.now(execucao);
        if (Math.min(instalacao.getDiaEmissao(), hoje.lengthOfMonth()) != hoje.getDayOfMonth()
                || atual.getDataMedicao().isAfter(execucao.instant())
                || atual.isFalhaEmissaoFatura() || faturas.existsByMedicaoAtualId(medicaoId)) return;
        var anterior = medicoes.findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDescIdDesc(
                instalacao.getId(), atual.getDataMedicao());
        if (anterior.isEmpty()) return;
        var competencia = YearMonth.from(atual.getDataMedicao().atZone(execucao.getZone()));
        faturas.saveAndFlush(new Fatura(anterior.get(), atual, competencia, config.valorM3(), execucao));
    }

    private Instalacao bloquearInstalacao(Long medicaoId) {
        var instalacaoId = medicoes.buscarInstalacaoId(medicaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicao", medicaoId));
        return instalacoes.buscarComBloqueio(instalacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instalacao", instalacaoId));
    }
}
