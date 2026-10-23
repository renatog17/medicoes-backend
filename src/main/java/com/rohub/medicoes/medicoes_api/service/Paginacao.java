package com.rohub.medicoes.medicoes_api.service;

import org.springframework.data.domain.*;
import com.rohub.medicoes.medicoes_api.exception.RequisicaoInvalidaException;
public final class Paginacao {
    private Paginacao() {}
    public static Pageable of(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new RequisicaoInvalidaException("page deve ser >= 0 e size deve estar entre 1 e 100");
        }
        return PageRequest.of(page, size, Sort.by("id").ascending());
    }
}
