package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Medidor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
public interface MedidorRepository extends JpaRepository<Medidor, Long> {
    Optional<Medidor> findByIdAndExcluidoFalse(Long id);
    Page<Medidor> findAllByExcluidoFalse(Pageable pageable);
    boolean existsByNumeroSerie(String numeroSerie);
    boolean existsByNumeroSerieAndIdNot(String numeroSerie, Long id);
}
