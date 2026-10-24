package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.Medicao;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MedicaoService {
    private final MedicaoRepository medicoes;
    private final InstalacaoRepository instalacoes;
    private final Clock clock;

    public MedicaoService(MedicaoRepository medicoes, InstalacaoRepository instalacoes, Clock clock) {
        this.medicoes = medicoes;
        this.instalacoes = instalacoes;
        this.clock = clock;
    }

    public MedicaoResponse buscar(Long id) {
        return ApiMapper.response(medicoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicao", id)));
    }

    public PaginaResponse<MedicaoResponse> listar(int page, int size, Long instalacaoId) {
        var pageable = Paginacao.of(page, size);
        var resultado = instalacaoId == null ? medicoes.findAll(pageable)
                : medicoes.findByInstalacaoId(instalacaoId, pageable);
        return PaginaResponse.from(resultado.map(ApiMapper::response));
    }

    @Transactional
    public MedicaoResponse criar(MedicaoRequest request) {
        // O bloqueio da instalacao protege inclusive o cadastro da primeira medicao.
        var instalacao = instalacoes.buscarComBloqueio(request.instalacaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instalacao", request.instalacaoId()));
        if (request.dataMedicao().isBefore(instalacao.getDataInicio())
                || (instalacao.getDataFim() != null && request.dataMedicao().isAfter(instalacao.getDataFim()))) {
            throw new RequisicaoInvalidaException("Data da medicao deve estar dentro do periodo da instalacao");
        }
        medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(request.instalacaoId()).ifPresent(ultima -> {
            if (!request.dataMedicao().isAfter(ultima.getDataMedicao())) {
                throw new ConflitoException("Data da medicao deve ser posterior a ultima medicao");
            }
            var mesSolicitado = YearMonth.from(request.dataMedicao().atZone(clock.getZone()));
            var mesUltimaMedicao = YearMonth.from(ultima.getDataMedicao().atZone(clock.getZone()));
            if (mesSolicitado.equals(mesUltimaMedicao)) {
                throw new ConflitoException("Ja existe uma medicao para esta instalacao neste mes");
            }
            if (request.leitura().compareTo(ultima.getLeitura()) < 0) {
                throw new ConflitoException("Leitura nao pode ser menor que a ultima medicao");
            }
        });
        return ApiMapper.response(medicoes.saveAndFlush(
                new Medicao(instalacao, request.dataMedicao(), request.leitura())));
    }
}
