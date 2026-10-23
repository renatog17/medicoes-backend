package com.rohub.medicoes.medicoes_api.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "medidores")
public class Medidor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "excluido", nullable = false)
    private boolean excluido;

    public boolean isExcluido() {
        return excluido;
    }

    public void setExcluido(boolean excluido) {
        this.excluido = excluido;
    }

    @NotBlank
    @Size(max = 255)
    @Column(name = "numero_serie", length = 255, nullable = false, unique = true)
    private String numeroSerie;

    @Size(max = 100)
    @Column(name = "fabricante", length = 100)
    private String fabricante;

    @Size(max = 100)
    @Column(name = "modelo", length = 100)
    private String modelo;

    public Medidor() {
    }

    public Medidor(String numeroSerie) {
        this.numeroSerie = numeroSerie;
    }

    public Long getId() {
        return id;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public void setNumeroSerie(String numeroSerie) {
        this.numeroSerie = numeroSerie;
    }

    public String getFabricante() {
        return fabricante;
    }

    public void setFabricante(String fabricante) {
        this.fabricante = fabricante;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }
}
