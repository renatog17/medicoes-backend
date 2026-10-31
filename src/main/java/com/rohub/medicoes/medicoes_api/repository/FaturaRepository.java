package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Fatura;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;

public interface FaturaRepository extends JpaRepository<Fatura, Long>, JpaSpecificationExecutor<Fatura> {
    @Override
    @EntityGraph(attributePaths = {"instalacao", "instalacao.cliente"})
    Optional<Fatura> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"instalacao", "instalacao.cliente"})
    Page<Fatura> findAll(Specification<Fatura> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"instalacao", "instalacao.cliente"})
    Optional<Fatura> findByInstalacaoIdAndCompetencia(Long instalacaoId, String competencia);
    List<Fatura> findBySituacao(Fatura.Situacao situacao);
}
