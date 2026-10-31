package com.rohub.medicoes.medicoes_api.integracao;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

public interface BancoGateway {
    record Pagador(String nome, String documento) {}
    record Registro(String referenciaExterna, BigDecimal valor, String moeda, LocalDate vencimento, Pagador pagador) {}
    record Cobranca(String id, String referenciaExterna, BigDecimal valor, String moeda, String situacao,
                    String pixCopiaECola, BigDecimal valorPago, Instant pagoEm) {}
    Cobranca registrar(Registro registro);
    Cobranca consultar(String id);
    Optional<Cobranca> buscarPorReferencia(String referencia);
}
