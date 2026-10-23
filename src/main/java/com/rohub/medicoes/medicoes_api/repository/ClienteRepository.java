package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    java.util.Optional<Cliente> findByIdAndExcluidoFalse(Long id);
    org.springframework.data.domain.Page<Cliente> findAllByExcluidoFalse(org.springframework.data.domain.Pageable pageable);
    long countByEnderecoCobrancaId(Long id);
}
