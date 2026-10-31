package com.rohub.medicoes.medicoes_api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;

@Entity
@Table(name = "faturas")
public class Fatura {

    public enum Situacao {
        ABERTA, PAGA, SEM_VALOR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instalacao_id", nullable = false)
    private Instalacao instalacao;

    @Column(nullable = false)
    private Long medicaoAnteriorId;

    @Column(nullable = false)
    private Long medicaoAtualId;

    @Column(nullable = false, length = 7)
    private String competencia;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal consumo;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal tarifa;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private LocalDate emissao;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(nullable = false)
    private Instant criadaEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Situacao situacao;

    private String bancoId;

    @Column(columnDefinition = "text")
    private String pix;

    @Column(length = 30)
    private String situacaoBancaria;

    @Column(precision = 19, scale = 2)
    private BigDecimal valorPago;

    private Instant pagoEm;

    @Version
    private long versao;

    protected Fatura() {}

    public Fatura(
            Medicao anterior,
            Medicao atual,
            YearMonth competencia,
            BigDecimal tarifa,
            Clock clock
    ) {
        this.instalacao = atual.getInstalacao();
        this.medicaoAnteriorId = anterior.getId();
        this.medicaoAtualId = atual.getId();
        this.competencia = competencia.toString();
        this.consumo = atual.getLeitura().subtract(anterior.getLeitura());

        if (consumo.signum() < 0) {
            throw new IllegalArgumentException("Consumo negativo");
        }

        this.tarifa = tarifa;
        this.total = consumo.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);
        this.emissao = competencia.plusMonths(1).atDay(1);
        this.vencimento = competencia.plusMonths(1).atDay(10);
        this.criadaEm = clock.instant();
        this.situacao = total.signum() == 0
                ? Situacao.SEM_VALOR
                : Situacao.ABERTA;
    }

    public void atualizarBanco(
            String bancoId,
            String pix,
            String status,
            BigDecimal valorPago,
            Instant pagoEm
    ) {
        this.bancoId = bancoId;
        this.pix = pix;
        this.situacaoBancaria = status;
        this.valorPago = valorPago;
        this.pagoEm = pagoEm;

        if ("PAGA".equals(status)
                && valorPago != null
                && valorPago.compareTo(total) == 0
                && pagoEm != null) {
            this.situacao = Situacao.PAGA;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getInstalacaoId() {
        return instalacao.getId();
    }

    public Long getClienteId() {
        return instalacao.getCliente().getId();
    }

    public Long getMedicaoAnteriorId() {
        return medicaoAnteriorId;
    }

    public Long getMedicaoAtualId() {
        return medicaoAtualId;
    }

    public String getCompetencia() {
        return competencia;
    }

    public BigDecimal getConsumo() {
        return consumo;
    }

    public BigDecimal getTarifa() {
        return tarifa;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDate getEmissao() {
        return emissao;
    }

    public LocalDate getVencimento() {
        return vencimento;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public String getBancoId() {
        return bancoId;
    }

    public String getPix() {
        return pix;
    }

    public String getSituacaoBancaria() {
        return situacaoBancaria;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public Instant getPagoEm() {
        return pagoEm;
    }
}