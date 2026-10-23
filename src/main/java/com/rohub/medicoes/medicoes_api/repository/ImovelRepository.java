package com.rohub.medicoes.medicoes_api.repository;

import com.rohub.medicoes.medicoes_api.domain.Imovel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
public interface ImovelRepository extends JpaRepository<Imovel, Long> {
    Optional<Imovel> findByIdAndExcluidoFalse(Long id);
    Page<Imovel> findAllByExcluidoFalse(Pageable pageable);
    long countByEnderecoId(Long id);
}
