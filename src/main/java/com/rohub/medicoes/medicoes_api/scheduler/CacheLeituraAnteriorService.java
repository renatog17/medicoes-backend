package com.rohub.medicoes.medicoes_api.scheduler;

import com.rohub.medicoes.medicoes_api.repository.MedicaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CacheLeituraAnteriorService {
    private final MedicaoRepository medicoes;

    public CacheLeituraAnteriorService(MedicaoRepository medicoes) {
        this.medicoes = medicoes;
    }

    @Transactional
    public int preencherPendentes() {
        return medicoes.preencherCacheLeituraAnterior();
    }
}
