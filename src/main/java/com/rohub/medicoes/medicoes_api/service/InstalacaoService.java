package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional(readOnly = true)
public class InstalacaoService {
    private final InstalacaoRepository instalacoes;
    private final ClienteRepository clientes;
    private final ImovelRepository imoveis;
    private final MedidorRepository medidores;
    private final MedicaoRepository medicoes;

    public InstalacaoService(InstalacaoRepository instalacoes, ClienteRepository clientes,
                             ImovelRepository imoveis, MedidorRepository medidores, MedicaoRepository medicoes) {
        this.instalacoes = instalacoes;
        this.clientes = clientes;
        this.imoveis = imoveis;
        this.medidores = medidores;
        this.medicoes = medicoes;
    }

    public InstalacaoResponse buscar(Long id) {
        return ApiMapper.response(encontrar(id));
    }

    public PaginaResponse<InstalacaoResponse> listar(int page, int size, Long clienteId,
                                                     Long imovelId, Long medidorId, Boolean ativa) {
        Specification<Instalacao> filtro = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (clienteId != null) predicates.add(cb.equal(root.get("cliente").get("id"), clienteId));
            if (imovelId != null) predicates.add(cb.equal(root.get("imovel").get("id"), imovelId));
            if (medidorId != null) predicates.add(cb.equal(root.get("medidor").get("id"), medidorId));
            if (ativa != null)
                predicates.add(ativa ? cb.isNull(root.get("dataFim")) : cb.isNotNull(root.get("dataFim")));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PaginaResponse.from(instalacoes.findAll(filtro, Paginacao.of(page, size)).map(ApiMapper::response));
    }

    @Transactional
    public InstalacaoResponse criar(InstalacaoRequest request) {
        Cliente cliente = clientes.findByIdAndExcluidoFalse(request.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", request.clienteId()));
        Imovel imovel = imoveis.findByIdAndExcluidoFalse(request.imovelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Imovel", request.imovelId()));
        Medidor medidor = medidores.findByIdAndExcluidoFalse(request.medidorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medidor", request.medidorId()));
        if (instalacoes.existsByMedidorIdAndDataFimIsNull(request.medidorId())) {
            throw new ConflitoException("Medidor ja possui instalacao aberta");
        }
        Instalacao entidade = new Instalacao(cliente, imovel, medidor, request.dataInicio());
        entidade.setObservacoes(request.observacoes());
        return ApiMapper.response(instalacoes.saveAndFlush(entidade));
    }

    @Transactional
    public InstalacaoResponse encerrar(Long id, EncerramentoRequest request) {
        Instalacao entidade = instalacoes.buscarComBloqueio(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Instalacao", id));
        if (entidade.getDataFim() != null) throw new ConflitoException("Instalacao ja encerrada");
        if (!request.dataFim().isAfter(entidade.getDataInicio())) {
            throw new RequisicaoInvalidaException("Data de fim deve ser posterior ao inicio");
        }
        medicoes.findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(id).ifPresent(ultima -> {
            if (request.dataFim().isBefore(ultima.getDataMedicao())) {
                throw new RequisicaoInvalidaException("Data de fim nao pode ser anterior a ultima medicao");
            }
        });
        entidade.setDataFim(request.dataFim());
        return ApiMapper.response(instalacoes.saveAndFlush(entidade));
    }

    private Instalacao encontrar(Long id) {
        return instalacoes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Instalacao", id));
    }
}
