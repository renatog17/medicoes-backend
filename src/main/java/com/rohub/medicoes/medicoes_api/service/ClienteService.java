package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class ClienteService {
    private final ClienteRepository repository;
    private final InstalacaoRepository instalacoes;
    private final EnderecoService enderecos;

    public ClienteService(ClienteRepository repository, InstalacaoRepository instalacoes, EnderecoService enderecos) {
        this.repository = repository; this.instalacoes = instalacoes;
        this.enderecos = enderecos;
    }
    public ClienteResponse buscar(Long id) { return ApiMapper.response(encontrar(id)); }
    public PaginaResponse<ClienteResponse> listar(int page, int size) {
        return PaginaResponse.from(repository.findAllByExcluidoFalse(Paginacao.of(page, size)).map(ApiMapper::response));
    }
    @Transactional
    public ClienteResponse criar(ClienteRequest request) {
        Cliente entidade = new Cliente();
        preencher(entidade, request);
        return ApiMapper.response(repository.saveAndFlush(entidade));
    }
    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest request) {
        Cliente entidade = encontrar(id);
        preencher(entidade, request);
        return ApiMapper.response(repository.saveAndFlush(entidade));
    }
    @Transactional
    public void excluir(Long id) {
        Cliente entidade = encontrar(id);
        if (instalacoes.existsByClienteId(id)) {
            throw new ConflitoException("Cliente possui instalacoes vinculadas");
        }
        entidade.setExcluido(true);
        repository.saveAndFlush(entidade);
    }
    private Cliente encontrar(Long id) {
        return repository.findByIdAndExcluidoFalse(id).orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }
    private void preencher(Cliente entidade, ClienteRequest request) {
        entidade.setNome(request.nome()); entidade.setCin(request.cin());
        entidade.setEmail(request.email()); entidade.setTelefone(request.telefone());
        entidade.setEnderecoCobranca(enderecos.salvar(entidade.getEnderecoCobranca(), request.enderecoCobranca()));
    }
}
