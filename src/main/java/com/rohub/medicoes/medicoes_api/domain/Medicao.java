package com.rohub.medicoes.medicoes_api.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "medicoes")
public class Medicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instalacao_id", nullable = false)
    private Instalacao instalacao;

    @NotNull
    @Column(name = "data_medicao", nullable = false)
    private Instant dataMedicao;

    @NotNull
    @PositiveOrZero
    @Column(name = "leitura", nullable = false, precision = 19, scale = 6)
    private BigDecimal leitura;

    public Medicao() {
    }

    public Medicao(Instalacao instalacao, Instant dataMedicao, BigDecimal leitura) {
        this.instalacao = instalacao;
        this.dataMedicao = dataMedicao;
        this.leitura = leitura;
    }

    public Long getId() {
        return id;
    }

    public Instalacao getInstalacao() {
        return instalacao;
    }

    public void setInstalacao(Instalacao instalacao) {
        this.instalacao = instalacao;
    }

    public Instant getDataMedicao() {
        return dataMedicao;
    }

    public void setDataMedicao(Instant dataMedicao) {
        this.dataMedicao = dataMedicao;
    }

    public BigDecimal getLeitura() {
        return leitura;
    }

    public void setLeitura(BigDecimal leitura) {
        this.leitura = leitura;
    }
}
