package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.*;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.repository.*;
import com.rohub.medicoes.medicoes_api.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EnderecoService {
    private final EnderecoRepository enderecos;
    private final ClienteRepository clientes;
    private final ImovelRepository imoveis;
    public EnderecoService(EnderecoRepository enderecos, ClienteRepository clientes, ImovelRepository imoveis) {
        this.enderecos = enderecos; this.clientes = clientes; this.imoveis = imoveis;
    }
    @Transactional
    public Endereco salvar(Endereco atual, EnderecoRequest request) {
        Endereco destino = atual;
        if (atual == null || clientes.countByEnderecoCobrancaId(atual.getId())
                + imoveis.countByEnderecoId(atual.getId()) > 1) {
            destino = new Endereco();
        }
        ApiMapper.preencher(destino, request);
        return enderecos.save(destino);
    }
}
