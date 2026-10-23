package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class ImovelService {
    private final ImovelRepository repository;
    private final InstalacaoRepository instalacoes;
    private final EnderecoService enderecos;

    public ImovelService(ImovelRepository repository, InstalacaoRepository instalacoes, EnderecoService enderecos) {
        this.repository = repository; this.instalacoes = instalacoes;
        this.enderecos = enderecos;
    }
    public ImovelResponse buscar(Long id) { return ApiMapper.response(encontrar(id)); }
    public PaginaResponse<ImovelResponse> listar(int page, int size) {
        return PaginaResponse.from(repository.findAllByExcluidoFalse(Paginacao.of(page, size)).map(ApiMapper::response));
    }
    @Transactional
    public ImovelResponse criar(ImovelRequest request) {
        Imovel entidade = new Imovel();
        preencher(entidade, request);
        return ApiMapper.response(repository.saveAndFlush(entidade));
    }
    @Transactional
    public ImovelResponse atualizar(Long id, ImovelRequest request) {
        Imovel entidade = encontrar(id);
        preencher(entidade, request);
        return ApiMapper.response(repository.saveAndFlush(entidade));
    }
    @Transactional
    public void excluir(Long id) {
        Imovel entidade = encontrar(id);
        if (instalacoes.existsByImovelId(id)) {
            throw new ConflitoException("Imovel possui instalacoes vinculadas");
        }
        entidade.setExcluido(true);
        repository.saveAndFlush(entidade);
    }
    private Imovel encontrar(Long id) {
        return repository.findByIdAndExcluidoFalse(id).orElseThrow(() -> new RecursoNaoEncontradoException("Imovel", id));
    }
    private void preencher(Imovel entidade, ImovelRequest request) {
        entidade.setEndereco(enderecos.salvar(entidade.getEndereco(), request.endereco()));
    }
}
