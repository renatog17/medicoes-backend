package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Medicao;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicaoRepository extends JpaRepository<Medicao, Long> {
    @Query(value = """
            SELECT m.id
            FROM medicoes m
            JOIN instalacoes i ON i.id = m.instalacao_id
            WHERE LEAST(i.dia_emissao, :ultimoDiaMes) = :dia
              AND m.data_medicao <= :limite
              AND m.falha_emissao_fatura = FALSE
              AND NOT EXISTS (SELECT 1 FROM faturas f WHERE f.medicao_atual_id = m.id)
              AND EXISTS (
                  SELECT 1 FROM medicoes anterior
                  WHERE anterior.instalacao_id = m.instalacao_id
                    AND anterior.data_medicao < m.data_medicao
              )
            ORDER BY m.instalacao_id, m.data_medicao, m.id
            """, nativeQuery = true)
    List<Long> buscarPendentesDeEmissao(@Param("dia") int dia, @Param("ultimoDiaMes") int ultimoDiaMes,
                                      @Param("limite") Instant limite);

    @Query("select m.instalacao.id from Medicao m where m.id = :id")
    Optional<Long> buscarInstalacaoId(@Param("id") Long id);

    @Modifying
    @Query(value = """
            UPDATE medicoes m SET falha_emissao_fatura = TRUE
            WHERE m.id = :id
              AND NOT EXISTS (SELECT 1 FROM faturas f WHERE f.medicao_atual_id = m.id)
            """, nativeQuery = true)
    void marcarFalhaEmissaoFatura(@Param("id") Long id);

    Optional<Medicao> findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDescIdDesc(
            Long instalacaoId, Instant data);

    @Modifying
    @Query(value = """
            WITH pendentes AS (
                SELECT atual.id, anterior.leitura
                FROM medicoes atual
                CROSS JOIN LATERAL (
                    SELECT leitura
                    FROM medicoes anterior
                    WHERE anterior.instalacao_id = atual.instalacao_id
                      AND anterior.data_medicao < atual.data_medicao
                    ORDER BY anterior.data_medicao DESC, anterior.id DESC
                    LIMIT 1
                ) anterior
                WHERE atual.cache_leitura_anterior IS NULL
            )
            UPDATE medicoes atual
            SET cache_leitura_anterior = pendentes.leitura
            FROM pendentes
            WHERE atual.id = pendentes.id
              AND atual.cache_leitura_anterior IS NULL
            """, nativeQuery = true)
    int preencherCacheLeituraAnterior();

    Optional<Medicao> findFirstByInstalacaoIdAndDataMedicaoGreaterThanEqualAndDataMedicaoLessThanOrderByDataMedicaoDesc(
            Long instalacaoId, java.time.Instant inicio, java.time.Instant fim);
    Optional<Medicao> findFirstByInstalacaoIdAndDataMedicaoLessThanOrderByDataMedicaoDesc(
            Long instalacaoId, java.time.Instant data);
    Page<Medicao> findByInstalacaoId(Long instalacaoId, Pageable pageable);
    Optional<Medicao> findFirstByInstalacaoIdOrderByDataMedicaoDescIdDesc(Long instalacaoId);
}
