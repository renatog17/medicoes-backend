package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Instalacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
public interface InstalacaoRepository extends JpaRepository<Instalacao, Long>, JpaSpecificationExecutor<Instalacao> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from Instalacao i where i.id = :id")
    java.util.Optional<Instalacao> buscarComBloqueio(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByClienteId(Long id);
    boolean existsByImovelId(Long id);
    boolean existsByMedidorId(Long id);
    boolean existsByMedidorIdAndDataFimIsNull(Long id);
}
