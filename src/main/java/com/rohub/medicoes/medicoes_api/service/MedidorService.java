package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class MedidorService {
    private final MedidorRepository repository;
    private final InstalacaoRepository instalacoes;

    public MedidorService(MedidorRepository repository, InstalacaoRepository instalacoes) {
        this.repository = repository; this.instalacoes = instalacoes;
    }
    public MedidorResponse buscar(Long id) { return ApiMapper.response(encontrar(id)); }
    public PaginaResponse<MedidorResponse> listar(int page, int size) {
        return PaginaResponse.from(repository.findAllByExcluidoFalse(Paginacao.of(page, size)).map(ApiMapper::response));
    }
    @Transactional
    public MedidorResponse criar(MedidorRequest request) {
        Medidor entidade = new Medidor();
        preencher(entidade, request);
        return ApiMapper.response(repository.saveAndFlush(entidade));
    }
    @Transactional
    public MedidorResponse atualizar(Long id, MedidorRequest request) {
        Medidor entidade = encontrar(id);
        preencher(entidade, request);
        return ApiMapper.response(repository.saveAndFlush(entidade));
    }
    @Transactional
    public void excluir(Long id) {
        Medidor entidade = encontrar(id);
        if (instalacoes.existsByMedidorId(id)) {
            throw new ConflitoException("Medidor possui instalacoes vinculadas");
        }
        entidade.setExcluido(true);
        repository.saveAndFlush(entidade);
    }
    private Medidor encontrar(Long id) {
        return repository.findByIdAndExcluidoFalse(id).orElseThrow(() -> new RecursoNaoEncontradoException("Medidor", id));
    }
    private void preencher(Medidor entidade, MedidorRequest request) {
        verificarSerie(request.numeroSerie(), entidade.getId());
        entidade.setNumeroSerie(request.numeroSerie()); entidade.setFabricante(request.fabricante());
        entidade.setModelo(request.modelo());
    }
    private void verificarSerie(String serie, Long id) {
        boolean duplicada = id == null ? repository.existsByNumeroSerie(serie)
            : repository.existsByNumeroSerieAndIdNot(serie, id);
        if (duplicada) throw new ConflitoException("Numero de serie ja cadastrado");
    }
}
