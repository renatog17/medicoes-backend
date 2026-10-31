package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Medicao;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicaoRepository extends JpaRepository<Medicao, Long> {
    Optional<Medicao> findFirstByInstalacaoIdAndDataMedicaoGreaterThanEqualAndDataMedicaoLessThanOrderByDataMedicaoDesc(
            Long instalacaoId, java.time.Instant inicio, java.time.Instant fim);
    Optional<Medicao> findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDesc(
            Long instalacaoId, java.time.Instant data);
    Page<Medicao> findByInstalacaoId(Long instalacaoId, Pageable pageable);
    Optional<Medicao> findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(Long instalacaoId);
}
