package com.rohub.medicoes.medicoes_api.service;

import com.rohub.medicoes.medicoes_api.domain.Fatura;
import com.rohub.medicoes.medicoes_api.dto.*;
import com.rohub.medicoes.medicoes_api.exception.RecursoNaoEncontradoException;
import com.rohub.medicoes.medicoes_api.repository.FaturaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FaturaConsultaService {
    private final FaturaRepository faturas;
    public FaturaConsultaService(FaturaRepository faturas) { this.faturas = faturas; }
    public FaturaResponse buscar(Long id) {
        return FaturaResponse.from(faturas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura", id)));
    }
    public PaginaResponse<FaturaResponse> listar(int page, int size, Long instalacaoId, String competencia, Fatura.Situacao situacao) {
        Specification<Fatura> filtro = (root, query, cb) -> cb.conjunction();
        if (instalacaoId != null) filtro = filtro.and((r, q, cb) -> cb.equal(r.get("instalacao").get("id"), instalacaoId));
        if (competencia != null) filtro = filtro.and((r, q, cb) -> cb.equal(r.get("competencia"), competencia));
        if (situacao != null) filtro = filtro.and((r, q, cb) -> cb.equal(r.get("situacao"), situacao));
        return PaginaResponse.from(faturas.findAll(filtro, Paginacao.of(page, size)).map(FaturaResponse::from));
    }
}
